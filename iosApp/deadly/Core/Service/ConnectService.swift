import Foundation
import SwiftAudioStreamEx
import AVFoundation
import os.log
#if canImport(UIKit)
import UIKit
#endif

private let logger = Logger(subsystem: "com.grateful.deadly", category: "ConnectService")

/// Manages the Connect v2 WebSocket connection.
/// Auto-registers this device, maintains heartbeat, and publishes device/state updates.
/// Also drives local audio playback in response to server state broadcasts.
@Observable
@MainActor
final class ConnectService: NSObject {
    private(set) var devices: [ConnectDevice] = []
    private(set) var connectState: ConnectState?
    private(set) var isConnected = false
    private(set) var pendingCommand: String?
    private(set) var pendingTransfer: String?
    private(set) var activeDeviceVolume: Int = 100
    var showVolumeUI: Bool = false

    /// Server-clock minus local-clock, in milliseconds. Add to the local epoch
    /// time (ms since 1970) to approximate the server's wall-clock when
    /// comparing against `ConnectState.positionTs`. Starts at 0 and converges
    /// after the first time_sync round-trip completes. See
    /// docs/connect-v2-architecture.md "Clock Sync".
    private(set) var serverTimeOffsetMs: Double = 0

    var isActiveDevice: Bool {
        guard let state = connectState else { return false }
        return state.activeDeviceId == appPreferences.installId
    }

    var isRemoteControlling: Bool {
        guard let state = connectState else { return false }
        return state.activeDeviceId != nil && state.activeDeviceId != appPreferences.installId
    }

    /// Callback to load a show into the local player. Set by PlaylistServiceImpl
    /// to avoid circular dependency. Called when Connect state has a recording
    /// that isn't loaded locally.
    /// Parameters: (showId, recordingId, trackIndex, positionMs, autoPlay)
    var onLoadShow: ((String, String, Int, Int, Bool) async -> Void)?

    private let appPreferences: AppPreferences
    private let authService: AuthService
    private let streamPlayer: StreamPlayer

    private var webSocket: URLSessionWebSocketTask?
    private var urlSession: URLSession?
    private var heartbeatTask: Task<Void, Never>?
    private var positionReportTask: Task<Void, Never>?
    private var reconnectTask: Task<Void, Never>?
    private var timeSyncRefreshTask: Task<Void, Never>?
    private var reconnectAttempt = 0
    private var shouldConnect = false
    // Guards the one-shot tracklist re-hydration so position broadcasts arriving
    // before our load echoes back don't make us re-send it repeatedly.
    private var reassertingTracks = false
    // True from a (re)connect until the server next assigns an owner. Distinguishes
    // an ownerless GHOST (server lost us across a restart/socket drop — reclaim) from
    // a deliberate PARK (transfer/stop on a live socket — pause). Only the ghost
    // arrives right after a reconnect; a park arrives mid-connection.
    private var reclaimOnReconnect = false
    // Set on every (re)connect. The monotonic version guard only dedupes
    // out-of-order messages within one connection; across a reconnect the
    // server may have restarted and reset its counter, so the first snapshot
    // after connecting is authoritative regardless of version. (Belt-and-braces
    // with the server seeding versions from wall-clock time.)
    private var resyncOnReconnect = false
    private var volumeObservation: NSKeyValueObservation?
    // Lowest RTT seen in the current time_sync batch. Replies with higher RTT
    // are dropped; only the best (fastest round-trip) sample of each batch
    // gets promoted to serverTimeOffsetMs.
    private var timeSyncBestRttMs: Double = .infinity

    // Connect WS wire-contract version. See docs/PROTOCOL.md for semantics.
    // Bump in lockstep with the documented protocol; the server may branch on it.
    private static let protocolVersion = 1
    private static let reconnectDelays: [Double] = [1, 2, 4, 8, 30]
    private static let heartbeatInterval: UInt64 = 15_000_000_000 // 15s in nanoseconds
    private static let timeSyncRefreshInterval: UInt64 = 5 * 60 * 1_000_000_000 // 5 min
    private static let timeSyncSamples = 3
    private static let timeSyncSampleSpacing: UInt64 = 200_000_000 // 200ms

    init(appPreferences: AppPreferences, authService: AuthService, streamPlayer: StreamPlayer) {
        self.appPreferences = appPreferences
        self.authService = authService
        self.streamPlayer = streamPlayer
        super.init()

        // Forward LOCAL play/pause that bypassed the in-app buttons — the lock
        // screen, Bluetooth/headset keys, CarPlay, and audio-session interruptions
        // all drive StreamPlayer directly and never call sendPlay()/sendPause().
        // Without this the server keeps thinking the active device is playing, its
        // next position broadcast arrives as playing=true, and reactToState resumes
        // the audio seconds after the user paused from their headphones.
        streamPlayer.onPlayIntentChange = { [weak self] intent in
            self?.reconcileLocalPlayIntent(intent)
        }
    }

