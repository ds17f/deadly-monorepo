import GRDB

/// One row per pending server push. Unique on (kind, refId) — re-enqueue
/// of the same row collapses to a single entry. See PLANS/mobile-server-sync.md.
struct SyncOutboxRecord: Codable, Sendable, Equatable, FetchableRecord, MutablePersistableRecord {
    static let databaseTableName = "sync_outbox"

    var id: Int64?
    var kind: String
    var refId: String
    var createdAt: Int64
    var lastAttemptAt: Int64?
    var attemptCount: Int
    var lastError: String?

    enum Kind {
        static let favoriteShow = "favorite_show"
    }

    mutating func didInsert(_ inserted: InsertionSuccess) {
        id = inserted.rowID
    }
}
