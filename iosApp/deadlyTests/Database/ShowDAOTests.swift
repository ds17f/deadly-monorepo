import Foundation
import Testing
import GRDB
@testable import deadly

@Suite("ShowDAO Tests")
struct ShowDAOTests {

    let db: AppDatabase
    let dao: ShowDAO

    init() throws {
        db = try AppDatabase.makeEmpty()
        dao = ShowDAO(database: db)
    }

    // MARK: - Helpers

    private func makeShow(
        showId: String = "1977-05-08",
        date: String = "1977-05-08",
        year: Int = 1977,
        month: Int = 5,
        yearMonth: String = "1977-05",
        venueName: String = "Barton Hall",
        city: String? = "Ithaca",
        state: String? = "NY",
        averageRating: Double? = nil
    ) -> ShowRecord {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        return ShowRecord(
            showId: showId,
            date: date,
            year: year,
            month: month,
            yearMonth: yearMonth,
            band: "Grateful Dead",
            url: nil,
            venueName: venueName,
            city: city,
            state: state,
            country: "USA",
            locationRaw: nil,
            setlistStatus: nil,
            setlistRaw: nil,
            songList: nil,
            lineupStatus: nil,
            lineupRaw: nil,
            memberList: nil,
            showSequence: 1,
            recordingsRaw: nil,
            recordingCount: 0,
            bestRecordingId: nil,
            averageRating: averageRating,
            totalReviews: 0,
            isInLibrary: false,
            libraryAddedAt: nil,
            coverImageUrl: nil,
            createdAt: now,
            updatedAt: now
        )
    }

    // MARK: - Insert / fetch round-trip

    @Test("insert and fetchById round-trip")
    func insertAndFetchById() throws {
        let show = makeShow()
        try dao.insert(show)
        let fetched = try dao.fetchById("1977-05-08")
        #expect(fetched?.showId == "1977-05-08")
        #expect(fetched?.venueName == "Barton Hall")
        #expect(fetched?.year == 1977)
    }

    @Test("fetchById returns nil for unknown id")
    func fetchByIdMissing() throws {
        let result = try dao.fetchById("does-not-exist")
        #expect(result == nil)
    }

    @Test("insertAll bulk inserts all shows")
    func insertAll() throws {
        let shows = [
            makeShow(showId: "1977-05-08", date: "1977-05-08", year: 1977, month: 5, yearMonth: "1977-05"),
            makeShow(showId: "1972-08-27", date: "1972-08-27", year: 1972, month: 8, yearMonth: "1972-08", venueName: "Old Renaissance Faire Grounds", city: "Veneta", state: "OR"),
            makeShow(showId: "1969-08-16", date: "1969-08-16", year: 1969, month: 8, yearMonth: "1969-08", venueName: "Woodstock", city: "Bethel", state: "NY"),
        ]
        try dao.insertAll(shows)
        #expect(try dao.fetchCount() == 3)
    }

    @Test("fetchByIds returns only requested shows")
    func fetchByIds() throws {
        let shows = [
            makeShow(showId: "1977-05-08", date: "1977-05-08", year: 1977, month: 5, yearMonth: "1977-05"),
            makeShow(showId: "1972-08-27", date: "1972-08-27", year: 1972, month: 8, yearMonth: "1972-08", venueName: "Veneta"),
            makeShow(showId: "1969-08-16", date: "1969-08-16", year: 1969, month: 8, yearMonth: "1969-08", venueName: "Woodstock"),
        ]
        try dao.insertAll(shows)
        let fetched = try dao.fetchByIds(["1977-05-08", "1969-08-16"])
        #expect(fetched.count == 2)
        let ids = Set(fetched.map(\.showId))
        #expect(ids.contains("1977-05-08"))
        #expect(ids.contains("1969-08-16"))
        #expect(!ids.contains("1972-08-27"))
    }

    // MARK: - Date queries

