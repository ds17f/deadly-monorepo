import Foundation
#if canImport(UIKit)
import UIKit
#endif

/// Manages the Connect WebSocket lifecycle and state.
///
/// Observes `AuthService.token` — connects when non-nil, disconnects when nil.
/// Sends `register` on open, parses `devices` responses.
@Observable
@MainActor
final class ConnectService {

    // MARK: - Published state

    private(set) var connectionState: ConnectConnectionState = .disconnected
    private(set) var devices: [ConnectDevice] = []

    // MARK: - Dependencies

    private let authService: AuthService
    private let appPreferences: AppPreferences

    // MARK: - Internal

    private let webSocket = ConnectWebSocket()
    private var observationTask: Task<Void, Never>?

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

    init(authService: AuthService, appPreferences: AppPreferences) {
        self.authService = authService
        self.appPreferences = appPreferences

        setupWebSocketCallbacks()
        startObservingAuth()
    }

    // MARK: - Auth observation

    private func startObservingAuth() {
        observationTask = Task { [weak self] in
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
        print("[Connect] Sent register: deviceId=\(deviceId)")
    }

    // MARK: - Incoming message handling

    private func handleMessage(_ text: String) {
        guard let data = text.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let type = obj["type"] as? String else { return }

        switch type {
        case "devices":
            if let devicesData = try? JSONSerialization.data(withJSONObject: obj["devices"] ?? []),
               let list = try? JSONDecoder().decode([ConnectDevice].self, from: devicesData) {
                devices = list
                print("[Connect] Devices: \(list.map { "\($0.name) (\($0.type))" })")
            }
        default:
            break
        }
    }
}
