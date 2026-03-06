import Foundation
import Testing
import GRDB
@testable import deadly

@Suite("FavoritesDAO Tests")
struct FavoritesDAOTests {

    let db: AppDatabase
    let showDAO: ShowDAO
    let dao: FavoritesDAO

    init() throws {
        db = try AppDatabase.makeEmpty()
        showDAO = ShowDAO(database: db)
        dao = FavoritesDAO(database: db)
    }

    // MARK: - Helpers

    private func insertShow(_ showId: String) throws {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        try showDAO.insert(ShowRecord(
            showId: showId,
            date: "1977-05-08",
            year: 1977,
            month: 5,
            yearMonth: "1977-05",
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
            isFavorite: false,
            favoritedAt: nil,
            coverImageUrl: nil,
            createdAt: now,
            updatedAt: now
        ))
    }

    private func makeFavoriteShow(
        showId: String,
        isPinned: Bool = false,
        addedAt: Int64 = 1000,
        preferredRecordingId: String? = nil
    ) -> FavoriteShowRecord {
        FavoriteShowRecord(
            showId: showId,
            addedToFavoritesAt: addedAt,
            isPinned: isPinned,
            notes: nil,
            preferredRecordingId: preferredRecordingId,
            downloadedRecordingId: nil,
            downloadedFormat: nil,
            customRating: nil,
            lastAccessedAt: nil,
            tags: nil
        )
    }

    // MARK: - Add / remove

    @Test("add and fetchById round-trip")
    func addAndFetchById() throws {
        try insertShow("1977-05-08")
        try dao.add(makeFavoriteShow(showId: "1977-05-08"))
        let fetched = try dao.fetchById("1977-05-08")
        #expect(fetched?.showId == "1977-05-08")
        #expect(fetched?.isPinned == false)
    }

    @Test("remove deletes library entry")
    func removeShow() throws {
        try insertShow("1977-05-08")
        try dao.add(makeFavoriteShow(showId: "1977-05-08"))
        #expect(try dao.isFavorite("1977-05-08") == true)
        try dao.remove("1977-05-08")
        #expect(try dao.isFavorite("1977-05-08") == false)
    }

    @Test("isFavorite returns false when show not added")
    func isFavoriteFalse() throws {
        let result = try dao.isFavorite("not-in-library")
        #expect(result == false)
    }

    // MARK: - fetchAll ordering

    @Test("fetchAll orders pinned first, then by addedToFavoritesAt desc")
    func fetchAllOrdering() throws {
        try insertShow("show-a")
        try insertShow("show-b")
        try insertShow("show-c")
        try dao.addAll([
            makeFavoriteShow(showId: "show-a", isPinned: false, addedAt: 1000),
            makeFavoriteShow(showId: "show-b", isPinned: true,  addedAt: 500),
            makeFavoriteShow(showId: "show-c", isPinned: false, addedAt: 2000),
        ])
        let results = try dao.fetchAll()
        #expect(results.count == 3)
        #expect(results[0].showId == "show-b")  // pinned first
        #expect(results[1].showId == "show-c")  // most recently added
        #expect(results[2].showId == "show-a")
    }

    @Test("fetchPinned returns only pinned shows")
    func fetchPinned() throws {
        try insertShow("show-a")
        try insertShow("show-b")
        try dao.addAll([
            makeFavoriteShow(showId: "show-a", isPinned: true),
            makeFavoriteShow(showId: "show-b", isPinned: false),
        ])
        let pinned = try dao.fetchPinned()
        #expect(pinned.count == 1)
        #expect(pinned[0].showId == "show-a")
    }

    // MARK: - Pin management

    @Test("updatePinStatus toggles pin")
    func updatePinStatus() throws {
        try insertShow("1977-05-08")
        try dao.add(makeFavoriteShow(showId: "1977-05-08", isPinned: false))
        try dao.updatePinStatus("1977-05-08", isPinned: true)
        #expect(try dao.fetchById("1977-05-08")?.isPinned == true)
        try dao.updatePinStatus("1977-05-08", isPinned: false)
        #expect(try dao.fetchById("1977-05-08")?.isPinned == false)
    }

    // MARK: - Recording preference (via RecordingPreferenceDAO)

    @Test("RecordingPreferenceDAO upsert and fetch round-trip")
    func recordingPreference() throws {
        try insertShow("1977-05-08")
        let prefDAO = RecordingPreferenceDAO(database: db)
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        try prefDAO.upsert(RecordingPreferenceRecord(showId: "1977-05-08", recordingId: "gd77-05-08.sbd", updatedAt: now))
        #expect(try prefDAO.fetchRecordingId("1977-05-08") == "gd77-05-08.sbd")
        try prefDAO.delete("1977-05-08")
        #expect(try prefDAO.fetchRecordingId("1977-05-08") == nil)
    }

    // MARK: - Cascade delete

    @Test("cascade: deleting parent show removes library entry")
    func cascadeDeleteViaShowDAO() throws {
        try insertShow("1977-05-08")
        try dao.add(makeFavoriteShow(showId: "1977-05-08"))
        #expect(try dao.fetchCount() == 1)
        try showDAO.deleteAll()
        #expect(try dao.fetchCount() == 0)
    }

    // MARK: - clearAll

    @Test("clearAll removes every library entry")
    func clearAll() throws {
        try insertShow("show-a")
        try insertShow("show-b")
        try dao.addAll([
            makeFavoriteShow(showId: "show-a"),
            makeFavoriteShow(showId: "show-b"),
        ])
        try dao.clearAll()
        #expect(try dao.fetchCount() == 0)
    }
}