    /// Push a divergence between local play intent and the server's `playing` to
    /// the session, but only while we're the active device. We send only on a
    /// MISMATCH so server-driven changes don't echo: reactToState sets
    /// `connectState` before it touches the player, so by the time the engine
    /// reports the resulting intent the local value already equals the server's
    /// and nothing is sent. A duplicate from the in-app toggle is harmless —
    /// handlePlay/handlePause are no-ops when already in the target state.
    private func reconcileLocalPlayIntent(_ localIntendsPlay: Bool) {
        guard isActiveDevice, let serverPlaying = connectState?.playing else { return }
        guard localIntendsPlay != serverPlaying else { return }
        if localIntendsPlay {
            logger.info("playIntent reconcile: local play diverged from server (paused) — sendPlay")
            sendPlay()
        } else {
            logger.info("playIntent reconcile: local pause diverged from server (playing) — sendPause")
            sendPause()
        }
    }

    // MARK: - Public Interface

    func startIfAuthenticated() {
        let hasToken = authService.token != nil
        logger.info("startIfAuthenticated: token=\(hasToken ? "present" : "null", privacy: .public) shouldConnect=\(self.shouldConnect, privacy: .public)")
        guard hasToken else { return }
        if shouldConnect {
            // Already meant to be connected. Since we no longer stop() on
            // background, a socket iOS killed during suspension leaves
            // shouldConnect=true; reconnect on foreground instead of no-opping.
            // handleNetworkRestored is a no-op if still connected.
            if !isConnected { handleNetworkRestored() }
            return
        }
        shouldConnect = true
        reconnectAttempt = 0
        Task { await connect() }
    }

    func stop() {
        logger.info("stop() called")
        shouldConnect = false
        reconnectTask?.cancel()
        reconnectTask = nil
        stopHeartbeat()
        stopTimeSyncRefresh()
        stopVolumeObservation()
        let ws = webSocket
        webSocket = nil
        ws?.cancel(with: .normalClosure, reason: nil)
        isConnected = false
        devices = []
        connectState = nil
        pendingCommand = nil
        serverTimeOffsetMs = 0
        timeSyncBestRttMs = .infinity
    }

    // MARK: - Commands

    func sendLoad(
        showId: String,
        recordingId: String,
        tracks: [SessionTrack],
        trackIndex: Int,
        positionMs: Int,
        durationMs: Int,
        date: String?,
        venue: String?,
        location: String?,
        autoplay: Bool = true
    ) {
        logger.info("sendLoad: show=\(showId, privacy: .public) rec=\(recordingId, privacy: .public) track=\(trackIndex, privacy: .public) pos=\(positionMs, privacy: .public) dur=\(durationMs, privacy: .public) autoplay=\(autoplay, privacy: .public)")
        var extra: [String: Any] = [
            "showId": showId,
            "recordingId": recordingId,
            "tracks": tracks.map { ["title": $0.title, "durationMs": $0.durationMs] },
            "trackIndex": trackIndex,
            "positionMs": positionMs,
            "durationMs": durationMs,
            "autoplay": autoplay,
        ]
        if let date { extra["date"] = date }
        if let venue { extra["venue"] = venue }
        if let location { extra["location"] = location }
        sendCommand("load", extra: extra)
    }

    func sendPlay() {
        logger.info("sendPlay (pending=\(self.pendingCommand ?? "nil", privacy: .public) serverPlaying=\(self.connectState?.playing ?? false, privacy: .public))")
        // Only enter a pending state if there's a live socket to confirm it, and
        // only if the server isn't already playing — a redundant play won't
        // trigger a state broadcast, so pendingCommand would never clear.
        if isConnected, connectState?.playing != true {
            pendingCommand = "play"
        }
        sendCommand("play")
    }

    func sendPause() {
        logger.info("sendPause (pending=\(self.pendingCommand ?? "nil", privacy: .public) serverPlaying=\(self.connectState?.playing ?? false, privacy: .public))")
        if isConnected, connectState?.playing == true {
            pendingCommand = "pause"
        }
        sendCommand("pause")
    }

    func sendSeek(trackIndex: Int, positionMs: Int, durationMs: Int) {
        logger.info("sendSeek: track=\(trackIndex, privacy: .public) pos=\(positionMs, privacy: .public) dur=\(durationMs, privacy: .public)")
        sendCommand("seek", extra: [
            "trackIndex": trackIndex,
            "positionMs": positionMs,
            "durationMs": durationMs,
        ])
    }

    func sendNext() {
        logger.info("sendNext (pending=\(self.pendingCommand ?? "nil", privacy: .public) -> next)")
        if isConnected { pendingCommand = "next" }
        sendCommand("next")
    }