    @Test("fetchByYear returns only matching year")
    func fetchByYear() throws {
        try dao.insertAll([
            makeShow(showId: "1977-05-08", date: "1977-05-08", year: 1977, month: 5, yearMonth: "1977-05"),
            makeShow(showId: "1977-01-17", date: "1977-01-17", year: 1977, month: 1, yearMonth: "1977-01"),
            makeShow(showId: "1972-08-27", date: "1972-08-27", year: 1972, month: 8, yearMonth: "1972-08"),
        ])
        let results = try dao.fetchByYear(1977)
        #expect(results.count == 2)
        #expect(results.allSatisfy { $0.year == 1977 })
    }

    @Test("fetchByYearMonth returns only matching month")
    func fetchByYearMonth() throws {
        try dao.insertAll([
            makeShow(showId: "1977-05-08", date: "1977-05-08", year: 1977, month: 5, yearMonth: "1977-05"),
            makeShow(showId: "1977-05-22", date: "1977-05-22", year: 1977, month: 5, yearMonth: "1977-05"),
            makeShow(showId: "1977-01-17", date: "1977-01-17", year: 1977, month: 1, yearMonth: "1977-01"),
        ])
        let results = try dao.fetchByYearMonth("1977-05")
        #expect(results.count == 2)
        #expect(results.allSatisfy { $0.yearMonth == "1977-05" })
    }

    @Test("fetchByDate returns shows on exact date ordered by showSequence")
    func fetchByDate() throws {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        try db.write { db in
            var s1 = makeShow(showId: "1977-09-03-a")
            s1.date = "1977-09-03"; s1.showSequence = 1
            try s1.insert(db)
            var s2 = makeShow(showId: "1977-09-03-b")
            s2.date = "1977-09-03"; s2.showSequence = 2
            try s2.insert(db)
            var other = makeShow(showId: "1977-05-08")
            try other.insert(db)
        }
        let _ = now
        let results = try dao.fetchByDate("1977-09-03")
        #expect(results.count == 2)
        #expect(results[0].showSequence == 1)
        #expect(results[1].showSequence == 2)
    }

    @Test("fetchInDateRange returns shows within bounds inclusive")
    func fetchInDateRange() throws {
        try dao.insertAll([
            makeShow(showId: "1977-01-17", date: "1977-01-17", year: 1977, month: 1, yearMonth: "1977-01"),
            makeShow(showId: "1977-05-08", date: "1977-05-08", year: 1977, month: 5, yearMonth: "1977-05"),
            makeShow(showId: "1977-09-03", date: "1977-09-03", year: 1977, month: 9, yearMonth: "1977-09"),
            makeShow(showId: "1978-01-22", date: "1978-01-22", year: 1978, month: 1, yearMonth: "1978-01"),
        ])
        let results = try dao.fetchInDateRange(start: "1977-01-17", end: "1977-09-03")
        #expect(results.count == 3)
    }

    // MARK: - Location queries

    @Test("fetchByVenue performs LIKE search")
    func fetchByVenue() throws {
        try dao.insertAll([
            makeShow(showId: "1977-05-08", venueName: "Barton Hall, Cornell University"),
            makeShow(showId: "1972-08-27", date: "1972-08-27", year: 1972, month: 8, yearMonth: "1972-08", venueName: "Old Renaissance Faire Grounds"),
        ])
        let results = try dao.fetchByVenue("Barton")
        #expect(results.count == 1)
        #expect(results[0].showId == "1977-05-08")
    }

    @Test("fetchByState returns exact state match")
    func fetchByState() throws {
        try dao.insertAll([
            makeShow(showId: "1977-05-08", state: "NY"),
            makeShow(showId: "1972-08-27", date: "1972-08-27", year: 1972, month: 8, yearMonth: "1972-08", state: "OR"),
            makeShow(showId: "1969-08-16", date: "1969-08-16", year: 1969, month: 8, yearMonth: "1969-08", state: "NY"),
        ])
        let results = try dao.fetchByState("NY")
        #expect(results.count == 2)
        #expect(results.allSatisfy { $0.state == "NY" })
    }

