import GRDB

struct TrackReviewRecord: Codable, Sendable, Equatable, FetchableRecord, MutablePersistableRecord {
    static let databaseTableName = "track_reviews"

    var id: Int64?
    var showId: String
    var trackTitle: String
    var trackNumber: Int?
    var recordingId: String?
    var thumbs: Int?        // 1=up, -1=down, nil=unrated
    var starRating: Int?    // 1-5
    var notes: String?
    var createdAt: Int64
    var updatedAt: Int64

    mutating func didInsert(_ inserted: InsertionSuccess) {
        id = inserted.rowID
    }
}