    func sendPrev() {
        logger.info("sendPrev (pending=\(self.pendingCommand ?? "nil", privacy: .public) -> prev)")
        if isConnected { pendingCommand = "prev" }
        sendCommand("prev")
    }

    func sendTransfer(targetDeviceId: String) {
        guard connectState?.showId != nil else { return }
        logger.info("sendTransfer: target=\(targetDeviceId, privacy: .public)")
        if isConnected { pendingTransfer = targetDeviceId }
        sendCommand("transfer", extra: ["targetDeviceId": targetDeviceId])
    }

    func sendPosition(positionMs: Int) {
        logger.info("sendPosition: pos=\(positionMs, privacy: .public)")
        sendCommand("position", extra: ["positionMs": positionMs])
    }

    func sendStop() {
        logger.info("sendStop")
        sendCommand("stop")
    }

    // ADR-0010 §7: cross-device end-of-show countdown.
    func sendAnnounceNext(showId: String, deadline: Double) {
        logger.info("sendAnnounceNext: \(showId, privacy: .public) @ \(deadline)")
        sendCommand("announce_next", extra: ["showId": showId, "deadline": deadline])
    }

    func sendCancelAdvance() {
        logger.info("sendCancelAdvance")
        sendCommand("cancel_advance")
    }

    func sendAdvanceNow() {
        logger.info("sendAdvanceNow")
        sendCommand("advance_now")
    }

    func sendVolume(volume: Int) {
        logger.info("sendVolume: \(volume, privacy: .public)")
        sendCommand("volume", extra: ["volume": volume])
    }

    func sendVolumeReport(volume: Int) {
        logger.info("sendVolumeReport: \(volume, privacy: .public)")
        sendCommand("volume_report", extra: ["volume": volume])
    }

    // MARK: - Connection

    private func connect() async {
        guard shouldConnect, let token = authService.token else {
            logger.warning("connect: bailing — shouldConnect=\(self.shouldConnect, privacy: .public) token=\(self.authService.token != nil ? "present" : "null", privacy: .public)")
            return
        }
        // Re-entrancy guard: two near-simultaneous triggers (foreground +
        // network-restored) can both pass their !isConnected gates before
        // didOpen flips isConnected, opening a second socket. Bail if one
        // already exists; handleDisconnect nils webSocket before reconnecting.
        guard webSocket == nil else {
            logger.info("connect: socket already open/in-flight, skipping")
            return
        }

        let baseUrl = appPreferences.apiBaseUrl
        let wsBase = baseUrl
            .replacingOccurrences(of: "https://", with: "wss://")
            .replacingOccurrences(of: "http://", with: "ws://")
        guard let url = URL(string: "\(wsBase)/ws/connect") else {
            logger.error("connect: invalid URL from base \(wsBase, privacy: .public)")
            return
        }

        logger.info("Connecting to \(url.absoluteString, privacy: .public)")

        var request = URLRequest(url: url)
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")

        let session = URLSession(configuration: .default, delegate: self, delegateQueue: nil)
        urlSession = session
        let task = session.webSocketTask(with: request)
        webSocket = task
        task.resume()
        // receiveMessages() is started from didOpenWithProtocol delegate
    }

    private func receiveMessages() {
        webSocket?.receive { [weak self] result in
            Task { @MainActor [weak self] in
                guard let self else { return }
                switch result {
                case .success(let message):
                    self.handleMessage(message)
                    self.receiveMessages()
                case .failure(let error):
                    logger.warning("receiveMessages failure: \(error.localizedDescription, privacy: .public)")
                    break
                }
            }
        }
    }

    // MARK: - Message Handling

