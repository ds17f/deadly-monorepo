import GRDB

/// Flat DB record for the `dead_collections` table.
/// Tags and show IDs are stored as JSON strings (matching Android).
struct DeadCollectionRecord: Codable, Sendable, Equatable, FetchableRecord, MutablePersistableRecord {
    static let databaseTableName = "dead_collections"

    var id: String
    var name: String
    var description: String
    var tagsJson: String
    var showIdsJson: String
    var totalShows: Int
    var primaryTag: String?
    var createdAt: Int64
    var updatedAt: Int64
}
