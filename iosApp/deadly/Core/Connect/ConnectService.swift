import Foundation
import SwiftAudioStreamEx
#if canImport(UIKit)
import UIKit
#endif

/// Manages the Connect WebSocket lifecycle and state.
///
/// Observes `AuthService.token` — connects when non-nil, disconnects when nil.
/// Sends `register` on open, handles all incoming message types, and
/// provides outgoing helpers for session/position/command messages.
@Observable
@MainActor
final class ConnectService {

    // MARK: - Published state

    private(set) var connectionState: ConnectConnectionState = .disconnected
    private(set) var devices: [ConnectDevice] = []
    private(set) var userState: UserPlaybackState?

    var isActiveDevice: Bool {
        userState?.activeDeviceId == deviceId
    }

    var interpolatedPositionMs: Int {
        guard let state = userState else { return 0 }
        guard state.isPlaying else { return state.positionMs }
        let elapsed = Date().timeIntervalSince1970 * 1000 - state.updatedAt
        return min(state.positionMs + Int(elapsed), state.durationMs)
    }

    // MARK: - Dependencies

    private let authService: AuthService
    private let appPreferences: AppPreferences
    private let streamPlayer: StreamPlayer
    private let playlistService: PlaylistServiceImpl

    // MARK: - Internal

    private let webSocket = ConnectWebSocket()
    private var positionTimer: Timer?
    private var observationTask: Task<Void, Never>?
    private var lastSyncedShowId: String?
    private var lastSyncedTrackIndex: Int?

    private var deviceId: String {
        let key = "connect_device_id"
        if let existing = UserDefaults.standard.string(forKey: key) {
            return existing
        }
        let newId = UUID().uuidString
        UserDefaults.standard.set(newId, forKey: key)
        return newId
    }

    private var deviceName: String {
        #if canImport(UIKit)
        return UIDevice.current.name
        #else
        return "Mac"
        #endif
    }

    // MARK: - Init

    init(
        authService: AuthService,
        appPreferences: AppPreferences,
        streamPlayer: StreamPlayer,
        playlistService: PlaylistServiceImpl
    ) {
        self.authService = authService
        self.appPreferences = appPreferences
        self.streamPlayer = streamPlayer
        self.playlistService = playlistService

        setupWebSocketCallbacks()
        startObservingAuth()
        startObservingPlayback()
    }

    // MARK: - Auth observation

    private func startObservingAuth() {
        observationTask = Task { [weak self] in
            // Continuously observe @Observable token property
            var lastToken: String?
            while !Task.isCancelled {
                guard let self else { return }
                let token = self.authService.token
                if token != lastToken {
                    lastToken = token
                    if token != nil {
                        self.connect()
                    } else {
                        self.disconnect()
                    }
                }
                try? await Task.sleep(for: .milliseconds(500))
            }
        }
    }

    // MARK: - Playback observation

    private func startObservingPlayback() {
        // Observe StreamPlayer state changes and send session_update
        Task { [weak self] in
            var lastState: String?
            var lastTrackId: UUID?
            while !Task.isCancelled {
                guard let self else { return }
                let trackId = self.streamPlayer.currentTrack?.id
                let state = self.streamPlayer.playbackState
                let stateKey = "\(trackId?.uuidString ?? "")-\(state)"

                if stateKey != lastState {
                    lastState = stateKey
                    lastTrackId = trackId
                    if self.isActiveDevice || self.userState?.activeDeviceId == nil {
                        if let playbackState = self.buildPlaybackState() {
                            self.announcePlayback(playbackState)
                        }
                        self.updatePositionTimer()
                    } else {
                        self.positionTimer?.invalidate()
                        self.positionTimer = nil
                    }
                }
                try? await Task.sleep(for: .milliseconds(300))
            }
        }
    }