    private func handleMessage(_ message: URLSessionWebSocketTask.Message) {
        guard case .string(let text) = message,
              let data = text.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let type = json["type"] as? String else {
            logger.warning("handleMessage: failed to parse message")
            return
        }

        let decoder = JSONDecoder()
        switch type {
        case "state":
            if let stateData = try? JSONSerialization.data(withJSONObject: json["state"] as Any),
               let newState = try? decoder.decode(ConnectState.self, from: stateData) {
                // Version check: ignore stale broadcasts — but always accept the
                // first snapshot after a (re)connect, since the server may have
                // restarted and reset its version counter.
                if resyncOnReconnect {
                    resyncOnReconnect = false
                } else if let current = connectState, newState.version <= current.version {
                    logger.debug("Ignoring stale state v\(newState.version, privacy: .public) (current=\(current.version, privacy: .public))")
                    return
                }
                let myId = appPreferences.installId
                let isActive = newState.activeDeviceId == myId
                let isPlaying = streamPlayer.playbackState.isPlaying
                logger.info("State v\(newState.version, privacy: .public): show=\(newState.showId ?? "nil", privacy: .public) rec=\(newState.recordingId ?? "nil", privacy: .public) track=\(newState.trackIndex, privacy: .public)/\(newState.tracks.count, privacy: .public) playing=\(newState.playing, privacy: .public) activeDevice=\(newState.activeDeviceId ?? "nil", privacy: .public) isMe=\(isActive, privacy: .public) localPlaying=\(isPlaying, privacy: .public)")
                let old = connectState
                connectState = newState
                reactToState(old: old, new: newState)
            } else {
                logger.warning("handleMessage: failed to decode state")
            }
        case "devices":
            if let devicesData = try? JSONSerialization.data(withJSONObject: json["devices"] as Any) {
                devices = (try? decoder.decode([ConnectDevice].self, from: devicesData)) ?? []
                logger.info("Devices (\(self.devices.count, privacy: .public)): \(self.devices.map { "\($0.deviceName)[\($0.deviceType)]" }.joined(separator: ", "), privacy: .public)")
            }
        case "volume":
            if let volume = json["volume"] as? Int {
                logger.info("handleMessage: volume command \(volume, privacy: .public)")
                streamPlayer.volume = Float(volume) / 100.0
                activeDeviceVolume = volume
                sendVolumeReport(volume: volume)
            }
        case "volume_report":
            if let volume = json["volume"] as? Int {
                logger.info("handleMessage: volume_report \(volume, privacy: .public)")
                activeDeviceVolume = volume
            }
        case "time_sync":
            // Server stamps `serverTs` at send time and echoes `clientTs`.
            // NTP-style: one-way delay ≈ rtt/2, so server clock at our send
            // moment ≈ clientTs + rtt/2, giving offset = serverTs - that.
            guard let clientTs = (json["clientTs"] as? NSNumber)?.doubleValue,
                  let serverTs = (json["serverTs"] as? NSNumber)?.doubleValue else { return }
            let nowMs = Date().timeIntervalSince1970 * 1000.0
            let rtt = nowMs - clientTs
            let offset = serverTs - (clientTs + rtt / 2.0)
            if rtt < timeSyncBestRttMs {
                timeSyncBestRttMs = rtt
                serverTimeOffsetMs = offset
                logger.info("time_sync: rtt=\(Int(rtt), privacy: .public)ms offset=\(Int(offset), privacy: .public)ms (kept)")
            } else {
                logger.debug("time_sync: rtt=\(Int(rtt), privacy: .public)ms offset=\(Int(offset), privacy: .public)ms (dropped, best=\(Int(self.timeSyncBestRttMs), privacy: .public)ms)")
            }
        default:
            logger.debug("handleMessage: unknown type '\(type, privacy: .public)'")
        }
    }

    // MARK: - State Reaction

