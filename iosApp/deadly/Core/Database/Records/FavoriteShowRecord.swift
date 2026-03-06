import GRDB

/// Flat DB record for the `favorite_shows` table.
/// showId is the PK and also a FK to shows(showId) with CASCADE DELETE.
struct FavoriteShowRecord: Codable, Sendable, Equatable, FetchableRecord, MutablePersistableRecord {
    static let databaseTableName = "favorite_shows"

    var showId: String
    var addedToFavoritesAt: Int64
    var isPinned: Bool
    var notes: String?
    var preferredRecordingId: String?
    var downloadedRecordingId: String?
    var downloadedFormat: String?
    var recordingQuality: Int?
    var playingQuality: Int?
    var customRating: Double?
    var lastAccessedAt: Int64?
    var tags: String?
}
