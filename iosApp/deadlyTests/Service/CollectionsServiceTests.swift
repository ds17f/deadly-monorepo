import Foundation
import Testing
import GRDB
@testable import deadly

@MainActor
@Suite("CollectionsService Tests")
struct CollectionsServiceTests {

    let db: AppDatabase
    let service: CollectionsServiceImpl
    let collectionsDAO: CollectionsDAO

    init() throws {
        db = try AppDatabase.makeEmpty()
        let showDAO = ShowDAO(database: db)
        let recordingDAO = RecordingDAO(database: db)
        let repo = GRDBShowRepository(showDAO: showDAO, recordingDAO: recordingDAO, appPreferences: AppPreferences())
        collectionsDAO = CollectionsDAO(database: db)
        service = CollectionsServiceImpl(
            collectionsDAO: collectionsDAO,
            showRepository: repo
        )
    }

    // MARK: - Fixture helpers

    private func insertShow(showId: String, date: String, year: Int, month: Int, venueName: String = "Test Venue") throws {
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
            averageRating: nil,
            totalReviews: 0,
            isInLibrary: false,
            libraryAddedAt: nil,
            coverImageUrl: nil,
            createdAt: now,
            updatedAt: now
        ))
    }

    private func insertCollection(
        id: String,
        name: String,
        description: String = "",
        tags: [String] = [],
        showIds: [String] = []
    ) throws {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let tagsJson = String(data: try JSONEncoder().encode(tags), encoding: .utf8) ?? "[]"
        let showIdsJson = String(data: try JSONEncoder().encode(showIds), encoding: .utf8) ?? "[]"
        try collectionsDAO.insert(DeadCollectionRecord(
            id: id,
            name: name,
            description: description,
            tagsJson: tagsJson,
            showIdsJson: showIdsJson,
            totalShows: showIds.count,
            primaryTag: tags.first,
            createdAt: now,
            updatedAt: now
        ))
    }

    // MARK: - Tests

    @Test("loadAll populates collections list")
    func loadAllPopulates() throws {
        try insertCollection(id: "c1", name: "Dick's Picks", tags: ["official", "dicks-picks"], showIds: ["1977-05-08"])
        try insertCollection(id: "c2", name: "Europe '72", tags: ["era", "70s"], showIds: ["1972-08-27"])

        service.loadAll()

        #expect(service.collections.count == 2)
        #expect(service.collections[0].id == "c1")
        #expect(service.collections[0].name == "Dick's Picks")
        #expect(service.collections[0].totalShows == 1)
        #expect(service.collections[1].id == "c2")
    }

    @Test("loadAll extracts deduplicated sorted tags")
    func loadAllExtractsTags() throws {
        try insertCollection(id: "c1", name: "A", tags: ["official", "era"])
        try insertCollection(id: "c2", name: "B", tags: ["era", "70s"])

        service.loadAll()

        #expect(service.allTags == ["70s", "era", "official"])
    }

    @Test("filterByTag returns only matching collections")
    func filterByTagFilters() throws {
        try insertCollection(id: "c1", name: "Dick's Picks", tags: ["official"])
        try insertCollection(id: "c2", name: "Europe '72", tags: ["era"])

        service.filterByTag("official")

        #expect(service.collections.count == 1)
        #expect(service.collections[0].id == "c1")
    }

    @Test("search finds collections by name")
    func searchByName() throws {
        try insertCollection(id: "c1", name: "Dick's Picks")
        try insertCollection(id: "c2", name: "Europe '72")

        service.search("Dick")

        #expect(service.collections.count == 1)
        #expect(service.collections[0].id == "c1")
    }

    @Test("loadCollection resolves shows from showIdsJson")
    func loadCollectionResolvesShows() throws {
        try insertShow(showId: "1977-05-08", date: "1977-05-08", year: 1977, month: 5, venueName: "Barton Hall")
        try insertShow(showId: "1972-08-27", date: "1972-08-27", year: 1972, month: 8, venueName: "Veneta")
        try insertCollection(
            id: "c1",
            name: "Best Of",
            description: "Top shows",
            tags: ["curated"],
            showIds: ["1977-05-08", "1972-08-27"]
        )

        service.loadCollection(id: "c1")

        let collection = service.selectedCollection
        #expect(collection != nil)
        #expect(collection?.id == "c1")
        #expect(collection?.name == "Best Of")
        #expect(collection?.description == "Top shows")
        #expect(collection?.tags == ["curated"])
        #expect(collection?.shows.count == 2)
    }

    @Test("loadCollection with unknown ID sets selectedCollection to nil")
    func loadCollectionUnknownId() throws {
        service.loadCollection(id: "nonexistent")

        #expect(service.selectedCollection == nil)
    }
}
