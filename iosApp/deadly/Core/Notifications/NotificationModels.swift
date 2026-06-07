import Foundation

// In-app messaging — iOS consume models.
//
// The server is a dumb publisher; ALL seen/dismissed state lives on the client
// (here, persisted as a JSON blob in UserDefaults). We keep a cache of fetched
// messages plus a cursor (the last message id pulled) and fetch deltas on
// foreground. Faithful port of `ui/src/lib/notifications.ts`.
// See PLANS/in-app-messaging.md.

/// Wire shape from GET /api/notifications (snake_case on the wire).
struct NotificationWire: Codable {
    let id: Int64
    let title: String
    let body: String
    let level: String
    let createdAt: Int64
    let expiresAt: Int64?

    enum CodingKeys: String, CodingKey {
        case id, title, body, level
        case createdAt = "created_at"
        case expiresAt = "expires_at"
    }
}

/// Server response envelope: messages after the cursor + the new high-water id.
struct NotificationFetchResult: Codable {
    let messages: [NotificationWire]
    let cursor: Int64
}

/// A cached message plus local-only state. Never sent back to the server.
struct CachedNotification: Codable, Identifiable, Equatable {
    let id: Int64
    let title: String
    let body: String
    let level: String
    let createdAt: Int64
    let expiresAt: Int64?
    var seenAt: Int64?
    var dismissedAt: Int64?

    /// `expiresAt` is unix *seconds*; compare against a millisecond clock.
    func isExpired(now: Int64) -> Bool {
        guard let expiresAt else { return false }
        return expiresAt * 1000 < now
    }
}

extension Array where Element == CachedNotification {
    /// Active inbox: not dismissed and not expired, newest first.
    func active(now: Int64 = NotificationClock.now()) -> [CachedNotification] {
        filter { $0.dismissedAt == nil && !$0.isExpired(now: now) }
            .sorted { $0.createdAt > $1.createdAt }
    }

    /// Dismissed archive, newest first.
    func dismissedArchive() -> [CachedNotification] {
        filter { $0.dismissedAt != nil }
            .sorted { $0.createdAt > $1.createdAt }
    }

    /// Unread badge count: active and not yet seen.
    func unreadCount(now: Int64 = NotificationClock.now()) -> Int {
        filter { $0.dismissedAt == nil && $0.seenAt == nil && !$0.isExpired(now: now) }.count
    }
}

enum NotificationClock {
    /// Local timestamps are milliseconds, matching the web client.
    static func now() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
}
