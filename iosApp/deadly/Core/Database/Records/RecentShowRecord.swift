import GRDB

/// Flat DB record for the `recent_shows` table.
/// One row per show (UPSERT pattern): lastPlayedTimestamp updated each play.
struct RecentShowRecord: Codable, Sendable, Equatable, FetchableRecord, MutablePersistableRecord {
    static let databaseTableName = "recent_shows"

    var showId: String
    var lastPlayedTimestamp: Int64
    var firstPlayedTimestamp: Int64
    var totalPlayCount: Int
}
