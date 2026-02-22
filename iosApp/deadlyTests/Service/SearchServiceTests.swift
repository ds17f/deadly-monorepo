import Foundation
import Testing
import GRDB
@testable import deadly

@MainActor
@Suite("SearchService Tests")
struct SearchServiceTests {

    let db: AppDatabase
    let service: SearchServiceImpl

    init() throws {
        db = try AppDatabase.makeEmpty()
        let showDAO = ShowDAO(database: db)
        let recordingDAO = RecordingDAO(database: db)
        let showSearchDAO = ShowSearchDAO(database: db)
        let prefs = AppPreferences()
        let repo = GRDBShowRepository(showDAO: showDAO, recordingDAO: recordingDAO, appPreferences: prefs)
        service = SearchServiceImpl(
            showSearchDAO: showSearchDAO,
            showDAO: showDAO,
            showRepository: repo,
            appPreferences: prefs
        )
    }

    // MARK: - Fixture helpers

    private func insertShow(
        showId: String,
        date: String,
        year: Int,
        month: Int,
        yearMonth: String,
        venueName: String = "Test Venue",
        city: String? = nil,
        state: String? = nil,
        averageRating: Double? = nil,
        totalReviews: Int = 0
    ) throws {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
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
            averageRating: averageRating,
            totalReviews: totalReviews,
            isInLibrary: false,
            libraryAddedAt: nil,
            coverImageUrl: nil,
            createdAt: now,
            updatedAt: now
        ))
    }

    private func insertFTS(showId: String, searchText: String) throws {
        try ShowSearchDAO(database: db).insert(
            ShowSearchRecord(rowid: nil, showId: showId, searchText: searchText)
        )
    }

    private func insertFixtures() throws {
        // 1977-05-08 — Barton Hall, Cornell University, Ithaca NY
        try insertShow(
            showId: "1977-05-08",
            date: "1977-05-08",
            year: 1977,
            month: 5,
            yearMonth: "1977-05",
            venueName: "Barton Hall, Cornell University",
            city: "Ithaca",
            state: "NY",
            averageRating: 4.9,
            totalReviews: 200
        )
        try insertFTS(
            showId: "1977-05-08",
            searchText: "1977-05-08 5-8-77 5.8.77 barton hall cornell university ithaca ny 1977 77"
        )

        // 1980-05-08 — Red Rocks, Morrison CO
        try insertShow(
            showId: "1980-05-08",
            date: "1980-05-08",
            year: 1980,
            month: 5,
            yearMonth: "1980-05",
            venueName: "Red Rocks Amphitheatre",
            city: "Morrison",
            state: "CO",
            averageRating: 4.5,
            totalReviews: 80
        )
        try insertFTS(
            showId: "1980-05-08",
            searchText: "1980-05-08 red rocks amphitheatre morrison co 1980"
        )

        // 1972-08-27 — Veneta OR
        try insertShow(
            showId: "1972-08-27",
            date: "1972-08-27",
            year: 1972,
            month: 8,
            yearMonth: "1972-08",
            venueName: "Old Renaissance Faire Grounds",
            city: "Veneta",
            state: "OR",
            averageRating: 4.7,
            totalReviews: 150
        )
        try insertFTS(
            showId: "1972-08-27",
            searchText: "1972-08-27 old renaissance faire grounds veneta or 1972"
        )
    }

    // MARK: - FTS search tests

    @Test("FTS search 'Cornell' returns 1977-05-08 show")
    func ftsSearchCornell() async throws {
        try insertFixtures()
        await service.search(query: "Cornell")
        #expect(service.results.count == 1)
        #expect(service.results[0].show.id == "1977-05-08")
    }

    @Test("FTS search '1977' returns 1977 show")
    func ftsSearch1977() async throws {
        try insertFixtures()
        await service.search(query: "1977")
        #expect(service.results.count == 1)
        #expect(service.results[0].show.year == 1977)
    }

    @Test("FTS search '5-8-77' returns 1977-05-08 via tokenchars hyphen")
    func ftsSearchHyphenDate() async throws {
        try insertFixtures()
        await service.search(query: "5-8-77")
        #expect(service.results.count == 1)
        #expect(service.results[0].show.id == "1977-05-08")
    }

    // MARK: - LIKE fallback (≤2 chars)

    @Test("LIKE fallback 'NY' returns show in NY state")
    func likeSearchState() async throws {
        try insertFixtures()
        await service.search(query: "NY")
        #expect(service.results.count == 1)
        #expect(service.results[0].show.id == "1977-05-08")
    }

    // MARK: - Empty / clear

    @Test("Empty query returns empty results")
    func emptyQuery() async throws {
        try insertFixtures()
        await service.search(query: "")
        #expect(service.results.isEmpty)
    }

    @Test("clearResults empties results and query")
    func clearResults() async throws {
        try insertFixtures()
        await service.search(query: "Cornell")
        #expect(!service.results.isEmpty)
        service.clearResults()
        #expect(service.results.isEmpty)
        #expect(service.query == "")
    }

    // MARK: - searchByEra

    @Test("searchByEra '70s' returns only 1970-1979 shows")
    func searchByEra70s() throws {
        try insertFixtures()
        let shows = try service.searchByEra("70s")
        // 1977 and 1972 are in range, 1980 is not
        let ids = Set(shows.map(\.id))
        #expect(ids.contains("1977-05-08"))
        #expect(ids.contains("1972-08-27"))
        #expect(!ids.contains("1980-05-08"))
    }

    @Test("searchByEra unknown decade returns empty")
    func searchByEraUnknown() throws {
        try insertFixtures()
        let shows = try service.searchByEra("50s")
        #expect(shows.isEmpty)
    }

    // MARK: - getRandomShow

    @Test("getRandomShow returns non-nil when shows exist")
    func getRandomShow() throws {
        try insertFixtures()
        let show = try service.getRandomShow()
        #expect(show != nil)
    }

    @Test("getRandomShow returns nil when no shows exist")
    func getRandomShowEmpty() throws {
        let show = try service.getRandomShow()
        #expect(show == nil)
    }

    // MARK: - getTopRatedShows

    @Test("getTopRatedShows returns shows ordered by rating descending")
    func getTopRatedShows() throws {
        try insertFixtures()
        let shows = try service.getTopRatedShows(limit: 10)
        #expect(shows.count == 3)
        #expect(shows[0].id == "1977-05-08")  // 4.9
        #expect(shows[1].id == "1972-08-27")  // 4.7
        #expect(shows[2].id == "1980-05-08")  // 4.5
    }

    // MARK: - Match type detection

    @Test("match type: 'Cornell' in venue name → .venue")
    func matchTypeVenue() async throws {
        try insertFixtures()
        await service.search(query: "Cornell")
        #expect(service.results.first?.matchType == .venue)
    }

    @Test("match type: '1977' matches year → .year")
    func matchTypeYear() async throws {
        try insertFixtures()
        await service.search(query: "1977")
        #expect(service.results.first?.matchType == .year)
    }
}
