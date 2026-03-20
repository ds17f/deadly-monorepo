import Foundation

/// Low-level WebSocket wrapper around URLSessionWebSocketTask
/// with automatic exponential-backoff reconnection (1 s → 30 s).
final class ConnectWebSocket: NSObject, URLSessionWebSocketDelegate {

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
        intentionalClose = false
        currentURL = url
        currentToken = token

        var request = URLRequest(url: url)
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")

        let config = URLSessionConfiguration.default
        session = URLSession(configuration: config, delegate: self, delegateQueue: .main)
        task = session?.webSocketTask(with: request)
        task?.resume()
        receiveNext()
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

    private func receiveNext() {
        task?.receive { [weak self] result in
            guard let self else { return }
            switch result {
            case .success(let message):
                if case .string(let text) = message {
                    self.onMessage?(text)
                }
                self.receiveNext()
            case .failure:
                // Connection lost — delegate methods handle reconnection
                break
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
