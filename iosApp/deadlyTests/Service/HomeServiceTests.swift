import Foundation
import Testing
import GRDB
import SwiftAudioStreamEx
@testable import deadly

/// Stub RecentShowsService that delegates to DAO for HomeService tests
@MainActor
final class TestRecentShowsService: RecentShowsService {
    private let recentShowDAO: RecentShowDAO
    private let showRepository: any ShowRepository

    var recentShows: [Show] = []

    var recentShowsStream: AsyncStream<[Show]> {
        AsyncStream { continuation in
            continuation.yield(self.recentShows)
        }
    }

    init(recentShowDAO: RecentShowDAO, showRepository: any ShowRepository) {
        self.recentShowDAO = recentShowDAO
        self.showRepository = showRepository
    }

    func recordShowPlay(showId: String) {
        let timestamp = Int64(Date().timeIntervalSince1970 * 1000)
        try? recentShowDAO.upsert(showId: showId, timestamp: timestamp)
    }

    func getRecentShows(limit: Int) async -> [Show] {
        do {
            let records = try recentShowDAO.fetchRecent(limit: limit)
            let showIds = records.map(\.showId)
            let shows = try showRepository.getShowsByIds(showIds)
            let showsById = Dictionary(shows.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
            return showIds.compactMap { showsById[$0] }
        } catch {
            return []
        }
    }

    func isShowInRecent(showId: String) async -> Bool {
        return (try? recentShowDAO.fetchById(showId)) != nil
    }

    func removeShow(showId: String) async {}
    func clearRecentShows() async {}
    func startObservingPlayback() {}
    func stopObservingPlayback() {}
}

@MainActor
@Suite("HomeService Tests")
struct HomeServiceTests {

    let db: AppDatabase
    let service: HomeServiceImpl

    init() throws {
        db = try AppDatabase.makeEmpty()
        let showDAO = ShowDAO(database: db)
        let recordingDAO = RecordingDAO(database: db)
        let repo = GRDBShowRepository(showDAO: showDAO, recordingDAO: recordingDAO, appPreferences: AppPreferences())
        let recentShowsService = TestRecentShowsService(
            recentShowDAO: RecentShowDAO(database: db),
            showRepository: repo
        )
        service = HomeServiceImpl(
            showRepository: repo,
            collectionsDAO: CollectionsDAO(database: db),
            recentShowsService: recentShowsService
        )
    }

    // MARK: - Fixture helpers

    private func insertShow(
        showId: String,
        date: String,
        year: Int,
        month: Int,
        day: Int,
        venueName: String = "Test Venue",
        city: String? = nil,
        state: String? = nil
    ) throws {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let yearMonth = String(date.prefix(7))
        try ShowDAO(database: db).insert(ShowRecord(
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
            recordingCount: 1,
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

    private func insertCollection(id: String, name: String, totalShows: Int, tags: [String] = []) throws {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let tagsJson = (try? String(data: JSONEncoder().encode(tags), encoding: .utf8)) ?? "[]"
        try CollectionsDAO(database: db).insert(DeadCollectionRecord(
            id: id,
            name: name,
            description: "A collection",
            tagsJson: tagsJson,
            showIdsJson: "[]",
            totalShows: totalShows,
            primaryTag: tags.first,
            createdAt: now,
            updatedAt: now
        ))
    }

    private func insertRecentShow(showId: String, timestamp: Int64) throws {
        try RecentShowDAO(database: db).upsert(showId: showId, timestamp: timestamp)
    }

    private func insertFixtureShows() throws {
        try insertShow(showId: "1977-05-08", date: "1977-05-08", year: 1977, month: 5, day: 8,
                       venueName: "Barton Hall", city: "Ithaca", state: "NY")
        try insertShow(showId: "1980-05-08", date: "1980-05-08", year: 1980, month: 5, day: 8,
                       venueName: "Red Rocks Amphitheatre", city: "Morrison", state: "CO")
        try insertShow(showId: "1972-08-27", date: "1972-08-27", year: 1972, month: 8, day: 27,
                       venueName: "Old Renaissance Faire Grounds", city: "Veneta", state: "OR")
    }

    // MARK: - Tests

    @Test("empty database returns empty HomeContent")
    func emptyDatabase() async throws {
        await service.refresh()
        #expect(service.content.todayInHistory.isEmpty)
        #expect(service.content.featuredCollections.isEmpty)
        #expect(service.content.recentShows.isEmpty)
    }

    @Test("refresh loads featured collections sorted by totalShows desc")
    func featuredCollectionsSortedByShowCount() async throws {
        try insertCollection(id: "c1", name: "Small Collection", totalShows: 5)
        try insertCollection(id: "c2", name: "Large Collection", totalShows: 50)
        try insertCollection(id: "c3", name: "Medium Collection", totalShows: 20)

        await service.refresh()

        let collections = service.content.featuredCollections
        #expect(collections.count == 3)
        #expect(collections[0].id == "c2")  // 50
        #expect(collections[1].id == "c3")  // 20
        #expect(collections[2].id == "c1")  // 5
    }

    @Test("refresh loads recent shows ordered by recency")
    func recentShowsOrderedByRecency() async throws {
        try insertFixtureShows()
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        try insertRecentShow(showId: "1972-08-27", timestamp: now - 3000)
        try insertRecentShow(showId: "1977-05-08", timestamp: now - 1000)
        try insertRecentShow(showId: "1980-05-08", timestamp: now - 2000)

        await service.refresh()

        let shows = service.content.recentShows
        #expect(shows.count == 3)
        #expect(shows[0].id == "1977-05-08")  // most recent
        #expect(shows[1].id == "1980-05-08")
        #expect(shows[2].id == "1972-08-27")  // oldest
    }

    @Test("refresh loads todayInHistory for current month and day")
    func todayInHistoryMatchesCurrentDate() async throws {
        let calendar = Calendar.current
        let now = Date()
        let month = calendar.component(.month, from: now)
        let day = calendar.component(.day, from: now)

        // Insert a show on today's month/day in a different year
        let dateStr = String(format: "1970-%02d-%02d", month, day)
        try insertShow(showId: dateStr, date: dateStr, year: 1970, month: month, day: day,
                       venueName: "History Venue")

        await service.refresh()

        #expect(service.content.todayInHistory.count >= 1)
        #expect(service.content.todayInHistory.contains { $0.id == dateStr })
    }

    @Test("CollectionSummary showCountText singular")
    func showCountTextSingular() {
        let summary = CollectionSummary(id: "x", name: "Test", description: "", totalShows: 1, tags: [])
        #expect(summary.showCountText == "1 show")
    }

    @Test("CollectionSummary showCountText plural")
    func showCountTextPlural() {
        let summary = CollectionSummary(id: "x", name: "Test", description: "", totalShows: 42, tags: [])
        #expect(summary.showCountText == "42 shows")
    }

    @Test("CollectionSummary formattedName returns name unchanged")
    func formattedNameReturnsName() {
        let name = "Spring 1977 Tour"
        let summary = CollectionSummary(id: "x", name: name, description: "", totalShows: 10, tags: [])
        #expect(summary.formattedName == name)
    }
}