    private func updatePositionTimer() {
        positionTimer?.invalidate()
        positionTimer = nil

        guard streamPlayer.playbackState == .playing else { return }
        guard isActiveDevice || userState?.activeDeviceId == nil else { return }
        positionTimer = Timer.scheduledTimer(withTimeInterval: 15, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self, let state = self.buildPlaybackState() else { return }
                self.sendPositionUpdate(state)
            }
        }
    }

    // MARK: - Connection

    func connect() {
        guard let token = authService.token else { return }
        if connectionState == .connected || connectionState == .connecting { return }

        let wsUrl = appPreferences.apiBaseUrl
            .replacingOccurrences(of: "https://", with: "wss://")
            .replacingOccurrences(of: "http://", with: "ws://")
            + "/ws/connect"

        guard let url = URL(string: wsUrl) else { return }

        connectionState = .connecting
        webSocket.connect(url: url, token: token)
    }

    func disconnect() {
        webSocket.disconnect()
        connectionState = .disconnected
        devices = []
        userState = nil
        positionTimer?.invalidate()
        positionTimer = nil
        lastSyncedShowId = nil
        lastSyncedTrackIndex = nil
    }

    // MARK: - Outgoing messages

    func announcePlayback(_ state: ConnectPlaybackState) {
        lastSyncedShowId = state.showId
        lastSyncedTrackIndex = state.trackIndex
        webSocket.send(SessionUpdateMessage(state: state))
    }

    func sendPositionUpdate(_ state: ConnectPlaybackState) {
        webSocket.send(PositionUpdateMessage(state: state))
    }

    func claimSession() {
        webSocket.send(SessionClaimMessage())
    }

    func playOnDevice(targetDeviceId: String, state: ConnectPlaybackState) {
        webSocket.send(SessionPlayOnMessage(targetDeviceId: targetDeviceId, state: state))
    }

    func sendCommand(targetDeviceId: String, command: PlaybackCommand) {
        webSocket.send(CommandSendMessage(targetDeviceId: targetDeviceId, command: command))
    }

    func clearState() {
        webSocket.send(StateClearMessage())
    }

    // MARK: - WebSocket callbacks

    private func setupWebSocketCallbacks() {
        webSocket.onOpen = { [weak self] in
            Task { @MainActor [weak self] in
                guard let self else { return }
                self.connectionState = .connected
                self.sendRegister()
            }
        }

        webSocket.onClose = { [weak self] in
            Task { @MainActor [weak self] in
                guard let self else { return }
                self.connectionState = .reconnecting
            }
        }

        webSocket.onMessage = { [weak self] text in
            Task { @MainActor [weak self] in
                self?.handleMessage(text)
            }
        }
    }

    private func sendRegister() {
        let msg = RegisterMessage(
            device: RegisterDevice(
                deviceId: deviceId,
                type: "ios",
                name: deviceName,
                capabilities: ["playback", "control"]
            )
        )
        webSocket.send(msg)
    }

    // MARK: - Incoming message handling

    private func handleMessage(_ text: String) {
        guard let data = text.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let type = obj["type"] as? String else { return }

        let decoder = JSONDecoder()

        switch type {
        case "devices":
            if let devicesData = try? JSONSerialization.data(withJSONObject: obj["devices"] ?? []),
               let list = try? decoder.decode([ConnectDevice].self, from: devicesData) {
                devices = list
            }

        case "user_state":
            if let stateValue = obj["state"] {
                if stateValue is NSNull {
                    userState = nil
                } else if let stateData = try? JSONSerialization.data(withJSONObject: stateValue),
                          let state = try? decoder.decode(UserPlaybackState.self, from: stateData) {
                    userState = state
                    // Pause local audio if another device became active
                    if let activeId = state.activeDeviceId,
                       activeId != deviceId,
                       streamPlayer.playbackState == .playing {
                        streamPlayer.pause()
                    }
                    // Sync local player to match server state
                    syncLocalPlayer(to: state)
                }
            }

        case "command_received":
            if let commandData = try? JSONSerialization.data(withJSONObject: obj["command"] ?? [:]),
               let command = try? decoder.decode(PlaybackCommand.self, from: commandData) {
                handleCommand(command)
            }

        case "session_play_on":
            if let stateData = try? JSONSerialization.data(withJSONObject: obj["state"] ?? [:]),
               let state = try? decoder.decode(ConnectPlaybackState.self, from: stateData) {
                handlePlayOn(state)
            }

        case "active_session":
            break // Legacy — ignored

        case "error":
            break // Could log obj["message"]

        default:
            break
        }
    }

    // MARK: - Remote → Local

    private func handleCommand(_ command: PlaybackCommand) {
        switch command.action {
        case "play":
            streamPlayer.play()
        case "pause":
            streamPlayer.pause()
        case "next":
            streamPlayer.next()
        case "prev":
            streamPlayer.previous()
        case "seek":
            if let seekMs = command.seekMs {
                streamPlayer.seek(to: Double(seekMs) / 1000.0)
            }
        case "stop":
            streamPlayer.pause()
        default:
            break
        }
    }

    private func handlePlayOn(_ state: ConnectPlaybackState) {
        Task {
            await playlistService.loadShow(state.showId)
            playlistService.playTrack(at: state.trackIndex)
            if state.positionMs > 0 {
                try? await Task.sleep(for: .milliseconds(500))
                streamPlayer.seek(to: Double(state.positionMs) / 1000.0)
            }
        }
    }

    // MARK: - Local player sync

    private func syncLocalPlayer(to state: UserPlaybackState) {
        let showChanged = state.showId != lastSyncedShowId
        let trackChanged = state.trackIndex != lastSyncedTrackIndex
        guard showChanged || trackChanged else { return }

        lastSyncedShowId = state.showId
        lastSyncedTrackIndex = state.trackIndex

        Task {
            if showChanged {
                await playlistService.loadShow(state.showId)
                guard !playlistService.tracks.isEmpty else { return }
            }
            let trackIndex = min(state.trackIndex, playlistService.tracks.count - 1)
            playlistService.playTrack(at: trackIndex)

            // Wait for playback to start so we can seek
            let deadline = Date.now.addingTimeInterval(10)
            while streamPlayer.playbackState != .playing && Date.now < deadline {
                try? await Task.sleep(for: .milliseconds(100))
            }
            guard streamPlayer.playbackState == .playing else { return }

            if state.positionMs > 0 {
                streamPlayer.volume = 0
                streamPlayer.seek(to: Double(state.positionMs) / 1000.0)
                try? await Task.sleep(for: .milliseconds(300))
                streamPlayer.volume = 1
            }

            // If we're not the active device, pause (just hydrating)
            if !isActiveDevice {
                streamPlayer.pause()
            }
        }
    }

    // MARK: - Helpers

    private func buildPlaybackState() -> ConnectPlaybackState? {
        guard let track = streamPlayer.currentTrack else { return nil }
        let showId = track.metadata["showId"] ?? ""
        let recordingId = track.metadata["recordingId"] ?? ""
        guard !showId.isEmpty else { return nil }

        return ConnectPlaybackState(
            showId: showId,
            recordingId: recordingId,
            trackIndex: streamPlayer.queueState.currentIndex,
            positionMs: Int(streamPlayer.progress.currentTime * 1000),
            durationMs: Int(streamPlayer.progress.duration * 1000),
            trackTitle: track.title,
            status: mapPlaybackStatus(streamPlayer.playbackState),
            date: track.metadata["showDate"],
            venue: track.metadata["venue"],
            location: track.metadata["location"]
        )
    }

    private func mapPlaybackStatus(_ state: SwiftAudioStreamEx.PlaybackState) -> String {
        switch state {
        case .playing:
            return "playing"
        case .paused, .buffering, .loading:
            return "paused"
        case .idle, .ended:
            return "stopped"
        case .error:
            return "stopped"
        }
    }
}
