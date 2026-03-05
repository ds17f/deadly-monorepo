import GRDB

struct ShowPlayerTagRecord: Codable, Sendable, Equatable, FetchableRecord, MutablePersistableRecord {
    static let databaseTableName = "show_player_tags"

    var id: Int64?
    var showId: String
    var playerName: String
    var instruments: String?
    var isStandout: Bool
    var notes: String?
    var createdAt: Int64

    mutating func didInsert(_ inserted: InsertionSuccess) {
        id = inserted.rowID
    }
}
