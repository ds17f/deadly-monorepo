import GRDB

/// Flat DB record for the `shows` table. Separate from the rich `Show` domain model.
/// Column names are camelCase, matching Android's Room defaults (no @ColumnInfo on ShowEntity).
struct ShowRecord: Codable, Sendable, Equatable, FetchableRecord, MutablePersistableRecord {
    static let databaseTableName = "shows"

    var showId: String
    var date: String
    var year: Int
    var month: Int
    var yearMonth: String
    var band: String
    var url: String?
    var venueName: String
    var city: String?
    var state: String?
    var country: String
    var locationRaw: String?
    var setlistStatus: String?
    var setlistRaw: String?
    var songList: String?
    var lineupStatus: String?
    var lineupRaw: String?
    var memberList: String?
    var showSequence: Int
    var recordingsRaw: String?
    var recordingCount: Int
    var bestRecordingId: String?
    var averageRating: Double?
    var totalReviews: Int
    var isInLibrary: Bool
    var libraryAddedAt: Int64?
    var coverImageUrl: String?
    var createdAt: Int64
    var updatedAt: Int64
}
