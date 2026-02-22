import Testing
import GRDB
@testable import deadly

@Suite("FTS4 Show Search Tests")
struct ShowSearchTests {

    let db: AppDatabase

    init() throws {
        db = try AppDatabase.makeEmpty()
    }

    // MARK: - Helpers

    private func insertSearchRecord(showId: String, searchText: String) throws {
        try db.write { database in
            var record = ShowSearchRecord(rowid: nil, showId: showId, searchText: searchText)
            try record.insert(database)
        }
    }

    private func search(_ query: String) throws -> [ShowSearchRecord] {
        try db.read { database in
            try ShowSearchRecord.fetchAll(
                database,
                sql: "SELECT rowid, * FROM show_search WHERE show_search MATCH ?",
                arguments: [query]
            )
        }
    }

    // MARK: - Basic FTS4

    @Test("FTS4 MATCH returns matching records")
    func fts4BasicMatch() throws {
        try insertSearchRecord(showId: "1977-05-08", searchText: "Barton Hall Ithaca NY 1977-05-08")
        try insertSearchRecord(showId: "1972-08-27", searchText: "Old Renaissance Faire Grounds Veneta OR 1972-08-27")

        let results = try search("Barton")
        #expect(results.count == 1)
        #expect(results[0].showId == "1977-05-08")
    }

    @Test("FTS4 MATCH is case-insensitive")
    func fts4CaseInsensitive() throws {
        try insertSearchRecord(showId: "1977-05-08", searchText: "Barton Hall Ithaca NY")

        let lower = try search("barton")
        let upper = try search("BARTON")
        #expect(lower.count == 1)
        #expect(upper.count == 1)
    }

    @Test("FTS4 MATCH returns empty for non-matching query")
    func fts4NoMatch() throws {
        try insertSearchRecord(showId: "1977-05-08", searchText: "Barton Hall Ithaca NY")

        let results = try search("Sacramento")
        #expect(results.isEmpty)
    }

    @Test("FTS4 MATCH returns multiple results")
    func fts4MultipleResults() throws {
        try insertSearchRecord(showId: "1977-05-08", searchText: "Barton Hall Ithaca NY 1977")
        try insertSearchRecord(showId: "1977-01-17", searchText: "Paramount Theatre Oakland CA 1977")
        try insertSearchRecord(showId: "1972-08-27", searchText: "Old Renaissance Faire Grounds Veneta OR 1972")

        let results = try search("1977")
        #expect(results.count == 2)
    }

    // MARK: - tokenchars=-.  verification

    @Test("tokenchars=-. preserves hyphenated dates as single tokens")
    func tokencharsDashPreserved() throws {
        // "5-8-77" should be a single token when tokenchars includes "-"
        try insertSearchRecord(showId: "1977-05-08", searchText: "5-8-77 Barton Hall Ithaca NY")

        // With tokenchars=-, "5-8-77" is one token — MATCH must find it
        let results = try search("5-8-77")
        #expect(results.count == 1, "tokenchars=- should keep 5-8-77 as a single searchable token")
    }

    @Test("tokenchars=-. preserves dotted dates as single tokens")
    func tokencharsDotPreserved() throws {
        try insertSearchRecord(showId: "1977-05-08", searchText: "5.8.77 Barton Hall Ithaca NY")

        let results = try search("5.8.77")
        #expect(results.count == 1, "tokenchars=. should keep 5.8.77 as a single searchable token")
    }

    @Test("Full ISO date stored as token is searchable")
    func isoDateSearchable() throws {
        try insertSearchRecord(showId: "1977-05-08", searchText: "1977-05-08 Barton Hall Ithaca NY")

        let results = try search("1977-05-08")
        #expect(results.count == 1)
    }

    // MARK: - rowid capture

    @Test("rowid is captured after insert")
    func rowidCaptured() throws {
        try db.write { database in
            var record = ShowSearchRecord(rowid: nil, showId: "rowid-test", searchText: "Barton Hall")
            #expect(record.rowid == nil)
            try record.insert(database)
            #expect(record.rowid != nil)
        }
    }
}
