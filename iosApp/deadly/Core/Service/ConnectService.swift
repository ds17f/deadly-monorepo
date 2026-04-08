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

    var isActiveDevice: Bool {
        guard let state = connectState else { return false }
        return state.activeDeviceId == appPreferences.installId
    }

    var isRemoteControlling: Bool {
        guard let state = connectState else { return false }
        return state.activeDeviceId != nil && state.activeDeviceId != appPreferences.installId
    }

    private let appPreferences: AppPreferences
    private let authService: AuthService
    private let streamPlayer: StreamPlayer

    private var webSocket: URLSessionWebSocketTask?
    private var urlSession: URLSession?
    private var heartbeatTask: Task<Void, Never>?
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
        logger.info("sendPlay (pending=\(self.pendingCommand ?? "nil", privacy: .public) -> play)")
        pendingCommand = "play"
        sendCommand("play")
    }

    func sendPause() {
        logger.info("sendPause (pending=\(self.pendingCommand ?? "nil", privacy: .public) -> pause)")
        pendingCommand = "pause"
        sendCommand("pause")
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
        receiveMessages()
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
                logger.info("State v\(newState.version, privacy: .public): show=\(newState.showId ?? "nil", privacy: .public) rec=\(newState.recordingId ?? "nil", privacy: .public) track=\(newState.trackIndex, privacy: .public) playing=\(newState.playing, privacy: .public) activeDevice=\(newState.activeDeviceId ?? "nil", privacy: .public) isMe=\(isActive, privacy: .public) localPlaying=\(isPlaying, privacy: .public)")
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
            }
        }

        // Only drive local audio when this device is the active device
        guard isActiveDevice else {
            logger.info("reactToState: not active device, skipping playback control")
            return
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
