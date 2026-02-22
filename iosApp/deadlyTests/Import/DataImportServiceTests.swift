import Foundation
import Testing
@testable import deadly

// MARK: - Test stubs

/// Stub that returns a canned GitHub release without making network requests.
final class StubGitHubReleasesClient: GitHubReleasesClient, @unchecked Sendable {
    let release: GitHubRelease
    let fixtureDir: URL  // directory whose contents represent the extracted ZIP

    init(release: GitHubRelease, fixtureDir: URL) {
        self.release = release
        self.fixtureDir = fixtureDir
    }

    func fetchLatestRelease() async throws -> GitHubRelease { release }

    /// Returns a placeholder temp URL; the stub extractor will supply the real content.
    func downloadZIP(from url: URL) async throws -> URL {
        // Return a temp file path — the stub extractor ignores it
        return FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".zip")
    }
}

/// Stub that serves the fixture directory instead of actually unzipping anything.
final class StubZipExtractor: ZipExtracting, @unchecked Sendable {
    let fixtureDir: URL

    init(fixtureDir: URL) {
        self.fixtureDir = fixtureDir
    }

    func extract(from zipURL: URL) throws -> URL {
        // Copy fixture to a temp dir so the service's defer-cleanup doesn't clobber the fixture
        let destDir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.copyItem(at: fixtureDir, to: destDir)
        return destDir
    }
}

// MARK: - Fixture builder

private struct Fixtures {
    let rootDir: URL

    init() throws {
        rootDir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let showsDir = rootDir.appendingPathComponent("shows")
        let recordingsDir = rootDir.appendingPathComponent("recordings")
        try FileManager.default.createDirectory(at: showsDir, withIntermediateDirectories: true)
        try FileManager.default.createDirectory(at: recordingsDir, withIntermediateDirectories: true)
    }

    func cleanup() {
        try? FileManager.default.removeItem(at: rootDir)
    }

    func writeShow(_ dict: [String: Any]) throws {
        guard let showId = dict["show_id"] as? String else { return }
        let data = try JSONSerialization.data(withJSONObject: dict)
        let file = rootDir.appendingPathComponent("shows/\(showId).json")
        try data.write(to: file)
    }

    func writeRecording(id: String, _ dict: [String: Any]) throws {
        let data = try JSONSerialization.data(withJSONObject: dict)
        let file = rootDir.appendingPathComponent("recordings/\(id).json")
        try data.write(to: file)
    }

    func writeManifest(version: String) throws {
        let dict: [String: Any] = [
            "package": ["version": version],
            "build_info": ["git_commit": "abc123", "build_timestamp": "2024-01-01"]
        ]
        let data = try JSONSerialization.data(withJSONObject: dict)
        try data.write(to: rootDir.appendingPathComponent("manifest.json"))
    }

    func writeCollections(_ collections: [[String: Any]]) throws {
        let dict: [String: Any] = ["collections": collections]
        let data = try JSONSerialization.data(withJSONObject: dict)
        try data.write(to: rootDir.appendingPathComponent("collections.json"))
    }
}

// MARK: - Tests

@Suite("DataImportService Tests")
struct DataImportServiceTests {

    private func makeRelease(zipDirURL: URL) -> GitHubRelease {
        // Build a minimal JSON that decodes to GitHubRelease
        let json = """
        {"tag_name": "v1.0.0", "assets": [{"name": "data-v1.0.0.zip", "browser_download_url": "\(zipDirURL.absoluteString)"}]}
        """
        return try! JSONDecoder().decode(GitHubRelease.self, from: json.data(using: .utf8)!)
    }

    private func makeService(fixtures: Fixtures, db: AppDatabase) -> DataImportService {
        let release = makeRelease(zipDirURL: fixtures.rootDir)
        return DataImportService(
            gitHubClient: StubGitHubReleasesClient(release: release, fixtureDir: fixtures.rootDir),
            zipExtractor: StubZipExtractor(fixtureDir: fixtures.rootDir),
            showDAO: ShowDAO(database: db),
            recordingDAO: RecordingDAO(database: db),
            collectionsDAO: CollectionsDAO(database: db),
            showSearchDAO: ShowSearchDAO(database: db),
            dataVersionDAO: DataVersionDAO(database: db),
            libraryDAO: LibraryDAO(database: db)
        )
    }

    // MARK: - Full pipeline

    @Test("Full pipeline imports shows, recordings, and sets dataVersion")
    func testFullPipeline() async throws {
        let fixtures = try Fixtures()
        defer { fixtures.cleanup() }

        // Write 2 shows
        try fixtures.writeShow([
            "show_id": "gd1977-05-08", "band": "GD", "venue": "Barton Hall",
            "date": "1977-05-08", "recordings": ["rec-sbd"], "best_recording": "rec-sbd",
            "avg_rating": 4.8, "recording_count": 1,
            "total_high_ratings": 50, "total_low_ratings": 2
        ])
        try fixtures.writeShow([
            "show_id": "gd1978-01-22", "band": "GD", "venue": "Civic Center",
            "date": "1978-01-22", "recordings": ["rec-aud"],
            "avg_rating": 3.5, "recording_count": 1,
            "total_high_ratings": 10, "total_low_ratings": 5
        ])

        // Write 2 recordings
        try fixtures.writeRecording(id: "rec-sbd", [
            "date": "1977-05-08", "venue": "Barton Hall", "location": "Ithaca, NY",
            "rating": 4.8, "review_count": 52, "source_type": "SBD"
        ])
        try fixtures.writeRecording(id: "rec-aud", [
            "date": "1978-01-22", "venue": "Civic Center", "location": "Providence, RI",
            "rating": 3.5, "review_count": 15, "source_type": "AUD"
        ])

        try fixtures.writeManifest(version: "1.0.0")

        let db = try AppDatabase.makeEmpty()
        let service = makeService(fixtures: fixtures, db: db)

        // Collect all progress events
        var events: [ImportProgress] = []
        for await progress in service.run(force: true) {
            events.append(progress)
        }

        // Verify final event is completed
        let last = events.last
        #expect(last?.phase == .completed)

        // Verify DB counts
        let showDAO = ShowDAO(database: db)
        let recordingDAO = RecordingDAO(database: db)
        let dataVersionDAO = DataVersionDAO(database: db)
        let showSearchDAO = ShowSearchDAO(database: db)

        #expect(try showDAO.fetchCount() == 2)
        #expect(try recordingDAO.fetchCount() == 2)
        #expect(try showSearchDAO.indexedCount() == 2)

        let version = try dataVersionDAO.fetch()
        #expect(version != nil)
        #expect(version?.dataVersion == "1.0.0")
        #expect(version?.totalShows == 2)
        #expect(version?.gitCommit == "abc123")
    }

