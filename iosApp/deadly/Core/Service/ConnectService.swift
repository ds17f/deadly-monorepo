import Foundation
import SwiftAudioStreamEx
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
    /// Parameters: (showId, trackIndex, positionMs, autoPlay)
    var onLoadShow: ((String, Int, Int, Bool) async -> Void)?

    private let appPreferences: AppPreferences
    private let authService: AuthService
    private let streamPlayer: StreamPlayer

    private var webSocket: URLSessionWebSocketTask?
    private var urlSession: URLSession?
    private var heartbeatTask: Task<Void, Never>?
    private var positionReportTask: Task<Void, Never>?
    private var reconnectTask: Task<Void, Never>?
    private var reconnectAttempt = 0
    private var shouldConnect = false

    private static let reconnectDelays: [Double] = [1, 2, 4, 8, 30]
    private static let heartbeatInterval: UInt64 = 15_000_000_000 // 15s in nanoseconds

    init(appPreferences: AppPreferences, authService: AuthService, streamPlayer: StreamPlayer) {
        self.appPreferences = appPreferences
        self.authService = authService
        self.streamPlayer = streamPlayer
        super.init()
    }

    // MARK: - Public Interface

    func startIfAuthenticated() {
        let hasToken = authService.token != nil
        logger.info("startIfAuthenticated: token=\(hasToken ? "present" : "null", privacy: .public) shouldConnect=\(self.shouldConnect, privacy: .public)")
        guard hasToken else { return }
        guard !shouldConnect else { return }
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
        let ws = webSocket
        webSocket = nil
        ws?.cancel(with: .normalClosure, reason: nil)
        isConnected = false
        devices = []
        connectState = nil
        pendingCommand = nil
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
        // Only show spinner if the server isn't already playing — a redundant play
        // won't trigger a state broadcast so pendingCommand would never clear.
        if connectState?.playing != true {
            pendingCommand = "play"
        }
        sendCommand("play")
    }

    func sendPause() {
        logger.info("sendPause (pending=\(self.pendingCommand ?? "nil", privacy: .public) serverPlaying=\(self.connectState?.playing ?? false, privacy: .public))")
        if connectState?.playing == true {
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
        pendingCommand = "next"
        sendCommand("next")
    }

    func sendPrev() {
        logger.info("sendPrev (pending=\(self.pendingCommand ?? "nil", privacy: .public) -> prev)")
        pendingCommand = "prev"
        sendCommand("prev")
    }

    func sendTransfer(targetDeviceId: String) {
        guard connectState?.showId != nil else { return }
        logger.info("sendTransfer: target=\(targetDeviceId, privacy: .public)")
        pendingTransfer = targetDeviceId
        sendCommand("transfer", extra: ["targetDeviceId": targetDeviceId])
    }

    func sendPosition(positionMs: Int) {
        logger.info("sendPosition: pos=\(positionMs, privacy: .public)")
        sendCommand("position", extra: ["positionMs": positionMs])
    }

    // MARK: - Connection

    private func connect() async {
        guard shouldConnect, let token = authService.token else {
            logger.warning("connect: bailing — shouldConnect=\(self.shouldConnect, privacy: .public) token=\(self.authService.token != nil ? "present" : "null", privacy: .public)")
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
                // Version check: ignore stale broadcasts
                if let current = connectState, newState.version <= current.version {
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

        // If this device was active but no longer is, pause audio and report final position
        let wasActive = old?.activeDeviceId == appPreferences.installId
        let nowActive = new.activeDeviceId == appPreferences.installId
        if wasActive && !nowActive {
            let positionMs = Int(streamPlayer.progress.currentTime * 1000)
            logger.info("reactToState: transferred away — pausing and reporting position \(positionMs, privacy: .public)ms")
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
                    await onLoadShow(showId, new.trackIndex, new.positionMs, autoPlay)
                }
            }
            stopPositionReporting()
            return
        }

        // Only drive local playback when this device is the active device
        guard isActiveDevice else {
            logger.info("reactToState: not active device, skipping playback control")
            stopPositionReporting()
            return
        }

        // When this device just became active (e.g. transfer in), sync local player
        // to server state — the local player may be at a completely different track/position.
        let justBecameActive = !wasActive && nowActive
        if justBecameActive {
            if self.streamPlayer.queueState.currentIndex != new.trackIndex {
                logger.info("reactToState: became active, syncing track \(self.streamPlayer.queueState.currentIndex, privacy: .public) -> \(new.trackIndex, privacy: .public)")
                streamPlayer.skipTo(index: new.trackIndex, autoplay: new.playing)
            }
            let serverPosition = Double(new.positionMs) / 1000.0
            if abs(streamPlayer.progress.currentTime - serverPosition) > 1 {
                logger.info("reactToState: became active, syncing position to \(new.positionMs, privacy: .public)ms")
                streamPlayer.seek(to: serverPosition)
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
        let deviceId = appPreferences.installId
        #if canImport(UIKit)
        let deviceName = UIDevice.current.name
        #else
        let deviceName = "iPhone"
        #endif

        logger.info("sendRegister: deviceId=\(deviceId, privacy: .public) name=\(deviceName, privacy: .public)")
        let msg: [String: String] = [
            "type": "register",
            "deviceId": deviceId,
            "deviceType": "ios",
            "deviceName": deviceName,
        ]
        guard let data = try? JSONEncoder().encode(msg),
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
                sendPosition(positionMs: positionMs)
            }
        }
    }

    private func stopPositionReporting() {
        positionReportTask?.cancel()
        positionReportTask = nil
    }

    // MARK: - Heartbeat

    private func startHeartbeat() {
        stopHeartbeat()
        heartbeatTask = Task {
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: Self.heartbeatInterval)
                guard !Task.isCancelled else { break }
                webSocket?.send(.string(#"{"type":"heartbeat"}"#)) { _ in }
            }
        }
    }

    private func stopHeartbeat() {
        heartbeatTask?.cancel()
        heartbeatTask = nil
    }

    // MARK: - Disconnect / Reconnect

    private func handleDisconnect(closeCode: Int?) {
        stopHeartbeat()
        stopPositionReporting()
        isConnected = false
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
            self.sendRegister()
            self.startHeartbeat()
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
