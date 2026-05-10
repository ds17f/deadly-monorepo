import Foundation
#if canImport(UIKit)
import UIKit
#endif

/// Fire-and-forget anonymous analytics client.
/// Buffers events in memory and flushes to the server periodically.
final class AnalyticsService: @unchecked Sendable {
    private let appPreferences: AppPreferences
    private let apiKey: String
    private let platform = "ios"
    private let sessionId = UUID().uuidString
    private let appVersion: String

    private let buffer = AnalyticsBuffer()
    private let flushInterval: TimeInterval = 30
    private let maxBufferSize = 50

    /// Computed each flush so changes to the dev custom-server setting take
    /// effect immediately without needing an app restart.
    private var baseURL: String { appPreferences.apiBaseUrl }

    private let flushQueue = DispatchQueue(label: "deadly.analytics.flush", qos: .utility)
    private var flushTimer: DispatchSourceTimer?

    init(appPreferences: AppPreferences, apiKey: String) {
        self.appPreferences = appPreferences
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

        guard let request = buildPostRequest(for: events) else { return }

        // Fire-and-forget — silently discard failures
        URLSession.shared.dataTask(with: request).resume()
    }

    /// Force-flushes the buffer and reports success/failure via `completion`.
    /// On failure, events are restored to the buffer so they can be retried.
    /// Intended for the developer "Flush Analytics" tool.
    func flushNow(completion: @escaping (Bool, Int, String?) -> Void) {
        let events = buffer.drain()
        guard !events.isEmpty else {
            completion(true, 0, nil)
            return
        }

        guard let request = buildPostRequest(for: events) else {
            buffer.prepend(events)
            completion(false, events.count, "Invalid analytics URL")
            return
        }

        URLSession.shared.dataTask(with: request) { [weak self] _, response, error in
            if let error = error {
                self?.buffer.prepend(events)
                completion(false, events.count, error.localizedDescription)
                return
            }
            if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
                self?.buffer.prepend(events)
                completion(false, events.count, "HTTP \(http.statusCode)")
                return
            }
            completion(true, events.count, nil)
        }.resume()
    }

    private func buildPostRequest(for events: [AnalyticsEvent]) -> URLRequest? {
        guard let url = URL(string: "\(baseURL)/api/analytics") else { return nil }

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
        return request
    }

    // MARK: - Private

    private func startFlushTimer() {
        let timer = DispatchSource.makeTimerSource(queue: flushQueue)
        timer.schedule(deadline: .now() + flushInterval, repeating: flushInterval)
        timer.setEventHandler { [weak self] in
            self?.flush()
        }
        timer.resume()
        flushTimer = timer
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

    func prepend(_ restored: [AnalyticsEvent]) {
        lock.lock()
        defer { lock.unlock() }
        events.insert(contentsOf: restored, at: 0)
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
