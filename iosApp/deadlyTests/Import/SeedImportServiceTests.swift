import Foundation
import GRDB
import Testing
@testable import deadly

/// End-to-end test for the prebuilt-catalog fast path: build a tiny neutral
/// `catalog.db` seed, attach-copy it into a freshly migrated app DB, and verify
/// the catalog, the rebuilt FTS index, the committed `data_version` health gate,
/// and favorites preservation across the `shows` CASCADE.
@Suite("SeedImportService")
struct SeedImportServiceTests {

    @Test("Imports catalog, rebuilds FTS, writes data_version, preserves favorites")
    func roundTrip() throws {
        let db = try AppDatabase.makeEmpty()
        let favoritesDAO = FavoritesDAO(database: db)

        // Pre-existing user state: a show + a favorite that must survive re-import.
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        try db.write { database in
            try database.execute(sql: """
                INSERT INTO shows (showId, date, year, month, yearMonth, band, venueName, createdAt, updatedAt)
                VALUES ('s1', '1977-05-08', 1977, 5, '1977-05', 'Grateful Dead', 'Old Venue', ?, ?)
            """, arguments: [now, now])
        }
        try favoritesDAO.add(FavoriteShowRecord(showId: "s1", addedToFavoritesAt: now, updatedAt: now))

        // Build the neutral seed and run the import.
        let seedURL = try makeSeedFile()
        defer { try? FileManager.default.removeItem(at: seedURL) }

        let importer = SeedImportService(database: db, favoritesDAO: favoritesDAO)
        let result = try importer.importSeed(at: seedURL)

        #expect(result.shows == 2)
        #expect(result.recordings == 2)

        try db.read { database in
            // Catalog copied
            #expect(try Int.fetchOne(database, sql: "SELECT COUNT(*) FROM shows") == 2)
            #expect(try Int.fetchOne(database, sql: "SELECT COUNT(*) FROM recordings") == 2)
            #expect(try Int.fetchOne(database, sql: "SELECT COUNT(*) FROM dead_collections") == 1)
            // Seed venue overwrote the pre-existing show row
            #expect(try String.fetchOne(database, sql: "SELECT venueName FROM shows WHERE showId='s1'") == "Barton Hall")

            // FTS rebuilt: one row per show, with derived tags searchable
            #expect(try Int.fetchOne(database, sql: "SELECT COUNT(*) FROM show_search") == 2)
            let bartonHits = try String.fetchAll(database, sql: "SELECT showId FROM show_search WHERE show_search MATCH 'Barton'")
            #expect(bartonHits == ["s1"])
            let sbdHits = try String.fetchAll(database, sql: "SELECT showId FROM show_search WHERE show_search MATCH 'soundboard'")
            #expect(sbdHits == ["s1"])
            let popularHits = try String.fetchAll(database, sql: "SELECT showId FROM show_search WHERE show_search MATCH 'popular'")
            #expect(popularHits == ["s1"])

            // data_version committed (the health gate)
            #expect(try Int.fetchOne(database, sql: "SELECT COUNT(*) FROM data_version") == 1)
        }

        // Health gate flips
        #expect(try DataVersionDAO(database: db).hasData() == true)

        // Favorite for s1 preserved across the CASCADE
        let favorites = try favoritesDAO.fetchAll()
        #expect(favorites.contains { $0.showId == "s1" })
    }

