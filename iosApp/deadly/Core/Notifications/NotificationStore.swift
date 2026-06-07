import Foundation

/// Local cache + cursor for in-app messages, persisted as a single JSON blob in
/// UserDefaults. The mobile-faithful equivalent of the web client's
/// `localStorage` store — load all, filter in memory, tiny volume.
///
/// All seen/dismissed state lives here and is never sent to the server. Faithful
/// port of the merge/prune/mark logic in `ui/src/lib/notifications.ts`.
@MainActor
@Observable
final class NotificationStore {
    private struct Persisted: Codable {
        var cursor: Int64
        var messages: [CachedNotification]
    }

    private static let storeKey = "deadly.notifications.v1"
    /// Drop dismissed messages from the local cache after this long.
    private static let dismissedTTLms: Int64 = 90 * 24 * 60 * 60 * 1000

    private let defaults: UserDefaults

    private(set) var notifications: [CachedNotification] = []
    private(set) var cursor: Int64 = 0

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        load()
    }

    private func load() {
        guard let data = defaults.data(forKey: Self.storeKey),
              let parsed = try? JSONDecoder().decode(Persisted.self, from: data) else {
            return
        }
        cursor = parsed.cursor
        notifications = parsed.messages
    }

    private func persist() {
        let snapshot = Persisted(cursor: cursor, messages: notifications)
        if let data = try? JSONEncoder().encode(snapshot) {
            defaults.set(data, forKey: Self.storeKey)
        }
    }

    /// Merge a fetch result into the store and prune. On a cold start (no cursor
    /// yet) the batch is marked seen-not-dismissed so a fresh user isn't slammed
    /// with unread badges — the messages appear in the inbox but don't nag.
    /// Subsequent deltas arrive unseen (they raise the badge).
    func merge(_ result: NotificationFetchResult) {
        let coldStart = cursor == 0
        let now = NotificationClock.now()
        var byId = Dictionary(uniqueKeysWithValues: notifications.map { ($0.id, $0) })

        for m in result.messages {
            let existing = byId[m.id]
            byId[m.id] = CachedNotification(
                id: m.id,
                title: m.title,
                body: m.body,
                level: m.level,
                createdAt: m.createdAt,
                expiresAt: m.expiresAt,
                // Preserve local state across re-fetches; default for new arrivals.
                seenAt: existing?.seenAt ?? (coldStart ? now : nil),
                dismissedAt: existing?.dismissedAt
            )
        }

        // Prune: expired messages and long-dismissed ones.
        let pruned = byId.values.filter { m in
            let expired = m.isExpired(now: now)
            let staleDismissed = m.dismissedAt.map { now - $0 > Self.dismissedTTLms } ?? false
            return !(expired || staleDismissed)
        }

        cursor = Swift.max(cursor, result.cursor)
        notifications = pruned.sorted { $0.createdAt > $1.createdAt }
        persist()
    }

    /// Clear the unread badge: stamp every unseen message as seen.
    func markAllSeen() {
        guard notifications.contains(where: { $0.seenAt == nil }) else { return }
        let now = NotificationClock.now()
        notifications = notifications.map {
            var m = $0
            if m.seenAt == nil { m.seenAt = now }
            return m
        }
        persist()
    }

    /// Remove a message from the active queue (stays in the archive).
    func dismiss(_ id: Int64) {
        guard notifications.contains(where: { $0.id == id && $0.dismissedAt == nil }) else { return }
        let now = NotificationClock.now()
        notifications = notifications.map {
            var m = $0
            if m.id == id && m.dismissedAt == nil { m.dismissedAt = now }
            return m
        }
        persist()
    }
}
