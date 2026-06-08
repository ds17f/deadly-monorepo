import Foundation

// In-app messaging — iOS consume models.
//
// The server is a dumb publisher; ALL seen/dismissed state lives on the client
// (here, persisted as a JSON blob in UserDefaults). We keep a cache of fetched
// messages plus a cursor (the last message id pulled) and fetch deltas on
// foreground. Faithful port of `ui/src/lib/notifications.ts`.
// See PLANS/in-app-messaging.md.

/// This client's platform, for targeting (decision E).
let kNotificationPlatform = "ios"

/// Wire shape from GET /api/notifications (snake_case on the wire).
struct NotificationWire: Codable {
    let id: Int64
    let title: String
    let body: String
    let level: String
    var category: String = "general"
    var minVersion: String?
    var maxVersion: String?
    var platforms: [String]?
    let createdAt: Int64
    let expiresAt: Int64?

    enum CodingKeys: String, CodingKey {
        case id, title, body, level, category, platforms
        case minVersion = "min_version"
        case maxVersion = "max_version"
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
struct CachedNotification: Codable, Identifiable, Equatable, Hashable {
    let id: Int64
    let title: String
    let body: String
    let level: String
    var category: String = "general"
    var minVersion: String?
    var maxVersion: String?
    var platforms: [String]?
    let createdAt: Int64
    let expiresAt: Int64?
    var seenAt: Int64?
    var dismissedAt: Int64?

    /// `expiresAt` is unix *seconds*; compare against a millisecond clock.
    func isExpired(now: Int64) -> Bool {
        guard let expiresAt else { return false }
        return expiresAt * 1000 < now
    }

    /// Client-side targeting (decision E): eligible only if this platform is in
    /// the message's platform list (or it has none) AND this app's version is
    /// within [minVersion, maxVersion] (nils = unbounded).
    func isEligible(appVersion: String) -> Bool {
        if let platforms, !platforms.isEmpty, !platforms.contains(kNotificationPlatform) {
            return false
        }
        if let minVersion, compareVersions(appVersion, minVersion) < 0 { return false }
        if let maxVersion, compareVersions(appVersion, maxVersion) > 0 { return false }
        return true
    }
}

/// A toast-worthy new arrival from a delta pull. `key` is the newest message id
/// — a stable token so the UI dedupes and won't re-toast on recomposition.
struct NewArrival: Equatable {
    let title: String
    let count: Int
    let key: Int64
}

extension Array where Element == CachedNotification {
    /// Active inbox: eligible, not dismissed, not expired, newest first.
    func active(appVersion: String, now: Int64 = NotificationClock.now()) -> [CachedNotification] {
        filter { $0.isEligible(appVersion: appVersion) && $0.dismissedAt == nil && !$0.isExpired(now: now) }
            .sorted { $0.createdAt > $1.createdAt }
    }

    /// Archived (dismissed) view, newest first.
    func dismissedArchive(appVersion: String) -> [CachedNotification] {
        filter { $0.isEligible(appVersion: appVersion) && $0.dismissedAt != nil }
            .sorted { $0.createdAt > $1.createdAt }
    }

    /// Unread badge count: eligible, active, not yet read (decision A — persistent).
    func unreadCount(appVersion: String, now: Int64 = NotificationClock.now()) -> Int {
        filter { $0.isEligible(appVersion: appVersion) && $0.dismissedAt == nil && $0.seenAt == nil && !$0.isExpired(now: now) }.count
    }
}

enum NotificationClock {
    /// Local timestamps are milliseconds, matching the web client.
    static func now() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
}

/// Compare two dotted version strings numerically ("2.10.0" > "2.9.5").
/// Non-numeric suffixes are ignored; missing segments count as 0. Lenient.
func compareVersions(_ a: String, _ b: String) -> Int {
    let pa = a.split(separator: ".")
    let pb = b.split(separator: ".")
    let n = Swift.max(pa.count, pb.count)
    for i in 0..<n {
        let x = i < pa.count ? Int(pa[i].prefix { $0.isNumber }) ?? 0 : 0
        let y = i < pb.count ? Int(pb[i].prefix { $0.isNumber }) ?? 0 : 0
        if x != y { return x < y ? -1 : 1 }
    }
    return 0
}
