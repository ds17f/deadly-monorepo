import GRDB

/// Record for the `show_search` FTS4 virtual table.
/// rowid is auto-assigned on insert and captured via didInsert.
struct ShowSearchRecord: Codable, Sendable, Equatable, FetchableRecord, MutablePersistableRecord {
    static let databaseTableName = "show_search"

    var rowid: Int64?
    var showId: String
    var searchText: String

    mutating func didInsert(_ inserted: InsertionSuccess) {
        rowid = inserted.rowID
    }
}
