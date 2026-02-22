import GRDB

/// Flat DB record for the `data_version` table.
/// Singleton: always id = 1. Stores metadata about the imported show data package.
struct DataVersionRecord: Codable, Sendable, Equatable, FetchableRecord, MutablePersistableRecord {
    static let databaseTableName = "data_version"

    var id: Int64 = 1
    var dataVersion: String
    var packageName: String
    var versionType: String
    var description: String?
    var importedAt: Int64
    var gitCommit: String?
    var gitTag: String?
    var buildTimestamp: String?
    var totalShows: Int
    var totalVenues: Int
    var totalFiles: Int
    var totalSizeBytes: Int64
}
