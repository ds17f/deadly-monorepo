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
        /// refId is the local favorite_songs row id as a string (Int64). The
        /// server identifies songs by natural key (showId + trackTitle), which
        /// the flusher reads off the row at push time.
        static let favoriteSong = "favorite_song"
        /// refId is the showId. Announces a play; the server stamps the time.
        static let recent = "recent"
        /// refId is the showId. The flusher reads the review row + its player
        /// tags at push time; a tombstoned row becomes a DELETE.
        static let review = "review"
        /// refId is the showId. The flusher reads the recording_preferences
        /// row at push time; an absent row becomes a DELETE.
        static let recordingPref = "recording_pref"
    }

    mutating func didInsert(_ inserted: InsertionSuccess) {
        id = inserted.rowID
    }
}
