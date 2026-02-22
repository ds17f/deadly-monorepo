import Foundation
import Testing
import GRDB
@testable import deadly

@MainActor
@Suite("LibraryService Tests")
struct LibraryServiceTests {

    let db: AppDatabase
    let service: LibraryServiceImpl

    init() throws {
        db = try AppDatabase.makeEmpty()
        let showDAO = ShowDAO(database: db)
        let recordingDAO = RecordingDAO(database: db)
        let repo = GRDBShowRepository(showDAO: showDAO, recordingDAO: recordingDAO, appPreferences: AppPreferences())
        service = LibraryServiceImpl(
            database: db,
            libraryDAO: LibraryDAO(database: db),
            showRepository: repo
        )
    }

    // MARK: - Fixture helpers

    private func insertShow(
        showId: String,
        date: String,
        year: Int,
        month: Int,
        venueName: String = "Test Venue",
        averageRating: Double? = nil
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
            recordingCount: 1,
            bestRecordingId: nil,
            averageRating: averageRating,
            totalReviews: averageRating != nil ? 1 : 0,
            isInLibrary: false,
            libraryAddedAt: nil,
            coverImageUrl: nil,
            createdAt: now,
            updatedAt: now
        ))
    }

    private func insertFixtureShows() throws {
        try insertShow(showId: "1977-05-08", date: "1977-05-08", year: 1977, month: 5,
                       venueName: "Barton Hall", averageRating: 4.9)
        try insertShow(showId: "1972-08-27", date: "1972-08-27", year: 1972, month: 8,
                       venueName: "Veneta Fairgrounds", averageRating: 4.5)
        try insertShow(showId: "1989-10-09", date: "1989-10-09", year: 1989, month: 10,
                       venueName: "Hampton Coliseum", averageRating: 4.7)
    }

    private func libraryRecord(for showId: String) throws -> LibraryShowRecord? {
        try db.read { db in try LibraryShowRecord.fetchOne(db, key: showId) }
    }

    private func showRecord(for showId: String) throws -> ShowRecord? {
        try db.read { db in try ShowRecord.fetchOne(db, key: showId) }
    }

    // MARK: - Tests

    @Test("addToLibrary inserts LibraryShowRecord and sets isInLibrary on ShowRecord")
    func addToLibrary() throws {
        try insertShow(showId: "1977-05-08", date: "1977-05-08", year: 1977, month: 5)

        try service.addToLibrary(showId: "1977-05-08")

        let libraryRec = try libraryRecord(for: "1977-05-08")
        #expect(libraryRec != nil)

        let showRec = try showRecord(for: "1977-05-08")
        #expect(showRec?.isInLibrary == true)
        #expect(showRec?.libraryAddedAt != nil)
    }

    @Test("removeFromLibrary deletes LibraryShowRecord and clears isInLibrary on ShowRecord")
    func removeFromLibrary() throws {
        try insertShow(showId: "1977-05-08", date: "1977-05-08", year: 1977, month: 5)
        try service.addToLibrary(showId: "1977-05-08")

        try service.removeFromLibrary(showId: "1977-05-08")

        let libraryRec = try libraryRecord(for: "1977-05-08")
        #expect(libraryRec == nil)

        let showRec = try showRecord(for: "1977-05-08")
        #expect(showRec?.isInLibrary == false)
        #expect(showRec?.libraryAddedAt == nil)
    }

    @Test("isInLibrary returns true after add, false after remove")
    func isInLibraryToggle() throws {
        try insertShow(showId: "1977-05-08", date: "1977-05-08", year: 1977, month: 5)

        #expect(try service.isInLibrary(showId: "1977-05-08") == false)

        try service.addToLibrary(showId: "1977-05-08")
        #expect(try service.isInLibrary(showId: "1977-05-08") == true)

        try service.removeFromLibrary(showId: "1977-05-08")
        #expect(try service.isInLibrary(showId: "1977-05-08") == false)
    }

    @Test("refresh sortedBy dateAdded descending puts newest-added first")
    func refreshSortByDateAddedDescending() throws {
        try insertFixtureShows()

        // Add in known order with small delays to ensure different timestamps
        try service.addToLibrary(showId: "1977-05-08")
        Thread.sleep(forTimeInterval: 0.01)
        try service.addToLibrary(showId: "1972-08-27")
        Thread.sleep(forTimeInterval: 0.01)
        try service.addToLibrary(showId: "1989-10-09")

        service.refresh(sortedBy: .dateAdded, direction: .descending)

        #expect(service.shows.count == 3)
        #expect(service.shows[0].id == "1989-10-09")  // added last = newest
        #expect(service.shows[2].id == "1977-05-08")  // added first = oldest
    }

    @Test("refresh sortedBy dateOfShow ascending puts oldest show first")
    func refreshSortByDateOfShowAscending() throws {
        try insertFixtureShows()
        try service.addToLibrary(showId: "1977-05-08")
        try service.addToLibrary(showId: "1972-08-27")
        try service.addToLibrary(showId: "1989-10-09")

        service.refresh(sortedBy: .dateOfShow, direction: .ascending)

        #expect(service.shows.count == 3)
        #expect(service.shows[0].id == "1972-08-27")
        #expect(service.shows[1].id == "1977-05-08")
        #expect(service.shows[2].id == "1989-10-09")
    }

    @Test("refresh sortedBy venue ascending puts venues in alphabetical order")
    func refreshSortByVenue() throws {
        try insertFixtureShows()
        try service.addToLibrary(showId: "1977-05-08")   // Barton Hall
        try service.addToLibrary(showId: "1989-10-09")   // Hampton Coliseum
        try service.addToLibrary(showId: "1972-08-27")   // Veneta Fairgrounds

        service.refresh(sortedBy: .venue, direction: .ascending)

        #expect(service.shows.count == 3)
        #expect(service.shows[0].id == "1977-05-08")   // Barton Hall
        #expect(service.shows[1].id == "1989-10-09")   // Hampton Coliseum
        #expect(service.shows[2].id == "1972-08-27")   // Veneta Fairgrounds
    }

    @Test("refresh sortedBy rating descending puts highest rated first")
    func refreshSortByRatingDescending() throws {
        try insertFixtureShows()
        try service.addToLibrary(showId: "1977-05-08")   // 4.9
        try service.addToLibrary(showId: "1972-08-27")   // 4.5
        try service.addToLibrary(showId: "1989-10-09")   // 4.7

        service.refresh(sortedBy: .rating, direction: .descending)

        #expect(service.shows.count == 3)
        #expect(service.shows[0].id == "1977-05-08")   // 4.9
        #expect(service.shows[1].id == "1989-10-09")   // 4.7
        #expect(service.shows[2].id == "1972-08-27")   // 4.5
    }
}
