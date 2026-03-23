import Foundation
#if canImport(UIKit)
import UIKit
#endif

/// Fire-and-forget anonymous analytics client.
/// Buffers events in memory and flushes to the server periodically.
final class AnalyticsService: Sendable {
    private let appPreferences: AppPreferences
    private let baseURL: String
    private let apiKey: String
    private let platform = "ios"
    private let sessionId = UUID().uuidString
    private let appVersion: String

    private let buffer = AnalyticsBuffer()
    private let flushInterval: TimeInterval = 30
    private let maxBufferSize = 50

    init(appPreferences: AppPreferences, apiKey: String) {
        self.appPreferences = appPreferences
        self.baseURL = appPreferences.apiBaseUrl
        self.apiKey = apiKey
        self.appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown"

        startFlushTimer()

        #if canImport(UIKit)
        NotificationCenter.default.addObserver(
            forName: UIApplication.willResignActiveNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.flush()
        }
        #endif
    }

    // MARK: - Public API

    func track(_ event: String, props: [String: Any] = [:]) {
        guard appPreferences.analyticsEnabled else { return }

        let entry = AnalyticsEvent(
            event: event,
            ts: Int(Date().timeIntervalSince1970 * 1000),
            iid: appPreferences.installId,
            sid: sessionId,
            platform: platform,
            app_version: appVersion,
            props: props.isEmpty ? nil : props
        )

        buffer.append(entry)

        if buffer.count >= maxBufferSize {
            flush()
        }
    }

    func flush() {
        let events = buffer.drain()
        guard !events.isEmpty else { return }

        guard let url = URL(string: "\(baseURL)/api/analytics") else { return }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(apiKey, forHTTPHeaderField: "X-Analytics-Key")

        let payload: [[String: Any]] = events.map { event in
            var dict: [String: Any] = [
                "event": event.event,
                "ts": event.ts,
                "iid": event.iid,
                "sid": event.sid,
                "platform": event.platform,
                "app_version": event.app_version,
            ]
            if let props = event.props {
                dict["props"] = props
            }
            return dict
        }

        let body: [String: Any] = ["events": payload]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        // Fire-and-forget — silently discard failures
        URLSession.shared.dataTask(with: request).resume()
    }

    // MARK: - Private

    private func startFlushTimer() {
        let interval = flushInterval
        Task.detached { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(interval))
                self?.flush()
            }
        }
    }
}

// MARK: - Thread-safe buffer

private final class AnalyticsBuffer: @unchecked Sendable {
    private let lock = NSLock()
    private var events: [AnalyticsEvent] = []

    var count: Int {
        lock.lock()
        defer { lock.unlock() }
        return events.count
    }

    func append(_ event: AnalyticsEvent) {
        lock.lock()
        defer { lock.unlock() }
        events.append(event)
    }

    func drain() -> [AnalyticsEvent] {
        lock.lock()
        defer { lock.unlock() }
        let drained = events
        events.removeAll()
        return drained
    }
}

// MARK: - Event model

private struct AnalyticsEvent {
    let event: String
    let ts: Int
    let iid: String
    let sid: String
    let platform: String
    let app_version: String
    let props: [String: Any]?
}
