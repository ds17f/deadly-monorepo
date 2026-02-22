import Foundation
import Testing
import GRDB
import SwiftAudioStreamEx
@testable import deadly

@MainActor
@Suite("RecentShowsService Tests")
struct RecentShowsServiceTests {

    let db: AppDatabase
    let recentShowDAO: RecentShowDAO
    let showRepo: GRDBShowRepository
    let streamPlayer: StreamPlayer
    let service: RecentShowsServiceImpl

    init() throws {
        db = try AppDatabase.makeEmpty()
        recentShowDAO = RecentShowDAO(database: db)
        let showDAO = ShowDAO(database: db)
        let recordingDAO = RecordingDAO(database: db)
        showRepo = GRDBShowRepository(showDAO: showDAO, recordingDAO: recordingDAO, appPreferences: AppPreferences())
        streamPlayer = StreamPlayer()
        service = RecentShowsServiceImpl(
            recentShowDAO: recentShowDAO,
            showRepository: showRepo,
            streamPlayer: streamPlayer
        )
    }

    // MARK: - Fixture helpers

    private func insertShow(_ showId: String) throws {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let yearMonth = String(showId.prefix(7))
        try ShowDAO(database: db).insert(ShowRecord(
            showId: showId,
            date: showId,
            year: 1977,
            month: 5,
            yearMonth: yearMonth,
            band: "Grateful Dead",
            url: nil,
            venueName: "Test Venue",
            city: "Test City",
            state: "NY",
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

    private func insertRecentShow(showId: String, timestamp: Int64) throws {
        try recentShowDAO.upsert(showId: showId, timestamp: timestamp)
    }

    // MARK: - Tests

    @Test("recordShowPlay inserts new show into recent_shows")
    func recordShowPlayInsertsNew() throws {
        let showId = "1977-05-08"
        try insertShow(showId)

        service.recordShowPlay(showId: showId)

        let record = try recentShowDAO.fetchById(showId)
        #expect(record != nil)
        #expect(record?.totalPlayCount == 1)
    }

    @Test("recordShowPlay increments play count on subsequent calls")
    func recordShowPlayIncrementsCount() throws {
        let showId = "1977-05-08"
        try insertShow(showId)

        service.recordShowPlay(showId: showId)
        service.recordShowPlay(showId: showId)
        service.recordShowPlay(showId: showId)

        let record = try recentShowDAO.fetchById(showId)
        #expect(record?.totalPlayCount == 3)
    }

    @Test("getRecentShows returns shows ordered by recency")
    func getRecentShowsOrderedByRecency() async throws {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        try insertShow("1972-08-27")
        try insertShow("1977-05-08")
        try insertShow("1980-05-08")
        try insertRecentShow(showId: "1972-08-27", timestamp: now - 3000)
        try insertRecentShow(showId: "1977-05-08", timestamp: now - 1000)
        try insertRecentShow(showId: "1980-05-08", timestamp: now - 2000)

        let shows = await service.getRecentShows(limit: 10)

        #expect(shows.count == 3)
        #expect(shows[0].id == "1977-05-08")  // most recent
        #expect(shows[1].id == "1980-05-08")
        #expect(shows[2].id == "1972-08-27")  // oldest
    }

    @Test("getRecentShows respects limit parameter")
    func getRecentShowsRespectsLimit() async throws {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        try insertShow("1972-08-27")
        try insertShow("1977-05-08")
        try insertShow("1980-05-08")
        try insertRecentShow(showId: "1972-08-27", timestamp: now - 3000)
        try insertRecentShow(showId: "1977-05-08", timestamp: now - 1000)
        try insertRecentShow(showId: "1980-05-08", timestamp: now - 2000)

        let shows = await service.getRecentShows(limit: 2)

        #expect(shows.count == 2)
    }

    @Test("isShowInRecent returns true for recent show")
    func isShowInRecentReturnsTrue() async throws {
        let showId = "1977-05-08"
        try insertShow(showId)
        try insertRecentShow(showId: showId, timestamp: Int64(Date().timeIntervalSince1970 * 1000))

        let result = await service.isShowInRecent(showId: showId)

        #expect(result == true)
    }

    @Test("isShowInRecent returns false for unknown show")
    func isShowInRecentReturnsFalse() async throws {
        let result = await service.isShowInRecent(showId: "unknown-show")

        #expect(result == false)
    }

    @Test("removeShow removes show from recent_shows")
    func removeShowRemovesFromRecent() async throws {
        let showId = "1977-05-08"
        try insertShow(showId)
        try insertRecentShow(showId: showId, timestamp: Int64(Date().timeIntervalSince1970 * 1000))

        await service.removeShow(showId: showId)

        let record = try recentShowDAO.fetchById(showId)
        #expect(record == nil)
    }

    @Test("clearRecentShows clears all shows")
    func clearRecentShowsClearsAll() async throws {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        try insertShow("1972-08-27")
        try insertShow("1977-05-08")
        try insertRecentShow(showId: "1972-08-27", timestamp: now)
        try insertRecentShow(showId: "1977-05-08", timestamp: now)

        await service.clearRecentShows()

        let count = try recentShowDAO.fetchCount()
        #expect(count == 0)
    }

    @Test("recentShows property updates after recordShowPlay")
    func recentShowsPropertyUpdates() async throws {
        let showId = "1977-05-08"
        try insertShow(showId)

        service.recordShowPlay(showId: showId)

        // Give time for async refresh
        try await Task.sleep(for: .milliseconds(100))

        #expect(service.recentShows.count == 1)
        #expect(service.recentShows[0].id == showId)
    }
}
