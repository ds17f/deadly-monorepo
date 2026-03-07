import GRDB

struct FavoriteSongRecord: Codable, Sendable, Equatable, FetchableRecord, MutablePersistableRecord {
    static let databaseTableName = "favorite_songs"

    var id: Int64?
    var showId: String
    var trackTitle: String
    var trackNumber: Int?
    var recordingId: String?
    var createdAt: Int64

    mutating func didInsert(_ inserted: InsertionSuccess) {
        id = inserted.rowID
    }
}