    private func reactToState(old: ConnectState?, new: ConnectState) {
        // Clear pending command if the server confirmed the expected transition
        if let cmd = pendingCommand {
            if cmd == "play" && new.playing {
                logger.info("reactToState: pending 'play' confirmed, clearing")
                pendingCommand = nil
            } else if cmd == "pause" && !new.playing {
                logger.info("reactToState: pending 'pause' confirmed, clearing")
                pendingCommand = nil
            } else if (cmd == "next" || cmd == "prev") && new.trackIndex != (old?.trackIndex ?? -1) {
                logger.info("reactToState: pending '\(cmd, privacy: .public)' confirmed (track \(old?.trackIndex ?? -1, privacy: .public) -> \(new.trackIndex, privacy: .public)), clearing")
                pendingCommand = nil
            } else if cmd == "seek" && new.positionMs != (old?.positionMs ?? -1) {
                logger.info("reactToState: pending 'seek' confirmed, clearing")
                pendingCommand = nil
            }
        }

        // Clear pending transfer when a device becomes active (transfer resolved)
        if pendingTransfer != nil, new.activeDeviceId != nil {
            logger.info("reactToState: transfer resolved, clearing pendingTransfer")
            pendingTransfer = nil
        }

        // ADR-0011 Chunk C: the device producing audio is the transport authority.
        // When the session is ownerless (server forgot the active device across a
        // restart/socket drop) and we're still playing this recording, KEEP PLAYING
        // and let the heartbeat ownership lease reclaim us server-side. Gated on
        // reclaimOnReconnect so a deliberate PARK (transfer/stop, which nulls active
        // on our LIVE socket) is NOT mistaken for a ghost — only a post-reconnect
        // null active is a ghost. Cause-agnostic (restart + disconnect both reconnect).
        let reclaimOwnerless = new.activeDeviceId == nil
            && streamPlayer.playbackState.isPlaying
            && streamPlayer.currentTrack?.metadata["recordingId"] == new.recordingId
            && reclaimOnReconnect
        // Once the server assigns an owner (us via the lease, or another device), the
        // post-reconnect ghost window is over: any later activeDevice=nil is then a
        // deliberate park we must pause for, not reclaim.
        if new.activeDeviceId != nil { reclaimOnReconnect = false }

        // If this device was active but is no longer — a different device took
        // over, or we were parked during a transfer — pause and report final
        // position. Skipped when the session went ownerless (reclaimOwnerless): a
        // null active device is a ghost we heal by holding the lease, not a takeover.
        let wasActive = old?.activeDeviceId == appPreferences.installId
        let nowActive = new.activeDeviceId == appPreferences.installId
        if wasActive && !nowActive && !reclaimOwnerless {
            let positionMs = Int(streamPlayer.progress.currentTime * 1000)
            logger.info("reactToState: parked/transferred away — pausing and reporting position \(positionMs, privacy: .public)ms")
            streamPlayer.pause()
            sendPosition(positionMs: positionMs)
        }

        // If the recording changed (or first state), load the show locally
        let localRecordingId = streamPlayer.currentTrack?.metadata["recordingId"]
        if let showId = new.showId, let recId = new.recordingId, recId != localRecordingId {
            let autoPlay = isActiveDevice && new.playing
            logger.info("reactToState: NEW RECORDING — server=\(recId, privacy: .public) local=\(localRecordingId ?? "nil", privacy: .public) isActive=\(self.isActiveDevice, privacy: .public) autoPlay=\(autoPlay, privacy: .public) track=\(new.trackIndex, privacy: .public)")
            if let onLoadShow {
                Task {
                    await onLoadShow(showId, recId, new.trackIndex, new.positionMs, autoPlay)
                }
            }
            stopPositionReporting()
            return
        }

        // Only drive local playback when this device is active (or we're the
        // ownerless ghost holding the lease — reclaimOwnerless computed above).
        guard isActiveDevice || reclaimOwnerless else {
            logger.info("reactToState: not active device, skipping playback control")
            reassertingTracks = false
            stopPositionReporting()
            return
        }

        // Server forgot our tracklist (a restart rehydrated the session from the
        // saved position only). We still hold the queue — re-send the load so
        // viewers' display and the server's next/prev get the tracks back. This
        // hydrates TRACKS ONLY: autoplay=false when we're the ownerless ghost, so
        // the load does NOT claim ownership — the heartbeat lease does that. (When
        // already active, autoplay tracks our real playing state.)
        if new.tracks.isEmpty {
            let queue = streamPlayer.loadedTracks
            let localRecId = streamPlayer.currentTrack?.metadata["recordingId"]
            if !queue.isEmpty, localRecId == new.recordingId, !reassertingTracks {
                reassertingTracks = true
                let sessionTracks = queue.map {
                    SessionTrack(title: $0.title, durationMs: Int(($0.duration ?? 0) * 1000))
                }
                let meta = streamPlayer.currentTrack?.metadata ?? [:]
                let idx = streamPlayer.queueState.currentIndex
                let posMs = Int(streamPlayer.progress.currentTime * 1000)
                let curDuration = (idx >= 0 && idx < queue.count) ? (queue[idx].duration ?? 0) : 0
                logger.info("reactToState: server tracks empty — re-asserting load (\(sessionTracks.count, privacy: .public) tracks, idx=\(idx, privacy: .public))")
                sendLoad(
                    showId: new.showId ?? meta["showId"] ?? "",
                    recordingId: new.recordingId ?? localRecId ?? "",
                    tracks: sessionTracks,
                    trackIndex: idx,
                    positionMs: posMs,
                    durationMs: Int(curDuration * 1000),
                    date: new.date ?? meta["showDate"],
                    venue: new.venue ?? meta["venue"],
                    location: new.location ?? meta["location"],
                    autoplay: nowActive && streamPlayer.playbackState.isPlaying
                )
            }
        } else {
            reassertingTracks = false
        }

        // Ownerless ghost: we are the transport authority — don't sync transport
        // down to the stale playing:false / saved position. Keep playing; the lease
        // claims us within a heartbeat, the server echoes activeDeviceId == us, and
        // normal active-device sync resumes from there.
        if reclaimOwnerless { return }

        // When this device just became active (e.g. transfer in), sync local player
        // to server state — the local player may be at a completely different track/position.
        //
        // BUT skip the transport sync when a pendingAdvance note is present: here we
        // "became active" only because we announced our OWN end-of-show (the server
        // claims the announcer active, ADR-0011). We are parked at the end of the show
        // waiting for the note-collector to advance — we know our position better than
        // the server, whose positionMs is the stale pre-end value. Without this guard
        // we seek back to that stale position and resume, replaying the tail, which
        // re-fires onShowCompleted and re-announces (resetting the countdown). The
        // note-collector owns the transition from here. (Mirrors reclaimOwnerless.)
        let justBecameActive = !wasActive && nowActive
        if justBecameActive && new.pendingAdvance == nil {
            let currentVolume = Int(streamPlayer.volume * 100)
            activeDeviceVolume = currentVolume
            sendVolumeReport(volume: currentVolume)
            let targetPositionMs = interpolatedPositionMs(new)
            if self.streamPlayer.queueState.currentIndex != new.trackIndex {
                logger.info("reactToState: became active, syncing track \(self.streamPlayer.queueState.currentIndex, privacy: .public) -> \(new.trackIndex, privacy: .public)")
                streamPlayer.skipTo(index: new.trackIndex, autoplay: new.playing)
            }
            let serverPosition = Double(targetPositionMs) / 1000.0
            if streamPlayer.playbackState.isPlaying {
                // Already playing the track — a direct seek is honored.
                if abs(streamPlayer.progress.currentTime - serverPosition) > 1 {
                    logger.info("reactToState: became active (playing), seeking to \(targetPositionMs, privacy: .public)ms (interpolated from \(new.positionMs, privacy: .public)ms)")
                    streamPlayer.seek(to: serverPosition)
                }
            } else {
                // Paused (e.g. this show was freshly hydrated): the engine drops
                // seeks while not playing, then play() would start from 0. Stash
                // the position as the first-play seek so the play() in the
                // playback-reconciliation step below lands at the right spot.
                logger.info("reactToState: became active (paused), pendingSeekOnFirstPlay=\(targetPositionMs, privacy: .public)ms (interpolated from \(new.positionMs, privacy: .public)ms)")
                streamPlayer.pendingSeekOnFirstPlay = serverPosition
            }
        }

        // React to track changes from remote controllers (while already active).
        // Guard against the echo from our own sendNext(): the engine already advanced
        // locally, so queueState.currentIndex already equals new.trackIndex — calling
        // skipTo again would restart the track and stop playback.
        if !justBecameActive, let oldState = old, new.trackIndex != oldState.trackIndex,
           streamPlayer.queueState.currentIndex != new.trackIndex {
            logger.info("reactToState: track changed \(oldState.trackIndex, privacy: .public) -> \(new.trackIndex, privacy: .public), skipping to index")
            streamPlayer.skipTo(index: new.trackIndex, autoplay: new.playing)
        }

        // React to seek from remote controllers (while already active).
        // Compare against local position (not old server state) so our own position
        // reports echoing back don't cause unnecessary seeks.
        if !justBecameActive, let oldState = old, new.trackIndex == oldState.trackIndex, new.positionMs != oldState.positionMs {
            let localPositionMs = Int(streamPlayer.progress.currentTime * 1000)
            let delta = abs(new.positionMs - localPositionMs)
            if delta > 2000 {
                let targetTime = Double(new.positionMs) / 1000.0
                logger.info("reactToState: seek from remote, jumping to \(new.positionMs, privacy: .public)ms (delta=\(delta, privacy: .public))")
                streamPlayer.seek(to: targetTime)
            }
        }

        let localPlaying = streamPlayer.playbackState.isPlaying
        if new.playing && !localPlaying {
            logger.info("reactToState: server says play, local paused -> calling streamPlayer.play()")
            streamPlayer.play()
        } else if !new.playing && localPlaying {
            logger.info("reactToState: server says pause, local playing -> calling streamPlayer.pause()")
            streamPlayer.pause()
        } else {
            logger.debug("reactToState: no playback change needed (server.playing=\(new.playing, privacy: .public) local.playing=\(localPlaying, privacy: .public))")
        }

        // Manage periodic position reporting — run only when active + playing
        if isActiveDevice && new.playing {
            if positionReportTask == nil {
                startPositionReporting()
            }
        } else {
            stopPositionReporting()
        }
    }