    // MARK: - Chronological navigation

    @Test("fetchNext returns first show after given date")
    func fetchNext() throws {
        try dao.insertAll([
            makeShow(showId: "1977-01-17", date: "1977-01-17", year: 1977, month: 1, yearMonth: "1977-01"),
            makeShow(showId: "1977-05-08", date: "1977-05-08", year: 1977, month: 5, yearMonth: "1977-05"),
            makeShow(showId: "1977-09-03", date: "1977-09-03", year: 1977, month: 9, yearMonth: "1977-09"),
        ])
        let next = try dao.fetchNext(after: "1977-05-08")
        #expect(next?.showId == "1977-09-03")
    }

    @Test("fetchPrevious returns last show before given date")
    func fetchPrevious() throws {
        try dao.insertAll([
            makeShow(showId: "1977-01-17", date: "1977-01-17", year: 1977, month: 1, yearMonth: "1977-01"),
            makeShow(showId: "1977-05-08", date: "1977-05-08", year: 1977, month: 5, yearMonth: "1977-05"),
            makeShow(showId: "1977-09-03", date: "1977-09-03", year: 1977, month: 9, yearMonth: "1977-09"),
        ])
        let prev = try dao.fetchPrevious(before: "1977-05-08")
        #expect(prev?.showId == "1977-01-17")
    }

    @Test("fetchNext returns nil when no later show exists")
    func fetchNextAtEnd() throws {
        try dao.insert(makeShow(showId: "1977-05-08"))
        let result = try dao.fetchNext(after: "1977-05-08")
        #expect(result == nil)
    }

    @Test("fetchPrevious returns nil when no earlier show exists")
    func fetchPreviousAtStart() throws {
        try dao.insert(makeShow(showId: "1977-05-08"))
        let result = try dao.fetchPrevious(before: "1977-05-08")
        #expect(result == nil)
    }

    // MARK: - Top rated / on this day

    @Test("fetchTopRated returns shows with ratings ordered by rating desc")
    func fetchTopRated() throws {
        try dao.insertAll([
            makeShow(showId: "1977-05-08", averageRating: 4.9),
            makeShow(showId: "1972-08-27", date: "1972-08-27", year: 1972, month: 8, yearMonth: "1972-08", averageRating: 4.5),
            makeShow(showId: "no-rating", date: "1975-06-17", year: 1975, month: 6, yearMonth: "1975-06", averageRating: nil),
        ])
        let results = try dao.fetchTopRated(limit: 10)
        #expect(results.count == 2)
        #expect(results[0].showId == "1977-05-08")
        #expect(results[1].showId == "1972-08-27")
    }

    @Test("fetchOnThisDay returns anniversary shows")
    func fetchOnThisDay() throws {
        try dao.insertAll([
            makeShow(showId: "1977-05-08", date: "1977-05-08", year: 1977, month: 5, yearMonth: "1977-05"),
            makeShow(showId: "1980-05-08", date: "1980-05-08", year: 1980, month: 5, yearMonth: "1980-05"),
            makeShow(showId: "1977-05-09", date: "1977-05-09", year: 1977, month: 5, yearMonth: "1977-05"),
        ])
        let results = try dao.fetchOnThisDay(month: 5, day: 8)
        #expect(results.count == 2)
        #expect(results.allSatisfy { $0.month == 5 })
    }

    // MARK: - deleteAll

    @Test("deleteAll removes every show")
    func deleteAll() throws {
        try dao.insertAll([
            makeShow(showId: "1977-05-08"),
            makeShow(showId: "1972-08-27", date: "1972-08-27", year: 1972, month: 8, yearMonth: "1972-08"),
        ])
        try dao.deleteAll()
        #expect(try dao.fetchCount() == 0)
    }
}
