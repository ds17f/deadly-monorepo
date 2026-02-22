import Foundation
import Testing
import GRDB
@testable import deadly

@Suite("RecordingDAO Tests")
struct RecordingDAOTests {

    let db: AppDatabase
    let showDAO: ShowDAO
    let dao: RecordingDAO

    init() throws {
        db = try AppDatabase.makeEmpty()
        showDAO = ShowDAO(database: db)
        dao = RecordingDAO(database: db)
    }

    // MARK: - Helpers

    private func insertShow(_ showId: String = "1977-05-08", date: String = "1977-05-08", year: Int = 1977, month: Int = 5, yearMonth: String = "1977-05") throws {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        try showDAO.insert(ShowRecord(
            showId: showId,
            date: date,
            year: year,
            month: month,
            yearMonth: yearMonth,
            band: "Grateful Dead",
            url: nil,
            venueName: "Test Venue",
            city: nil,
            state: nil,
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
            averageRating: nil,
            totalReviews: 0,
            isInLibrary: false,
            libraryAddedAt: nil,
            coverImageUrl: nil,
            createdAt: now,
            updatedAt: now
        ))
    }

    private func makeRecording(
        identifier: String,
        showId: String = "1977-05-08",
        rating: Double = 4.0,
        reviewCount: Int = 5
    ) -> RecordingRecord {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        return RecordingRecord(
            identifier: identifier,
            showId: showId,
            sourceType: "SBD",
            rating: rating,
            rawRating: rating,
            reviewCount: reviewCount,
            confidence: 0.9,
            highRatings: reviewCount,
            lowRatings: 0,
            taper: nil,
            source: nil,
            lineage: nil,
            sourceTypeString: nil,
            collectionTimestamp: now
        )
    }

    // MARK: - Insert / fetch round-trip

    @Test("insert and fetchById round-trip")
    func insertAndFetchById() throws {
        try insertShow()
        let rec = makeRecording(identifier: "gd77-05-08.sbd")
        try dao.insert(rec)
        let fetched = try dao.fetchById("gd77-05-08.sbd")
        #expect(fetched?.identifier == "gd77-05-08.sbd")
        #expect(fetched?.showId == "1977-05-08")
        #expect(fetched?.rating == 4.0)
    }

    @Test("fetchById returns nil for unknown identifier")
    func fetchByIdMissing() throws {
        let result = try dao.fetchById("does-not-exist")
        #expect(result == nil)
    }

    // MARK: - Per-show queries

    @Test("fetchForShow returns recordings ordered by rating desc")
    func fetchForShowOrdering() throws {
        try insertShow()
        try dao.insertAll([
            makeRecording(identifier: "rec-low", rating: 3.0),
            makeRecording(identifier: "rec-high", rating: 4.9),
            makeRecording(identifier: "rec-mid", rating: 4.2),
        ])
        let results = try dao.fetchForShow("1977-05-08")
        #expect(results.count == 3)
        #expect(results[0].identifier == "rec-high")
        #expect(results[1].identifier == "rec-mid")
        #expect(results[2].identifier == "rec-low")
    }

    @Test("fetchBestForShow returns highest rated recording")
    func fetchBestForShow() throws {
        try insertShow()
        try dao.insertAll([
            makeRecording(identifier: "rec-low", rating: 3.0),
            makeRecording(identifier: "rec-high", rating: 4.9),
        ])
        let best = try dao.fetchBestForShow("1977-05-08")
        #expect(best?.identifier == "rec-high")
    }

    @Test("fetchBestForShow returns nil when no recordings for show")
    func fetchBestForShowEmpty() throws {
        try insertShow()
        let result = try dao.fetchBestForShow("1977-05-08")
        #expect(result == nil)
    }

    @Test("fetchCountForShow returns correct count")
    func fetchCountForShow() throws {
        try insertShow()
        try insertShow("1972-08-27", date: "1972-08-27", year: 1972, month: 8, yearMonth: "1972-08")
        try dao.insertAll([
            makeRecording(identifier: "rec-a", showId: "1977-05-08"),
            makeRecording(identifier: "rec-b", showId: "1977-05-08"),
            makeRecording(identifier: "rec-c", showId: "1972-08-27"),
        ])
        #expect(try dao.fetchCountForShow("1977-05-08") == 2)
        #expect(try dao.fetchCountForShow("1972-08-27") == 1)
    }

    // MARK: - Top rated

    @Test("fetchTopRated filters by minRating and minReviews")
    func fetchTopRated() throws {
        try insertShow()
        try dao.insertAll([
            makeRecording(identifier: "rec-a", rating: 4.9, reviewCount: 50),
            makeRecording(identifier: "rec-b", rating: 4.5, reviewCount: 10),
            makeRecording(identifier: "rec-c", rating: 4.8, reviewCount: 3),  // fails minReviews
            makeRecording(identifier: "rec-d", rating: 3.0, reviewCount: 20), // fails minRating
        ])
        let results = try dao.fetchTopRated(minRating: 4.4, minReviews: 5, limit: 10)
        #expect(results.count == 2)
        let ids = results.map(\.identifier)
        #expect(ids.contains("rec-a"))
        #expect(ids.contains("rec-b"))
    }

    @Test("fetchTopRated respects limit")
    func fetchTopRatedLimit() throws {
        try insertShow()
        try dao.insertAll([
            makeRecording(identifier: "rec-a", rating: 4.9, reviewCount: 50),
            makeRecording(identifier: "rec-b", rating: 4.8, reviewCount: 50),
            makeRecording(identifier: "rec-c", rating: 4.7, reviewCount: 50),
        ])
        let results = try dao.fetchTopRated(minRating: 0, minReviews: 0, limit: 2)
        #expect(results.count == 2)
    }

    // MARK: - Cascade delete via ShowDAO

    @Test("cascade: deleting parent show removes its recordings")
    func cascadeDeleteViaShowDAO() throws {
        try insertShow()
        try dao.insertAll([
            makeRecording(identifier: "rec-a"),
            makeRecording(identifier: "rec-b"),
        ])
        #expect(try dao.fetchCount() == 2)
        try showDAO.deleteAll()
        #expect(try dao.fetchCount() == 0)
    }

    // MARK: - deleteForShow / deleteAll

    @Test("deleteForShow removes only recordings for that show")
    func deleteForShow() throws {
        try insertShow()
        try insertShow("1972-08-27", date: "1972-08-27", year: 1972, month: 8, yearMonth: "1972-08")
        try dao.insertAll([
            makeRecording(identifier: "rec-77", showId: "1977-05-08"),
            makeRecording(identifier: "rec-72", showId: "1972-08-27"),
        ])
        try dao.deleteForShow("1977-05-08")
        #expect(try dao.fetchCount() == 1)
        #expect(try dao.fetchById("rec-72") != nil)
    }

    @Test("deleteAll removes every recording")
    func deleteAll() throws {
        try insertShow()
        try dao.insertAll([
            makeRecording(identifier: "rec-a"),
            makeRecording(identifier: "rec-b"),
        ])
        try dao.deleteAll()
        #expect(try dao.fetchCount() == 0)
    }
}
