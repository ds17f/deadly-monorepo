import GRDB

/// Flat DB record for the `library_shows` table.
/// showId is the PK and also a FK to shows(showId) with CASCADE DELETE.
struct LibraryShowRecord: Codable, Sendable, Equatable, FetchableRecord, MutablePersistableRecord {
    static let databaseTableName = "library_shows"

    var showId: String
    var addedToLibraryAt: Int64
    var isPinned: Bool
    var libraryNotes: String?
    var preferredRecordingId: String?
    var downloadedRecordingId: String?
    var downloadedFormat: String?
    var customRating: Double?
    var lastAccessedAt: Int64?
    var tags: String?
}