    @Test("Pipeline skips import if data already exists and force = false")
    func testSkipsWhenDataPresent() async throws {
        let db = try AppDatabase.makeEmpty()
        // Pre-insert a data version record
        let dataVersionDAO = DataVersionDAO(database: db)
        try dataVersionDAO.upsert(DataVersionRecord(
            id: 1, dataVersion: "existing", packageName: "p", versionType: "r",
            description: nil, importedAt: 1000, gitCommit: nil, gitTag: nil,
            buildTimestamp: nil, totalShows: 5, totalVenues: 0, totalFiles: 0, totalSizeBytes: 0
        ))

        let fixtures = try Fixtures()
        defer { fixtures.cleanup() }
        let service = makeService(fixtures: fixtures, db: db)

        var events: [ImportProgress] = []
        for await progress in service.run(force: false) {
            events.append(progress)
        }

        let last = events.last
        #expect(last?.phase == .completed)
        #expect(last?.message.contains("skipping") == true)

        // Shows table should still be empty (import was skipped)
        #expect(try ShowDAO(database: db).fetchCount() == 0)
    }

    @Test("Library entries are preserved across re-import")
    func testLibraryPreservation() async throws {
        let fixtures = try Fixtures()
        defer { fixtures.cleanup() }

        try fixtures.writeShow([
            "show_id": "gd1977-05-08", "band": "GD", "venue": "Barton Hall",
            "date": "1977-05-08", "recordings": [], "avg_rating": 0.0,
            "recording_count": 0, "total_high_ratings": 0, "total_low_ratings": 0
        ])
        try fixtures.writeManifest(version: "1.0.0")

        let db = try AppDatabase.makeEmpty()

        // Pre-populate: run an initial import
        let service1 = makeService(fixtures: fixtures, db: db)
        for await _ in service1.run(force: true) {}

        // Add a library entry
        let libraryDAO = LibraryDAO(database: db)
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        try libraryDAO.add(LibraryShowRecord(
            showId: "gd1977-05-08",
            addedToLibraryAt: now,
            isPinned: false,
            libraryNotes: nil,
            preferredRecordingId: nil,
            downloadedRecordingId: nil,
            downloadedFormat: nil,
            customRating: nil,
            lastAccessedAt: nil,
            tags: nil
        ))
        #expect(try libraryDAO.fetchCount() == 1)

        // Re-import with force
        let service2 = makeService(fixtures: fixtures, db: db)
        for await _ in service2.run(force: true) {}

        // Library should be restored
        #expect(try libraryDAO.fetchCount() == 1)
    }

    @Test("FTS search index is populated after import")
    func testFTSAfterImport() async throws {
        let fixtures = try Fixtures()
        defer { fixtures.cleanup() }

        try fixtures.writeShow([
            "show_id": "gd1977-05-08", "band": "GD", "venue": "Barton Hall",
            "date": "1977-05-08", "recordings": [], "avg_rating": 4.8,
            "recording_count": 0, "total_high_ratings": 0, "total_low_ratings": 0,
            "source_types": ["SBD": 1]
        ])
        try fixtures.writeManifest(version: "1.0.0")

        let db = try AppDatabase.makeEmpty()
        let service = makeService(fixtures: fixtures, db: db)
        for await _ in service.run(force: true) {}

        let showSearchDAO = ShowSearchDAO(database: db)
        let results = try showSearchDAO.search("Barton")
        #expect(results.contains("gd1977-05-08"))

        let dateResults = try showSearchDAO.search("1977-05-08")
        #expect(dateResults.contains("gd1977-05-08"))
    }

    @Test("Collections are imported and resolved")
    func testCollectionsImport() async throws {
        let fixtures = try Fixtures()
        defer { fixtures.cleanup() }

        try fixtures.writeShow([
            "show_id": "gd1977-05-08", "band": "GD", "venue": "Barton Hall",
            "date": "1977-05-08", "recordings": [], "avg_rating": 0.0,
            "recording_count": 0, "total_high_ratings": 0, "total_low_ratings": 0
        ])
        try fixtures.writeManifest(version: "1.0.0")
        try fixtures.writeCollections([
            ["id": "col1", "name": "My Collection", "description": "A collection",
             "tags": ["tag1"],
             "show_selector": ["show_ids": ["gd1977-05-08"]]]
        ])

        let db = try AppDatabase.makeEmpty()
        let service = makeService(fixtures: fixtures, db: db)
        for await _ in service.run(force: true) {}

        let collectionsDAO = CollectionsDAO(database: db)
        #expect(try collectionsDAO.fetchCount() == 1)
        let col = try collectionsDAO.fetchById("col1")
        #expect(col?.name == "My Collection")
        #expect(col?.totalShows == 1)
    }
}
