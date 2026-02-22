import GRDB

/// Flat DB record for the `recordings` table.
/// Columns are snake_case (matching Android's @ColumnInfo). CodingKeys map Swift camelCase → DB snake_case.
struct RecordingRecord: Codable, Sendable, Equatable, FetchableRecord, MutablePersistableRecord {
    static let databaseTableName = "recordings"

    var identifier: String
    var showId: String
    var sourceType: String?
    var rating: Double
    var rawRating: Double
    var reviewCount: Int
    var confidence: Double
    var highRatings: Int
    var lowRatings: Int
    var taper: String?
    var source: String?
    var lineage: String?
    var sourceTypeString: String?
    var collectionTimestamp: Int64

    enum CodingKeys: String, CodingKey {
        case identifier
        case showId = "show_id"
        case sourceType = "source_type"
        case rating
        case rawRating = "raw_rating"
        case reviewCount = "review_count"
        case confidence
        case highRatings = "high_ratings"
        case lowRatings = "low_ratings"
        case taper
        case source
        case lineage
        case sourceTypeString = "source_type_string"
        case collectionTimestamp = "collection_timestamp"
    }
}
