import Testing
import GRDB
@testable import deadly

@Suite("RecentShowDAO Tests")
struct RecentShowDAOTests {

    let db: AppDatabase
    let dao: RecentShowDAO

    init() throws {
        db = try AppDatabase.makeEmpty()
        dao = RecentShowDAO(database: db)
    }

    // MARK: - UPSERT

    @Test("upsert creates a new row on first play")
    func upsertCreatesRow() throws {
        try dao.upsert(showId: "1977-05-08", timestamp: 1000)
        let record = try dao.fetchById("1977-05-08")
        #expect(record?.showId == "1977-05-08")
        #expect(record?.lastPlayedTimestamp == 1000)
        #expect(record?.firstPlayedTimestamp == 1000)
        #expect(record?.totalPlayCount == 1)
    }

    @Test("upsert increments totalPlayCount on second play")
    func upsertIncrementsPlayCount() throws {
        try dao.upsert(showId: "1977-05-08", timestamp: 1000)
        try dao.upsert(showId: "1977-05-08", timestamp: 2000)
        let record = try dao.fetchById("1977-05-08")
        #expect(record?.totalPlayCount == 2)
        #expect(record?.lastPlayedTimestamp == 2000)
        #expect(record?.firstPlayedTimestamp == 1000)  // preserved
    }

    @Test("upsert increments across many plays")
    func upsertManyPlays() throws {
        for i in 1...5 {
            try dao.upsert(showId: "1977-05-08", timestamp: Int64(i) * 100)
        }
        let record = try dao.fetchById("1977-05-08")
        #expect(record?.totalPlayCount == 5)
        #expect(record?.lastPlayedTimestamp == 500)
        #expect(record?.firstPlayedTimestamp == 100)
    }

    @Test("upsert handles multiple distinct shows independently")
    func upsertMultipleShows() throws {
        try dao.upsert(showId: "1977-05-08", timestamp: 1000)
        try dao.upsert(showId: "1972-08-27", timestamp: 2000)
        try dao.upsert(showId: "1977-05-08", timestamp: 3000)
        #expect(try dao.fetchCount() == 2)
        let a = try dao.fetchById("1977-05-08")
        let b = try dao.fetchById("1972-08-27")
        #expect(a?.totalPlayCount == 2)
        #expect(b?.totalPlayCount == 1)
    }

    // MARK: - Fetch ordering

    @Test("fetchRecent returns shows ordered by lastPlayedTimestamp desc")
    func fetchRecentOrdering() throws {
        try dao.upsert(showId: "show-a", timestamp: 100)
        try dao.upsert(showId: "show-b", timestamp: 300)
        try dao.upsert(showId: "show-c", timestamp: 200)
        let results = try dao.fetchRecent(limit: 10)
        #expect(results.count == 3)
        #expect(results[0].showId == "show-b")
        #expect(results[1].showId == "show-c")
        #expect(results[2].showId == "show-a")
    }

    @Test("fetchRecent respects limit")
    func fetchRecentLimit() throws {
        for i in 1...5 {
            try dao.upsert(showId: "show-\(i)", timestamp: Int64(i) * 100)
        }
        let results = try dao.fetchRecent(limit: 3)
        #expect(results.count == 3)
    }

    @Test("fetchMostPlayed orders by totalPlayCount desc")
    func fetchMostPlayed() throws {
        try dao.upsert(showId: "show-a", timestamp: 100)
        try dao.upsert(showId: "show-b", timestamp: 200)
        try dao.upsert(showId: "show-b", timestamp: 300)
        try dao.upsert(showId: "show-b", timestamp: 400)
        let results = try dao.fetchMostPlayed(limit: 10)
        #expect(results[0].showId == "show-b")
        #expect(results[0].totalPlayCount == 3)
        #expect(results[1].showId == "show-a")
        #expect(results[1].totalPlayCount == 1)
    }

    // MARK: - Cleanup

    @Test("deleteOlderThan removes records before cutoff")
    func deleteOlderThan() throws {
        try dao.upsert(showId: "old-show", timestamp: 500)
        try dao.upsert(showId: "new-show", timestamp: 2000)
        let deleted = try dao.deleteOlderThan(1000)
        #expect(deleted == 1)
        #expect(try dao.fetchCount() == 1)
        #expect(try dao.fetchById("new-show") != nil)
        #expect(try dao.fetchById("old-show") == nil)
    }

    @Test("deleteOlderThan returns 0 when nothing qualifies")
    func deleteOlderThanNoMatch() throws {
        try dao.upsert(showId: "new-show", timestamp: 9999)
        let deleted = try dao.deleteOlderThan(100)
        #expect(deleted == 0)
        #expect(try dao.fetchCount() == 1)
    }

    @Test("clearAll removes every recent show")
    func clearAll() throws {
        try dao.upsert(showId: "show-a", timestamp: 100)
        try dao.upsert(showId: "show-b", timestamp: 200)
        try dao.clearAll()
        #expect(try dao.fetchCount() == 0)
    }
}
