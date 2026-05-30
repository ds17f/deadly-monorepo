import GRDB

struct FavoriteSongRecord: Codable, Sendable, Equatable, FetchableRecord, MutablePersistableRecord {
    static let databaseTableName = "favorite_songs"

    var id: Int64?
    var showId: String
    var trackTitle: String
    var trackNumber: Int?
    var recordingId: String?
    var createdAt: Int64
    /// Last-write-wins comparator for sync. Bumped on every local mutation
    /// (insert, soft-delete, soft-resurrect). Stored as ms-since-epoch like
    /// every other timestamp on this record.
    var updatedAt: Int64
    /// Tombstone marker. Non-nil means the row was soft-deleted; UI reads
    /// filter `deletedAt IS NULL`. See PLANS/mobile-server-sync.md.
    var deletedAt: Int64?

    mutating func didInsert(_ inserted: InsertionSuccess) {
        id = inserted.rowID
    }
}
