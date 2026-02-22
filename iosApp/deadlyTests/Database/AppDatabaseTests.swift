import Foundation
import Testing
import GRDB
@testable import deadly

@Suite("AppDatabase Schema Tests")
struct AppDatabaseTests {

    let db: AppDatabase

    init() throws {
        db = try AppDatabase.makeEmpty()
    }

    // MARK: - Table existence

    @Test("All 7 tables exist after migration")
    func allTablesExist() throws {
        try db.read { database in
            let tables = try String.fetchAll(
                database,
                sql: "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"
            )
            #expect(tables.contains("shows"))
            #expect(tables.contains("recordings"))
            #expect(tables.contains("show_search"))
            #expect(tables.contains("dead_collections"))
            #expect(tables.contains("library_shows"))
            #expect(tables.contains("recent_shows"))
            #expect(tables.contains("data_version"))
        }
    }

    // MARK: - Column verification

    @Test("shows table has expected columns")
    func showsColumns() throws {
        try db.read { database in
            let columns = try database.columns(in: "shows").map(\.name)
            #expect(columns.contains("showId"))
            #expect(columns.contains("date"))
            #expect(columns.contains("year"))
            #expect(columns.contains("month"))
            #expect(columns.contains("yearMonth"))
            #expect(columns.contains("band"))
            #expect(columns.contains("venueName"))
            #expect(columns.contains("city"))
            #expect(columns.contains("state"))
            #expect(columns.contains("country"))
            #expect(columns.contains("recordingCount"))
            #expect(columns.contains("averageRating"))
            #expect(columns.contains("isInLibrary"))
            #expect(columns.contains("createdAt"))
            #expect(columns.contains("updatedAt"))
        }
    }

    @Test("recordings table has snake_case columns matching Android @ColumnInfo")
    func recordingsColumns() throws {
        try db.read { database in
            let columns = try database.columns(in: "recordings").map(\.name)
            #expect(columns.contains("identifier"))
            #expect(columns.contains("show_id"))
            #expect(columns.contains("source_type"))
            #expect(columns.contains("rating"))
            #expect(columns.contains("raw_rating"))
            #expect(columns.contains("review_count"))
            #expect(columns.contains("confidence"))
            #expect(columns.contains("high_ratings"))
            #expect(columns.contains("low_ratings"))
            #expect(columns.contains("taper"))
            #expect(columns.contains("source"))
            #expect(columns.contains("lineage"))
            #expect(columns.contains("source_type_string"))
            #expect(columns.contains("collection_timestamp"))
        }
    }

    @Test("dead_collections table has expected columns")
    func deadCollectionsColumns() throws {
        try db.read { database in
            let columns = try database.columns(in: "dead_collections").map(\.name)
            #expect(columns.contains("id"))
            #expect(columns.contains("name"))
            #expect(columns.contains("description"))
            #expect(columns.contains("tagsJson"))
            #expect(columns.contains("showIdsJson"))
            #expect(columns.contains("totalShows"))
            #expect(columns.contains("primaryTag"))
            #expect(columns.contains("createdAt"))
            #expect(columns.contains("updatedAt"))
        }
    }

    @Test("library_shows table has expected columns")
    func libraryShowsColumns() throws {
        try db.read { database in
            let columns = try database.columns(in: "library_shows").map(\.name)
            #expect(columns.contains("showId"))
            #expect(columns.contains("addedToLibraryAt"))
            #expect(columns.contains("isPinned"))
            #expect(columns.contains("preferredRecordingId"))
            #expect(columns.contains("downloadedRecordingId"))
        }
    }

    @Test("recent_shows table has expected columns")
    func recentShowsColumns() throws {
        try db.read { database in
            let columns = try database.columns(in: "recent_shows").map(\.name)
            #expect(columns.contains("showId"))
            #expect(columns.contains("lastPlayedTimestamp"))
            #expect(columns.contains("firstPlayedTimestamp"))
            #expect(columns.contains("totalPlayCount"))
        }
    }

    @Test("data_version table has expected columns")
    func dataVersionColumns() throws {
        try db.read { database in
            let columns = try database.columns(in: "data_version").map(\.name)
            #expect(columns.contains("id"))
            #expect(columns.contains("dataVersion"))
            #expect(columns.contains("packageName"))
            #expect(columns.contains("totalShows"))
            #expect(columns.contains("totalSizeBytes"))
        }
    }

    // MARK: - CRUD smoke tests