    // MARK: - Position Interpolation

    private func interpolatedPositionMs(_ state: ConnectState) -> Int {
        guard state.playing else { return state.positionMs }
        // positionTs is the server's wall-clock; translate our local epoch time
        // into server space via serverTimeOffsetMs before subtracting. Without
        // this, clients with skewed clocks mis-interpolate and misseek on
        // transfer (or skip on repeated position broadcasts).
        let serverNowMs = Date().timeIntervalSince1970 * 1000.0 + serverTimeOffsetMs
        let elapsedMs = serverNowMs - state.positionTs
        let interpolated = state.positionMs + Int(elapsedMs)
        return max(0, min(interpolated, state.durationMs))
    }

    // MARK: - Time Sync

    private func startTimeSyncRefresh() {
        stopTimeSyncRefresh()
        runTimeSync()
        timeSyncRefreshTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: Self.timeSyncRefreshInterval)
                guard !Task.isCancelled else { break }
                await MainActor.run { self?.runTimeSync() }
            }
        }
    }

    private func stopTimeSyncRefresh() {
        timeSyncRefreshTask?.cancel()
        timeSyncRefreshTask = nil
    }

    /// Send 3 time_sync probes spaced 200ms apart. Replies are scored in
    /// `handleMessage`; only the sample with the lowest RTT wins (NTP-style).
    private func runTimeSync() {
        timeSyncBestRttMs = .infinity
        Task { [weak self] in
            guard let self else { return }
            for i in 0..<Self.timeSyncSamples {
                let nowMs = Date().timeIntervalSince1970 * 1000.0
                let msg = "{\"type\":\"time_sync\",\"clientTs\":\(nowMs)}"
                await MainActor.run {
                    self.webSocket?.send(.string(msg)) { _ in }
                }
                if i < Self.timeSyncSamples - 1 {
                    try? await Task.sleep(nanoseconds: Self.timeSyncSampleSpacing)
                }
            }
        }
    }

    // MARK: - Send Helpers

    private func sendCommand(_ action: String, extra: [String: Any] = [:]) {
        var msg: [String: Any] = ["type": "command", "action": action]
        for (key, value) in extra { msg[key] = value }
        guard let data = try? JSONSerialization.data(withJSONObject: msg),
              let text = String(data: data, encoding: .utf8) else {
            logger.error("sendCommand: failed to serialize \(action, privacy: .public)")
            return
        }
        guard webSocket != nil else {
            logger.warning("sendCommand: \(action, privacy: .public) DROPPED — webSocket is nil")
            return
        }
        logger.debug("sendCommand: \(action, privacy: .public) (\(text.count, privacy: .public) bytes)")
        webSocket?.send(.string(text)) { error in
            if let error {
                logger.error("sendCommand: \(action, privacy: .public) send error: \(error.localizedDescription, privacy: .public)")
            }
        }
    }

    // MARK: - Registration

    private func sendRegister() {
        // Arm ghost-reclaim: a null active device in the first state(s) after this
        // (re)connect is a ghost to heal (lease), not a deliberate park.
        reclaimOnReconnect = true
        let deviceId = appPreferences.installId
        #if canImport(UIKit)
        let deviceName = UIDevice.current.name
        #else
        let deviceName = "iPhone"
        #endif

        // ADR-0011 §3 / docs/PROTOCOL.md: build identity for telemetry only —
        // the server never branches behavior on appVersion.
        let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown"

        logger.info("sendRegister: deviceId=\(deviceId, privacy: .public) name=\(deviceName, privacy: .public) proto=\(Self.protocolVersion, privacy: .public) app=\(appVersion, privacy: .public)")
        // Mixed value types (string + int) → encode as a JSON object explicitly.
        let msg: [String: Any] = [
            "type": "register",
            "deviceId": deviceId,
            "deviceType": "ios",
            "deviceName": deviceName,
            // Wire-contract version the server may branch on; see docs/PROTOCOL.md.
            "protocolVersion": Self.protocolVersion,
            "appVersion": appVersion,
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: msg),
              let text = String(data: data, encoding: .utf8) else { return }
        webSocket?.send(.string(text)) { _ in }
    }

    // MARK: - Position Reporting

    private static let positionReportInterval: UInt64 = 5_000_000_000 // 5s in nanoseconds

    private func startPositionReporting() {
        stopPositionReporting()
        positionReportTask = Task {
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: Self.positionReportInterval)
                guard !Task.isCancelled else { break }
                let positionMs = Int(streamPlayer.progress.currentTime * 1000)
                let localIndex = streamPlayer.queueState.currentIndex
                if let serverIndex = connectState?.trackIndex, localIndex != serverIndex {
                    // The active device's track changed outside Connect — a lock-
                    // screen / headset / CarPlay skip drives StreamPlayer directly,
                    // so no sendNext fires. Forward as an absolute seek so the index
                    // resyncs; a bare position report would land the new position on
                    // the stale server index. sendSeek arms no pending spinner.
                    let durationMs = Int(streamPlayer.progress.duration * 1000)
                    logger.info("positionReport: local track \(localIndex, privacy: .public) != server \(serverIndex, privacy: .public) — seek sync (pos=\(positionMs, privacy: .public))")
                    sendSeek(trackIndex: localIndex, positionMs: positionMs, durationMs: durationMs)
                } else {
                    sendPosition(positionMs: positionMs)
                }
            }
        }
    }

    private func stopPositionReporting() {
        positionReportTask?.cancel()
        positionReportTask = nil
    }

    // MARK: - Heartbeat

    private func startVolumeObservation() {
        let session = AVAudioSession.sharedInstance()
        try? session.setActive(true)
        volumeObservation = session.observe(\.outputVolume, options: [.new]) { [weak self] _, _ in
            Task { @MainActor [weak self] in
                guard let self, self.connectState?.activeDeviceId != nil else { return }
                self.showVolumeUI = true
            }
        }
    }

    private func stopVolumeObservation() {
        volumeObservation?.invalidate()
        volumeObservation = nil
    }

    private func startHeartbeat() {
        stopHeartbeat()
        heartbeatTask = Task {
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: Self.heartbeatInterval)
                guard !Task.isCancelled else { break }
                webSocket?.send(.string(buildHeartbeat())) { _ in }
            }
        }
    }

    // ADR-0011 Chunk B: renew the ownership lease. When audio is loaded locally,
    // piggyback {playing, recordingId, positionMs} so the server can heal an
    // ownerless session from our heartbeat (the restart/disconnect "ghost" fix).
    // Plain heartbeat when nothing is loaded. See docs/PROTOCOL.md.
    private func buildHeartbeat() -> String {
        let plain = #"{"type":"heartbeat"}"#
        guard let recordingId = streamPlayer.currentTrack?.metadata["recordingId"] else {
            return plain
        }
        let lease: [String: Any] = [
            "type": "heartbeat",
            "playing": streamPlayer.playbackState.isPlaying,
            "recordingId": recordingId,
            "positionMs": Int(streamPlayer.progress.currentTime * 1000),
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: lease),
              let text = String(data: data, encoding: .utf8) else { return plain }
        return text
    }

    private func stopHeartbeat() {
        heartbeatTask?.cancel()
        heartbeatTask = nil
    }

    // MARK: - Disconnect / Reconnect

    func handleNetworkRestored() {
        guard shouldConnect, !isConnected else { return }
        logger.info("handleNetworkRestored: cancelling backoff, reconnecting immediately")
        reconnectTask?.cancel()
        reconnectTask = nil
        reconnectAttempt = 0
        Task { await connect() }
    }

    private func handleDisconnect(closeCode: Int?) {
        stopHeartbeat()
        stopTimeSyncRefresh()
        stopPositionReporting()
        stopVolumeObservation()
        isConnected = false
        serverTimeOffsetMs = 0
        timeSyncBestRttMs = .infinity
        webSocket = nil

        // 4003 = Unauthorized (terminal — token invalid)
        if !shouldConnect || closeCode == 4003 {
            logger.info("handleDisconnect: not reconnecting (shouldConnect=\(self.shouldConnect, privacy: .public) code=\(closeCode ?? -1, privacy: .public))")
            return
        }

        let delay = Self.reconnectDelays[min(reconnectAttempt, Self.reconnectDelays.count - 1)]
        reconnectAttempt += 1
        logger.info("handleDisconnect: reconnecting in \(delay, privacy: .public)s (attempt \(self.reconnectAttempt, privacy: .public))")
        reconnectTask = Task {
            try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
            guard !Task.isCancelled else { return }
            await connect()
        }
    }
}

