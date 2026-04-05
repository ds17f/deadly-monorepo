import Foundation
import os.log

private let wsLog = Logger(subsystem: "com.grateful.deadly", category: "ConnectWS")

/// Low-level WebSocket wrapper around URLSessionWebSocketTask
/// with automatic exponential-backoff reconnection (1 s → 30 s).
final class ConnectWebSocket: NSObject, URLSessionWebSocketDelegate {
    private var msgSeq = 0
    private let instanceId = Int.random(in: 1000...9999)
    /// Incremented on every connect(); stale receive callbacks check this and bail.
    private var receiveGeneration = 0

    var onOpen: (() -> Void)?
    var onClose: (() -> Void)?
    var onMessage: ((String) -> Void)?

    private var session: URLSession?
    private var task: URLSessionWebSocketTask?
    private var reconnectDelay: TimeInterval = 1
    private var intentionalClose = false
    private var currentURL: URL?
    private var currentToken: String?

    private static let initialDelay: TimeInterval = 1
    private static let maxDelay: TimeInterval = 30

    func connect(url: URL, token: String) {
        wsLog.notice("[ConnectWS:\(self.instanceId)] connect() called, existing task state: \(String(describing: self.task?.state), privacy: .public)")
        intentionalClose = false
        currentURL = url
        currentToken = token

        var request = URLRequest(url: url)
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")

        let config = URLSessionConfiguration.default
        receiveGeneration += 1
        session = URLSession(configuration: config, delegate: self, delegateQueue: .main)
        task = session?.webSocketTask(with: request)
        task?.resume()
        receiveNext(generation: receiveGeneration)
    }

    func disconnect() {
        intentionalClose = true
        task?.cancel(with: .normalClosure, reason: nil)
        task = nil
        session?.invalidateAndCancel()
        session = nil
    }

    func send<T: Encodable>(_ message: T) {
        guard let data = try? JSONEncoder().encode(message),
              let string = String(data: data, encoding: .utf8) else { return }
        task?.send(.string(string)) { _ in }
    }

    // MARK: - URLSessionWebSocketDelegate

    func urlSession(
        _ session: URLSession,
        webSocketTask: URLSessionWebSocketTask,
        didOpenWithProtocol protocol: String?
    ) {
        reconnectDelay = Self.initialDelay
        onOpen?()
    }

    func urlSession(
        _ session: URLSession,
        webSocketTask: URLSessionWebSocketTask,
        didCloseWith closeCode: URLSessionWebSocketTask.CloseCode,
        reason: Data?
    ) {
        onClose?()
        if !intentionalClose { scheduleReconnect() }
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didCompleteWithError error: Error?
    ) {
        if error != nil {
            onClose?()
            if !intentionalClose { scheduleReconnect() }
        }
    }

    // MARK: - Private

    private func receiveNext(generation: Int) {
        task?.receive { [weak self] result in
            // task.receive delivers on a background thread regardless of delegateQueue,
            // so hop to main before touching any stored state.
            DispatchQueue.main.async { [weak self] in
                guard let self, self.receiveGeneration == generation else { return }
                switch result {
                case .success(let message):
                    if case .string(let text) = message {
                        self.msgSeq += 1
                        let seq = self.msgSeq
                        let prefix = text.prefix(80)
                        wsLog.notice("[ConnectWS:\(self.instanceId)] recv #\(seq): \(prefix, privacy: .public)")
                        self.onMessage?(text)
                    }
                    self.receiveNext(generation: generation)
                case .failure:
                    break
                }
            }
        }
    }

    private func scheduleReconnect() {
        guard let url = currentURL, let token = currentToken else { return }
        let delay = reconnectDelay
        reconnectDelay = min(reconnectDelay * 2, Self.maxDelay)
        DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self] in
            guard let self, !self.intentionalClose else { return }
            self.connect(url: url, token: token)
        }
    }
}
