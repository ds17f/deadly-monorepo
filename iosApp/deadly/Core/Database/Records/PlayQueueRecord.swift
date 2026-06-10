import GRDB

/// One row per upcoming show in the persistent show queue (ADR-0010).
///
/// Ordering is by `position` ascending; the head (next show) is the lowest
/// position. Local-only — never synced, never a Favorite. The unit is always a
/// whole show; track/playlist queuing is deferred.
struct PlayQueueRecord: Codable, Sendable, Equatable, FetchableRecord, MutablePersistableRecord {
    static let databaseTableName = "play_queue"

    var id: Int64?
    var showId: String
    /// nil = resolve to the recommended recording at play time.
    var recordingId: String?
    /// 0-based ordering; head of queue = MIN(position).
    var position: Int
    /// Non-null only for a re-queued interrupted show (resume target track).
    var resumeTrackIndex: Int?
    /// Paired with `resumeTrackIndex`.
    var resumePositionMs: Int64?
    var addedAt: Int64

    mutating func didInsert(_ inserted: InsertionSuccess) {
        id = inserted.rowID
    }
}
