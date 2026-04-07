import Foundation
#if canImport(UIKit)
import UIKit
#endif

/// Manages the Connect v2 WebSocket connection.
/// Auto-registers this device, maintains heartbeat, and publishes device/state updates.
@Observable
@MainActor
final class ConnectService: NSObject {
    private(set) var devices: [ConnectDevice] = []
    private(set) var connectState: ConnectState?
    private(set) var isConnected = false

    private let appPreferences: AppPreferences
    private let authService: AuthService

    private var webSocket: URLSessionWebSocketTask?
    private var urlSession: URLSession?
    private var heartbeatTask: Task<Void, Never>?
    private var reconnectTask: Task<Void, Never>?
    private var reconnectAttempt = 0
    private var shouldConnect = false

    private static let reconnectDelays: [Double] = [1, 2, 4, 8, 30]
    private static let heartbeatInterval: UInt64 = 15_000_000_000 // 15s in nanoseconds

    init(appPreferences: AppPreferences, authService: AuthService) {
        self.appPreferences = appPreferences
        self.authService = authService
        super.init()
    }

    // MARK: - Public Interface

    func startIfAuthenticated() {
        guard authService.token != nil else { return }
        guard !shouldConnect else { return }
        shouldConnect = true
        reconnectAttempt = 0
        Task { await connect() }
    }

    func stop() {
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
    }

    // MARK: - Connection

    private func connect() async {
        guard shouldConnect, let token = authService.token else { return }

        let baseUrl = appPreferences.apiBaseUrl
        let wsBase = baseUrl
            .replacingOccurrences(of: "https://", with: "wss://")
            .replacingOccurrences(of: "http://", with: "ws://")
        guard let encoded = token.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "\(wsBase)/ws/connect?token=\(encoded)") else {
            return
        }

        let session = URLSession(configuration: .default, delegate: self, delegateQueue: nil)
        urlSession = session
        let task = session.webSocketTask(with: url)
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
                case .failure:
                    // onClose fires via delegate; reconnect logic lives there
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
              let type = json["type"] as? String else { return }

        let decoder = JSONDecoder()
        switch type {
        case "state":
            if let stateData = try? JSONSerialization.data(withJSONObject: json["state"] as Any) {
                connectState = try? decoder.decode(ConnectState.self, from: stateData)
            }
        case "devices":
            if let devicesData = try? JSONSerialization.data(withJSONObject: json["devices"] as Any) {
                devices = (try? decoder.decode([ConnectDevice].self, from: devicesData)) ?? []
            }
        default:
            break
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
        if !shouldConnect || closeCode == 4003 { return }

        let delay = Self.reconnectDelays[min(reconnectAttempt, Self.reconnectDelays.count - 1)]
        reconnectAttempt += 1
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
                webSocketTask.cancel(with: .normalClosure, reason: nil)
                return
            }
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
        Task { @MainActor [weak self] in
            self?.handleDisconnect(closeCode: closeCode.rawValue)
        }
    }

    nonisolated func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didCompleteWithError error: Error?
    ) {
        guard error != nil else { return }
        Task { @MainActor [weak self] in
            self?.handleDisconnect(closeCode: nil)
        }
    }
}
