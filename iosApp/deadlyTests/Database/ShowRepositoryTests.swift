import Foundation
import Testing
import GRDB
@testable import deadly

@Suite("ShowRepository Tests")
struct ShowRepositoryTests {

    let db: AppDatabase
    let repo: GRDBShowRepository

    init() throws {
        db = try AppDatabase.makeEmpty()
        repo = GRDBShowRepository(
            showDAO: ShowDAO(database: db),
            recordingDAO: RecordingDAO(database: db),
            appPreferences: AppPreferences()
        )
    }

    // MARK: - Fixture helpers

    private static let setlistJSON = #"[{"set_name":"Set 1","songs":[{"name":"Truckin'","segue_into_next":true},{"name":"The Other One","segue_into_next":false}]}]"#
    private static let lineupJSON = #"[{"name":"Jerry Garcia","instruments":"Guitar, Vocals"},{"name":"Phil Lesh","instruments":"Bass"}]"#
    private static let recordingIdsJSON = #"["gd77-05-08.sbd.ht","gd77-05-08.aud.ht"]"#

    private func insertShow(
        showId: String,
        date: String,
        year: Int,
        month: Int,
        yearMonth: String,
        venueName: String = "Test Venue",
        city: String? = nil,
        state: String? = nil,
        country: String = "USA",
        locationRaw: String? = nil,
        setlistStatus: String? = nil,
        setlistRaw: String? = nil,
        lineupStatus: String? = nil,
        lineupRaw: String? = nil,
        recordingsRaw: String? = nil,
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
            country: country,
            locationRaw: locationRaw,
            setlistStatus: setlistStatus,
            setlistRaw: setlistRaw,
            songList: nil,
            lineupStatus: lineupStatus,
            lineupRaw: lineupRaw,
            memberList: nil,
            showSequence: 1,
            recordingsRaw: recordingsRaw,
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

    private func insertRecording(
        identifier: String,
        showId: String,
        sourceType: String = "SBD",
        rating: Double = 4.0,
        reviewCount: Int = 10
    ) throws {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        try RecordingDAO(database: db).insert(RecordingRecord(
            identifier: identifier,
            showId: showId,
            sourceType: sourceType,
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
        ))
    }

    private func insertFixtures() throws {
        // Show 1: 1977-05-08, Barton Hall, with setlist/lineup/recordingIds
        try insertShow(
            showId: "1977-05-08",
            date: "1977-05-08",
            year: 1977,
            month: 5,
            yearMonth: "1977-05",
            venueName: "Barton Hall, Cornell University",
            city: "Ithaca",
            state: "NY",
            setlistStatus: "complete",
            setlistRaw: Self.setlistJSON,
            lineupStatus: "complete",
            lineupRaw: Self.lineupJSON,
            recordingsRaw: Self.recordingIdsJSON,
            averageRating: 4.9,
            totalReviews: 200
        )
        // Show 2: 1980-05-08, Red Rocks — same month/day as Show 1 (for "On This Day")
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
        // Show 3: 1972-08-27, Veneta OR
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
        // Recordings for Show 1
        try insertRecording(identifier: "gd77-05-08.sbd.ht", showId: "1977-05-08", sourceType: "SBD", rating: 4.9, reviewCount: 120)
        try insertRecording(identifier: "gd77-05-08.aud.ht", showId: "1977-05-08", sourceType: "AUD", rating: 4.1, reviewCount: 30)
        // Recording for Show 3
        try insertRecording(identifier: "gd72-08-27.sbd", showId: "1972-08-27", sourceType: "SBD", rating: 4.5, reviewCount: 80)
    }

    // MARK: - getShowById

    @Test("getShowById returns show when found")
    func getShowByIdFound() throws {
        try insertFixtures()
        let show = try repo.getShowById("1977-05-08")
        #expect(show != nil)
        #expect(show?.id == "1977-05-08")
        #expect(show?.date == "1977-05-08")
    }

    @Test("getShowById returns nil when not found")
    func getShowByIdMissing() throws {
        let show = try repo.getShowById("does-not-exist")
        #expect(show == nil)
    }

    // MARK: - getShowsByIds

    @Test("getShowsByIds returns only requested shows")
    func getShowsByIds() throws {
        try insertFixtures()
        let shows = try repo.getShowsByIds(["1977-05-08", "1972-08-27"])
        #expect(shows.count == 2)
        let ids = Set(shows.map(\.id))
        #expect(ids.contains("1977-05-08"))
        #expect(ids.contains("1972-08-27"))
        #expect(!ids.contains("1980-05-08"))
    }

    // MARK: - getShowCount

    @Test("getShowCount returns total number of shows")
    func getShowCount() throws {
        try insertFixtures()
        #expect(try repo.getShowCount() == 3)
    }

    // MARK: - getShowsByYear

    @Test("getShowsByYear filters by year")
    func getShowsByYear() throws {
        try insertFixtures()
        let shows = try repo.getShowsByYear(1977)
        #expect(shows.count == 1)
        #expect(shows[0].id == "1977-05-08")
    }

    // MARK: - getShowsByDate

    @Test("getShowsByDate returns exact date match")
    func getShowsByDate() throws {
        try insertFixtures()
        let shows = try repo.getShowsByDate("1972-08-27")
        #expect(shows.count == 1)
        #expect(shows[0].id == "1972-08-27")
    }

    // MARK: - getShowsForDate (On This Day)

    @Test("getShowsForDate matches cross-year anniversary")
    func getShowsForDate() throws {
        try insertFixtures()
        let shows = try repo.getShowsForDate(month: 5, day: 8)
        #expect(shows.count == 2)
        let ids = Set(shows.map(\.id))
        #expect(ids.contains("1977-05-08"))
        #expect(ids.contains("1980-05-08"))
    }

    // MARK: - getShowsByVenue

    @Test("getShowsByVenue performs LIKE search")
    func getShowsByVenue() throws {
        try insertFixtures()
        let shows = try repo.getShowsByVenue("Barton")
        #expect(shows.count == 1)
        #expect(shows[0].id == "1977-05-08")
    }

    // MARK: - getShowsByState

    @Test("getShowsByState filters by exact state")
    func getShowsByState() throws {
        try insertFixtures()
        let shows = try repo.getShowsByState("NY")
        #expect(shows.count == 1)
        #expect(shows[0].id == "1977-05-08")
    }

    // MARK: - getTopRatedShows

    @Test("getTopRatedShows returns shows ordered by rating descending")
    func getTopRatedShows() throws {
        try insertFixtures()
        let shows = try repo.getTopRatedShows(limit: 10)
        #expect(shows.count == 3)
        #expect(shows[0].id == "1977-05-08")  // 4.9
        #expect(shows[1].id == "1972-08-27")  // 4.7
        #expect(shows[2].id == "1980-05-08")  // 4.5
    }

    // MARK: - Chronological navigation

    @Test("getNextShow returns next show in chronological order")
    func getNextShow() throws {
        try insertFixtures()
        let next = try repo.getNextShow(afterDate: "1977-05-08")
        #expect(next?.id == "1980-05-08")
    }

    @Test("getPreviousShow returns previous show in chronological order")
    func getPreviousShow() throws {
        try insertFixtures()
        let prev = try repo.getPreviousShow(beforeDate: "1977-05-08")
        #expect(prev?.id == "1972-08-27")
    }

    @Test("getNextShow returns nil at the end of the timeline")
    func getNextShowAtEnd() throws {
        try insertFixtures()
        let next = try repo.getNextShow(afterDate: "1980-05-08")
        #expect(next == nil)
    }

    // MARK: - Recording queries

    @Test("getRecordingsForShow returns all recordings for a show")
    func getRecordingsForShow() throws {
        try insertFixtures()
        let recordings = try repo.getRecordingsForShow("1977-05-08")
        #expect(recordings.count == 2)
    }

    @Test("getBestRecordingForShow returns highest rated recording")
    func getBestRecordingForShow() throws {
        try insertFixtures()
        let best = try repo.getBestRecordingForShow("1977-05-08")
        #expect(best?.identifier == "gd77-05-08.sbd.ht")
        #expect(best?.rating == 4.9)
    }

    @Test("getRecordingById returns recording when found")
    func getRecordingByIdFound() throws {
        try insertFixtures()
        let rec = try repo.getRecordingById("gd72-08-27.sbd")
        #expect(rec?.identifier == "gd72-08-27.sbd")
        #expect(rec?.showId == "1972-08-27")
    }

    @Test("getRecordingById returns nil when not found")
    func getRecordingByIdMissing() throws {
        let rec = try repo.getRecordingById("nope")
        #expect(rec == nil)
    }

    // MARK: - Mapping correctness

    @Test("show mapping: venue and location built from record fields")
    func mappingVenueAndLocation() throws {
        try insertFixtures()
        let show = try repo.getShowById("1977-05-08")
        #expect(show?.venue.name == "Barton Hall, Cornell University")
        #expect(show?.venue.city == "Ithaca")
        #expect(show?.venue.state == "NY")
        #expect(show?.venue.country == "USA")
        #expect(show?.location.city == "Ithaca")
        #expect(show?.location.state == "NY")
    }

    @Test("show mapping: setlist parsed from JSON")
    func mappingSetlist() throws {
        try insertFixtures()
        let show = try repo.getShowById("1977-05-08")
        let setlist = show?.setlist
        #expect(setlist != nil)
        #expect(setlist?.status == "complete")
        #expect(setlist?.sets.count == 1)
        #expect(setlist?.sets[0].songs.count == 2)
        #expect(setlist?.sets[0].songs[0].name == "Truckin'")
        #expect(setlist?.sets[0].songs[0].hasSegue == true)
        #expect(setlist?.sets[0].songs[1].name == "The Other One")
    }

    @Test("show mapping: lineup parsed from JSON")
    func mappingLineup() throws {
        try insertFixtures()
        let show = try repo.getShowById("1977-05-08")
        let lineup = show?.lineup
        #expect(lineup != nil)
        #expect(lineup?.status == "complete")
        #expect(lineup?.members.count == 2)
        #expect(lineup?.members[0].name == "Jerry Garcia")
    }

    @Test("show mapping: recordingIds decoded from JSON")
    func mappingRecordingIds() throws {
        try insertFixtures()
        let show = try repo.getShowById("1977-05-08")
        #expect(show?.recordingIds == ["gd77-05-08.sbd.ht", "gd77-05-08.aud.ht"])
    }

    @Test("show mapping: recordingIds defaults to empty array when nil")
    func mappingRecordingIdsNil() throws {
        try insertShow(showId: "1980-05-08", date: "1980-05-08", year: 1980, month: 5, yearMonth: "1980-05")
        let show = try repo.getShowById("1980-05-08")
        #expect(show?.recordingIds == [])
    }

    @Test("recording mapping: sourceType resolved from string")
    func mappingRecordingSourceType() throws {
        try insertFixtures()
        let sbd = try repo.getRecordingById("gd77-05-08.sbd.ht")
        #expect(sbd?.sourceType == .soundboard)
        let aud = try repo.getRecordingById("gd77-05-08.aud.ht")
        #expect(aud?.sourceType == .audience)
    }
}
