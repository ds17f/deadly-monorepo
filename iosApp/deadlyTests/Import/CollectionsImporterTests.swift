import Foundation
import Testing
@testable import deadly

@Suite("CollectionsImporter Tests")
struct CollectionsImporterTests {

    // MARK: - Helpers

    private func makeDB() throws -> AppDatabase { try AppDatabase.makeEmpty() }

    private func insertShow(_ db: AppDatabase, showId: String, date: String, venue: String = "V", year: Int? = nil) throws {
        let parts = date.split(separator: "-").map(String.init)
        let y = year ?? (Int(parts.first ?? "1970") ?? 1970)
        let m = parts.count > 1 ? (Int(parts[1]) ?? 1) : 1
        let ym = parts.count > 1 ? "\(parts[0])-\(parts[1])" : date
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let record = ShowRecord(
            showId: showId, date: date, year: y, month: m, yearMonth: ym,
            band: "GD", url: nil, venueName: venue, city: nil, state: nil,
            country: "USA", locationRaw: nil, setlistStatus: nil, setlistRaw: nil,
            songList: nil, lineupStatus: nil, lineupRaw: nil, memberList: nil,
            showSequence: 1, recordingsRaw: nil, recordingCount: 0, bestRecordingId: nil,
            averageRating: nil, totalReviews: 0, isInLibrary: false, libraryAddedAt: nil,
            coverImageUrl: nil, createdAt: now, updatedAt: now
        )
        try ShowDAO(database: db).insert(record)
    }

    private func makeImporter(_ db: AppDatabase) -> CollectionsImporter {
        CollectionsImporter(showDAO: ShowDAO(database: db))
    }

    private func makeTempDir(withCollectionsJSON json: String) throws -> URL {
        let tmpDir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: tmpDir, withIntermediateDirectories: true)
        try json.data(using: .utf8)!.write(to: tmpDir.appendingPathComponent("collections.json"))
        return tmpDir
    }

    // MARK: - Selector resolution tests

    @Test("resolveShowIds with explicit show IDs")
    func testResolveExplicitShowIds() throws {
        let db = try makeDB()
        try insertShow(db, showId: "show-1", date: "1977-05-08")
        try insertShow(db, showId: "show-2", date: "1977-05-09")
        let importer = makeImporter(db)

        let selector = makeSelectorJSON(showIds: ["show-1", "show-2"])
        let decoded = try! JSONDecoder().decode(ShowSelectorData.self, from: selector)
        let ids = importer.resolveShowIds(for: decoded)
        #expect(ids.sorted() == ["show-1", "show-2"])
    }

    @Test("resolveShowIds with specific date")
    func testResolveByDate() throws {
        let db = try makeDB()
        try insertShow(db, showId: "show-a", date: "1977-05-08")
        try insertShow(db, showId: "show-b", date: "1978-01-22")
        let importer = makeImporter(db)

        let selector = makeSelectorJSON(dates: ["1977-05-08"])
        let decoded = try! JSONDecoder().decode(ShowSelectorData.self, from: selector)
        let ids = importer.resolveShowIds(for: decoded)
        #expect(ids == ["show-a"])
    }

    @Test("resolveShowIds with date range")
    func testResolveByRange() throws {
        let db = try makeDB()
        try insertShow(db, showId: "s1", date: "1977-05-01")
        try insertShow(db, showId: "s2", date: "1977-05-08")
        try insertShow(db, showId: "s3", date: "1977-06-01")
        let importer = makeImporter(db)

        let selector = makeSelectorJSON(ranges: [["start": "1977-05-01", "end": "1977-05-31"]])
        let decoded = try! JSONDecoder().decode(ShowSelectorData.self, from: selector)
        let ids = importer.resolveShowIds(for: decoded)
        #expect(ids.sorted() == ["s1", "s2"])
    }

    @Test("resolveShowIds with venue LIKE match")
    func testResolveByVenue() throws {
        let db = try makeDB()
        try insertShow(db, showId: "v1", date: "1977-05-08", venue: "Barton Hall")
        try insertShow(db, showId: "v2", date: "1977-05-09", venue: "Other Place")
        let importer = makeImporter(db)

        let selector = makeSelectorJSON(venues: ["Barton"])
        let decoded = try! JSONDecoder().decode(ShowSelectorData.self, from: selector)
        let ids = importer.resolveShowIds(for: decoded)
        #expect(ids == ["v1"])
    }

    @Test("resolveShowIds with year")
    func testResolveByYear() throws {
        let db = try makeDB()
        try insertShow(db, showId: "y1", date: "1977-05-08", year: 1977)
        try insertShow(db, showId: "y2", date: "1977-06-01", year: 1977)
        try insertShow(db, showId: "y3", date: "1978-01-22", year: 1978)
        let importer = makeImporter(db)

        let selector = makeSelectorJSON(years: [1977])
        let decoded = try! JSONDecoder().decode(ShowSelectorData.self, from: selector)
        let ids = importer.resolveShowIds(for: decoded)
        #expect(ids.sorted() == ["y1", "y2"])
    }

    @Test("resolveShowIds with nil selector returns empty")
    func testResolveNilSelector() throws {
        let db = try makeDB()
        let importer = makeImporter(db)
        let ids = importer.resolveShowIds(for: nil)
        #expect(ids.isEmpty)
    }

    // MARK: - importCollections tests

    @Test("importCollections produces DeadCollectionRecord with correct fields")
    func testImportCollections() throws {
        let db = try makeDB()
        try insertShow(db, showId: "s1", date: "1977-05-08")

        let json = """
        {"collections": [{"id": "barton-hall", "name": "Barton Hall", "description": "Famous show",
         "tags": ["classic", "1977"],
         "show_selector": {"show_ids": ["s1"]}}]}
        """
        let tmpDir = try makeTempDir(withCollectionsJSON: json)
        defer { try? FileManager.default.removeItem(at: tmpDir) }

        let importer = makeImporter(db)
        let records = importer.importCollections(from: tmpDir)

        #expect(records.count == 1)
        let rec = records[0]
        #expect(rec.id == "barton-hall")
        #expect(rec.name == "Barton Hall")
        #expect(rec.description == "Famous show")
        #expect(rec.totalShows == 1)
        #expect(rec.primaryTag == "classic")
        #expect(rec.showIdsJson.contains("s1"))
        #expect(rec.tagsJson.contains("classic"))
    }

    @Test("importCollections returns empty when file absent")
    func testImportCollectionsMissingFile() throws {
        let db = try makeDB()
        let tmpDir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: tmpDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tmpDir) }

        let importer = makeImporter(db)
        let records = importer.importCollections(from: tmpDir)
        #expect(records.isEmpty)
    }

    // MARK: - Selector JSON helpers

    private func makeSelectorJSON(
        showIds: [String] = [],
        dates: [String] = [],
        ranges: [[String: String]] = [],
        venues: [String] = [],
        years: [Int] = []
    ) -> Data {
        var dict: [String: Any] = [:]
        if !showIds.isEmpty { dict["show_ids"] = showIds }
        if !dates.isEmpty { dict["dates"] = dates }
        if !ranges.isEmpty { dict["ranges"] = ranges }
        if !venues.isEmpty { dict["venues"] = venues }
        if !years.isEmpty { dict["years"] = years }
        return try! JSONSerialization.data(withJSONObject: dict)
    }
}