// MARK: - URLSessionWebSocketDelegate

extension ConnectService: URLSessionWebSocketDelegate {
    nonisolated func urlSession(
        _ session: URLSession,
        webSocketTask: URLSessionWebSocketTask,
        didOpenWithProtocol protocol: String?
    ) {
        Task { @MainActor [weak self] in
            guard let self, self.shouldConnect else {
                logger.warning("WebSocket opened but shouldConnect=false, closing")
                webSocketTask.cancel(with: .normalClosure, reason: nil)
                return
            }
            logger.info("Connected (WebSocket open)")
            self.isConnected = true
            self.reconnectAttempt = 0
            self.resyncOnReconnect = true
            self.sendRegister()
            self.startHeartbeat()
            self.startTimeSyncRefresh()
            self.startVolumeObservation()
            self.receiveMessages()
        }
    }

    nonisolated func urlSession(
        _ session: URLSession,
        webSocketTask: URLSessionWebSocketTask,
        didCloseWith closeCode: URLSessionWebSocketTask.CloseCode,
        reason: Data?
    ) {
        let reasonStr = reason.flatMap { String(data: $0, encoding: .utf8) } ?? "(none)"
        logger.info("WebSocket closed: code=\(closeCode.rawValue, privacy: .public) reason=\(reasonStr, privacy: .public)")
        Task { @MainActor [weak self] in
            self?.handleDisconnect(closeCode: closeCode.rawValue)
        }
    }

    nonisolated func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didCompleteWithError error: Error?
    ) {
        guard let error else { return }
        logger.warning("WebSocket error: \(error.localizedDescription, privacy: .public)")
        Task { @MainActor [weak self] in
            self?.handleDisconnect(closeCode: nil)
        }
    }
}