    @Test("ShowRecord round-trips through the database")
    func showRecordRoundTrip() throws {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        var record = ShowRecord(
            showId: "1977-05-08-barton-hall",
            date: "1977-05-08",
            year: 1977,
            month: 5,
            yearMonth: "1977-05",
            band: "Grateful Dead",
            url: nil,
            venueName: "Barton Hall, Cornell University",
            city: "Ithaca",
            state: "NY",
            country: "USA",
            locationRaw: "Ithaca, NY",
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
        )
        try db.write { database in
            try record.insert(database)
        }
        let fetched = try db.read { database in
            try ShowRecord.fetchOne(database, key: "1977-05-08-barton-hall")
        }
        #expect(fetched?.showId == "1977-05-08-barton-hall")
        #expect(fetched?.venueName == "Barton Hall, Cornell University")
        #expect(fetched?.year == 1977)
    }

    // MARK: - Foreign key cascade

    @Test("Deleting a show cascades to recordings")
    func cascadeDeleteRecordings() throws {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        try db.write { database in
            var show = ShowRecord(
                showId: "cascade-test-show",
                date: "1977-05-08",
                year: 1977,
                month: 5,
                yearMonth: "1977-05",
                band: "Grateful Dead",
                url: nil,
                venueName: "Barton Hall",
                city: "Ithaca",
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
                recordingCount: 1,
                bestRecordingId: "rec-sbd",
                averageRating: 4.5,
                totalReviews: 10,
                isInLibrary: false,
                libraryAddedAt: nil,
                coverImageUrl: nil,
                createdAt: now,
                updatedAt: now
            )
            try show.insert(database)

            var recording = RecordingRecord(
                identifier: "rec-sbd-cascade-test",
                showId: "cascade-test-show",
                sourceType: "SBD",
                rating: 4.5,
                rawRating: 4.5,
                reviewCount: 10,
                confidence: 0.9,
                highRatings: 8,
                lowRatings: 1,
                taper: nil,
                source: nil,
                lineage: nil,
                sourceTypeString: nil,
                collectionTimestamp: now
            )
            try recording.insert(database)

            let countBefore = try RecordingRecord
                .filter(Column("show_id") == "cascade-test-show")
                .fetchCount(database)
            #expect(countBefore == 1)

            try ShowRecord.deleteOne(database, key: "cascade-test-show")

            let countAfter = try RecordingRecord
                .filter(Column("show_id") == "cascade-test-show")
                .fetchCount(database)
            #expect(countAfter == 0)
        }
    }

    @Test("Deleting a show cascades to library_shows")
    func cascadeDeleteLibraryShows() throws {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        try db.write { database in
            var show = ShowRecord(
                showId: "lib-cascade-show",
                date: "1977-05-08",
                year: 1977,
                month: 5,
                yearMonth: "1977-05",
                band: "Grateful Dead",
                url: nil,
                venueName: "Barton Hall",
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
                isInLibrary: true,
                libraryAddedAt: now,
                coverImageUrl: nil,
                createdAt: now,
                updatedAt: now
            )
            try show.insert(database)

            var libraryShow = LibraryShowRecord(
                showId: "lib-cascade-show",
                addedToLibraryAt: now,
                isPinned: false,
                libraryNotes: nil,
                preferredRecordingId: nil,
                downloadedRecordingId: nil,
                downloadedFormat: nil,
                customRating: nil,
                lastAccessedAt: nil,
                tags: nil
            )
            try libraryShow.insert(database)

            try ShowRecord.deleteOne(database, key: "lib-cascade-show")

            let count = try LibraryShowRecord.fetchCount(database)
            #expect(count == 0)
        }
    }

    // MARK: - data_version singleton

    @Test("data_version singleton: duplicate id=1 is rejected")
    func dataVersionSingletonConstraint() throws {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        try db.write { database in
            var v1 = DataVersionRecord(
                dataVersion: "1.0.0",
                packageName: "Deadly Metadata",
                versionType: "release",
                description: nil,
                importedAt: now,
                gitCommit: nil,
                gitTag: nil,
                buildTimestamp: nil,
                totalShows: 2400,
                totalVenues: 300,
                totalFiles: 0,
                totalSizeBytes: 0
            )
            try v1.insert(database)
        }

        var threw = false
        do {
            try db.write { database in
                var v2 = DataVersionRecord(
                    dataVersion: "2.0.0",
                    packageName: "Deadly Metadata",
                    versionType: "release",
                    description: nil,
                    importedAt: now,
                    gitCommit: nil,
                    gitTag: nil,
                    buildTimestamp: nil,
                    totalShows: 2500,
                    totalVenues: 310,
                    totalFiles: 0,
                    totalSizeBytes: 0
                )
                try v2.insert(database)
            }
        } catch {
            threw = true
        }
        #expect(threw, "Inserting a second data_version row with id=1 should fail")

        let count = try db.read { try DataVersionRecord.fetchCount($0) }
        #expect(count == 1)
    }
}
