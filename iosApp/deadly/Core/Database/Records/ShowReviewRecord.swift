import GRDB

struct ShowReviewRecord: Codable, Sendable, Equatable, FetchableRecord, MutablePersistableRecord {
    static let databaseTableName = "show_reviews"

    var showId: String
    var notes: String?
    var customRating: Double?
    var recordingQuality: Int?
    var playingQuality: Int?
    var reviewedRecordingId: String?
    var createdAt: Int64
    var updatedAt: Int64
    // Tombstone marker. Set when a review is deleted so the deletion can sync;
    // UI reads filter these out. The show_reviews.deletedAt column already
    // exists (added in the v15 sync-columns migration).
    var deletedAt: Int64?
}