    @Test("Rejects a non-SQLite file")
    func rejectsGarbage() throws {
        let db = try AppDatabase.makeEmpty()
        let importer = SeedImportService(database: db, favoritesDAO: FavoritesDAO(database: db))

        let bogus = FileManager.default.temporaryDirectory.appendingPathComponent("\(UUID()).db")
        try Data("not a database".utf8).write(to: bogus)
        defer { try? FileManager.default.removeItem(at: bogus) }

        #expect(throws: ImportError.self) {
            _ = try importer.importSeed(at: bogus)
        }
    }

    // MARK: - Seed builder

    /// Creates a minimal neutral catalog seed (catalog tables only, no FTS, no
    /// device-local tables) at a temp path — mirrors what `build_catalog_db.py` ships.
    private func makeSeedFile() throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("\(UUID()).db")
        let queue = try DatabaseQueue(path: url.path)
        try queue.write { db in
            try db.execute(sql: """
                CREATE TABLE shows (
                    showId TEXT PRIMARY KEY, date TEXT, year INTEGER, month INTEGER, yearMonth TEXT,
                    band TEXT, url TEXT, venueName TEXT, city TEXT, state TEXT, country TEXT,
                    locationRaw TEXT, setlistStatus TEXT, setlistRaw TEXT, songList TEXT,
                    lineupStatus TEXT, lineupRaw TEXT, memberList TEXT, showSequence INTEGER,
                    recordingsRaw TEXT, recordingCount INTEGER, bestRecordingId TEXT, bestSourceType TEXT,
                    averageRating REAL, totalReviews INTEGER, coverImageUrl TEXT, createdAt INTEGER, updatedAt INTEGER
                );
                CREATE TABLE recordings (
                    identifier TEXT PRIMARY KEY, show_id TEXT, source_type TEXT, rating REAL, raw_rating REAL,
                    review_count INTEGER, confidence REAL, high_ratings INTEGER, low_ratings INTEGER,
                    taper TEXT, source TEXT, lineage TEXT, source_type_string TEXT, collection_timestamp INTEGER
                );
                CREATE TABLE dead_collections (
                    id TEXT PRIMARY KEY, name TEXT, description TEXT, tagsJson TEXT, showIdsJson TEXT,
                    totalShows INTEGER, primaryTag TEXT, createdAt INTEGER, updatedAt INTEGER
                );
                CREATE TABLE data_version (
                    id INTEGER PRIMARY KEY, dataVersion TEXT, packageName TEXT, versionType TEXT, description TEXT,
                    importedAt INTEGER, gitCommit TEXT, gitTag TEXT, buildTimestamp TEXT,
                    totalShows INTEGER, totalVenues INTEGER, totalFiles INTEGER, totalSizeBytes INTEGER
                );
            """)

            // s1: SBD, highly rated + popular (avg 4.5, 60 reviews) → "soundboard sbd", "top-rated", "popular"
            try db.execute(sql: """
                INSERT INTO shows VALUES
                ('s1','1977-05-08',1977,5,'1977-05','Grateful Dead',NULL,'Barton Hall','Ithaca','NY','USA',
                 'Ithaca, NY',NULL,NULL,'Scarlet Begonias,Fire On The Mountain',NULL,NULL,'Jerry Garcia,Bob Weir',
                 1,NULL,1,NULL,'SBD',4.5,60,NULL,1,1),
                ('s2','1969-02-27',1969,2,'1969-02','Grateful Dead',NULL,'Fillmore West','San Francisco','CA','USA',
                 'San Francisco, CA',NULL,NULL,'Dark Star',NULL,NULL,'Jerry Garcia',
                 1,NULL,1,NULL,'AUD',0.0,0,NULL,1,1);
            """)
            try db.execute(sql: """
                INSERT INTO recordings (identifier, show_id, source_type, rating, raw_rating, review_count, confidence, high_ratings, low_ratings, collection_timestamp)
                VALUES
                ('r1','s1','SBD',4.5,4.5,60,0.9,55,5,1),
                ('r2','s2','AUD',0.0,0.0,0,0.0,0,0,1);
            """)
            try db.execute(sql: """
                INSERT INTO dead_collections VALUES ('c1','Cornell 77','The legendary run','[]','["s1"]',1,NULL,1,1);
            """)
            try db.execute(sql: """
                INSERT INTO data_version (id, dataVersion, packageName, versionType, importedAt, totalShows, totalVenues, totalFiles, totalSizeBytes)
                VALUES (1,'9.9.9','deadly-monorepo-data','release',1,2,2,2,0);
            """)
        }
        // Close the queue so the file is fully flushed before SeedImportService ATTACHes it.
        return url
    }
}
