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
}
