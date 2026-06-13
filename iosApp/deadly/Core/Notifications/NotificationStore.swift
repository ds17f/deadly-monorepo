import Foundation

/// Local cache + cursor for in-app messages, persisted as a single JSON blob in
/// UserDefaults. The mobile-faithful equivalent of the web client's
/// `localStorage` store — load all, filter in memory, tiny volume.
///
/// All seen/dismissed state lives here and is never sent to the server. Faithful
/// port of the merge/prune/mark logic in `ui/src/lib/notifications.ts`.
/// A local read/dismiss mutation worth pushing to the server (ADR-0015). The
/// store stays free of networking; AppContainer wires `onStateChange` to the
/// NotificationService push.
enum NotificationStateChange {
    case seen(id: Int64)       // markRead
    case dismissed(id: Int64)  // archive
    case seenAll               // markAllRead (bulk, all active)
    case dismissedAll          // archiveAll (bulk, all active)
}

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

    /// Set when a non-cold delta brings new eligible unread messages, so the UI
    /// can show a transient toast. Observed via `.onChange` (decision C).
    private(set) var lastArrival: NewArrival?

    /// Fired after a local read/dismiss mutation so the owner can eagerly push
    /// it to the server (ADR-0015). Nil until wired; the store itself never
    /// touches the network.
    var onStateChange: ((NotificationStateChange) -> Void)?

    /// This app's version (CFBundleShortVersionString), for targeting (decision E).
    let appVersion: String = (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String) ?? "0"

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

    /// Ids already counted as an inbox impression this session, to dedupe
    /// re-renders / scrolls. Not persisted — an impression is per-session.
    private var impressed: Set<Int64> = []

    /// Merge a fetch result into the store and prune. v2 (decision G): new
    /// arrivals — including the cold-start batch — start **unread**; targeting +
    /// expiry keep the backlog relevant and "Mark all read" handles volume.
    /// A non-cold delta that adds eligible unread messages sets `lastArrival`.
    ///
    /// Returns the genuinely-new, eligible messages added by this merge (for any
    /// reason, including cold start) so the caller can emit per-message
    /// `notification_received` analytics. Empty when nothing new arrived.
    @discardableResult
    func merge(
        _ result: NotificationFetchResult,
        serverState: [NotificationStateRow] = []
    ) -> [CachedNotification] {
        let coldStart = cursor == 0
        let now = NotificationClock.now()
        let knownIds = Set(notifications.map { $0.id })
        var byId = Dictionary(uniqueKeysWithValues: notifications.map { ($0.id, $0) })

        for m in result.messages {
            let existing = byId[m.id]
            byId[m.id] = CachedNotification(
                id: m.id,
                title: m.title,
                body: m.body,
                level: m.level,
                category: m.category,
                minVersion: m.minVersion,
                maxVersion: m.maxVersion,
                platforms: m.platforms,
                createdAt: m.createdAt,
                expiresAt: m.expiresAt,
                // Preserve local state across re-fetches; new arrivals start unread.
                seenAt: existing?.seenAt,
                dismissedAt: existing?.dismissedAt
            )
        }

        // ADR-0015: overlay the server's per-user read/dismiss state (unix
        // seconds → ms) onto the cache BEFORE computing unread/toast, so a
        // message handled on another device never flashes unread. Union =
        // earliest non-null per column, matching the server's MIN(COALESCE).
        for row in serverState {
            guard var m = byId[row.notificationId] else { continue }
            m.seenAt = Self.unionEarliest(m.seenAt, row.seenAt.map { $0 * 1000 })
            m.dismissedAt = Self.unionEarliest(m.dismissedAt, row.dismissedAt.map { $0 * 1000 })
            byId[row.notificationId] = m
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

        // Genuinely new, eligible messages added by this merge — the basis for
        // both the toast (delta only) and `notification_received` analytics (any).
        let fresh = result.messages
            .filter { !knownIds.contains($0.id) }
            .compactMap { byId[$0.id] }
            .filter { $0.isEligible(appVersion: appVersion) && $0.dismissedAt == nil }
            .sorted { $0.createdAt > $1.createdAt }

        // Toast signal: only on a delta (never the cold-start backlog) and never
        // a message already seen on another device (the overlay merge above).
        let toastable = fresh.filter { $0.seenAt == nil }
        if !coldStart, let newest = toastable.first {
            lastArrival = NewArrival(title: newest.title, count: toastable.count, key: newest.id)
        }

        return fresh
    }

    /// Earliest non-null of two timestamps — the union comparator (ADR-0015).
    private static func unionEarliest(_ a: Int64?, _ b: Int64?) -> Int64? {
        guard let a else { return b }
        guard let b else { return a }
        return Swift.min(a, b)
    }

    /// Local state the server is missing (or has later than ours) — the offline
    /// catch-up flush for an eager push that failed while offline. Timestamps
    /// returned in unix seconds; empty in the common already-synced case.
    func unsyncedState(against server: [NotificationStateRow]) -> [NotificationStateRow] {
        let byServer = Dictionary(uniqueKeysWithValues: server.map { ($0.notificationId, $0) })
        var out: [NotificationStateRow] = []
        for m in notifications {
            guard m.seenAt != nil || m.dismissedAt != nil else { continue }
            let s = byServer[m.id]
            let needSeen = m.seenAt != nil && (s?.seenAt == nil || s!.seenAt! * 1000 > m.seenAt!)
            let needDismissed = m.dismissedAt != nil && (s?.dismissedAt == nil || s!.dismissedAt! * 1000 > m.dismissedAt!)
            if needSeen || needDismissed {
                out.append(NotificationStateRow(
                    notificationId: m.id,
                    seenAt: needSeen ? m.seenAt! / 1000 : nil,
                    dismissedAt: needDismissed ? m.dismissedAt! / 1000 : nil
                ))
            }
        }
        return out
    }

    /// Record an inbox impression for `id`; returns true the first time per
    /// session so the caller emits exactly one `notification_impression`.
    func registerImpression(_ id: Int64) -> Bool {
        impressed.insert(id).inserted
    }

    /// Mark a single message read ("tap to read" — opening its detail).
    func markRead(_ id: Int64) {
        guard notifications.contains(where: { $0.id == id && $0.seenAt == nil }) else { return }
        let now = NotificationClock.now()
        notifications = notifications.map {
            var m = $0
            if m.id == id && m.seenAt == nil { m.seenAt = now }
            return m
        }
        persist()
        onStateChange?(.seen(id: id))
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
        onStateChange?(.seenAll)
    }

    /// Archive (remove from the active queue; stays in the archive).
    func dismiss(_ id: Int64) {
        guard notifications.contains(where: { $0.id == id && $0.dismissedAt == nil }) else { return }
        let now = NotificationClock.now()
        notifications = notifications.map {
            var m = $0
            if m.id == id && m.dismissedAt == nil { m.dismissedAt = now }
            return m
        }
        persist()
        onStateChange?(.dismissed(id: id))
    }

    /// Archive every active eligible message at once.
    func archiveAll() {
        let now = NotificationClock.now()
        var changed = false
        notifications = notifications.map {
            var m = $0
            if m.isEligible(appVersion: appVersion) && m.dismissedAt == nil {
                m.dismissedAt = now
                changed = true
            }
            return m
        }
        if changed {
            persist()
            onStateChange?(.dismissedAll)
        }
    }
}
