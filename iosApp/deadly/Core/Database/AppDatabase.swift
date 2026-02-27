import Foundation
import GRDB

/// Wraps the SQLite database pool. Use `makeDefault()` for production and `makeEmpty()` for tests.
struct AppDatabase: @unchecked Sendable {
    private let dbWriter: any DatabaseWriter

    private init(_ dbWriter: any DatabaseWriter) {
        self.dbWriter = dbWriter
    }

    // MARK: - Factory

    static func makeDefault() throws -> AppDatabase {
        let fileManager = FileManager.default
        let appSupportURL = try fileManager.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        let dbURL = appSupportURL.appendingPathComponent("deadly.sqlite")
        var config = Configuration()
        config.prepareDatabase { db in
            try db.execute(sql: "PRAGMA foreign_keys = ON")
        }
        #if DEBUG
        config.publicStatementArguments = true
        #endif
        let pool = try DatabasePool(path: dbURL.path, configuration: config)
        let db = AppDatabase(pool)
        try db.migrate()
        return db
    }

    /// In-memory database for unit tests. Each call returns an isolated database.
    static func makeEmpty() throws -> AppDatabase {
        var config = Configuration()
        config.prepareDatabase { db in
            try db.execute(sql: "PRAGMA foreign_keys = ON")
        }
        let queue = try DatabaseQueue(path: ":memory:", configuration: config)
        let db = AppDatabase(queue)
        try db.migrate()
        return db
    }

    // MARK: - Access

    func read<T>(_ block: (Database) throws -> T) throws -> T {
        try dbWriter.read(block)
    }

    func write<T>(_ block: (Database) throws -> T) throws -> T {
        try dbWriter.write(block)
    }

    // MARK: - Migration

    private func migrate() throws {
        var migrator = DatabaseMigrator()
        // Note: eraseDatabaseOnSchemaChange is intentionally NOT set.
        // Migrations should behave identically in debug and release builds
        // to catch migration issues during development.
        migrator.registerMigration("v1-create-all-tables") { db in
            try AppDatabase.createSchema(db)
        }
        migrator.registerMigration("v2-download-tasks") { db in
            try AppDatabase.createDownloadTasksTable(db)
        }
        migrator.registerMigration("v3-add-coverImageUrl") { db in
            // Check which table name exists (shows vs Show) for compatibility
            let tableExists = try Bool.fetchOne(db, sql: """
                SELECT 1 FROM sqlite_master WHERE type='table' AND name='shows'
            """) ?? false
            let tableName = tableExists ? "shows" : "Show"

            // Check if column already exists
            let columns = try Row.fetchAll(db, sql: "PRAGMA table_info(\(tableName))")
            let hasColumn = columns.contains { $0["name"] as? String == "coverImageUrl" }

            if !hasColumn {
                try db.execute(sql: "ALTER TABLE \(tableName) ADD COLUMN coverImageUrl TEXT")
            }
        }
        try migrator.migrate(dbWriter)
    }

    // MARK: - Schema

    private static func createSchema(_ db: Database) throws {
        // MARK: shows
        try db.create(table: "shows") { t in
            t.column("showId", .text).primaryKey()
            t.column("date", .text).notNull()
            t.column("year", .integer).notNull()
            t.column("month", .integer).notNull()
            t.column("yearMonth", .text).notNull()
            t.column("band", .text).notNull()
            t.column("url", .text)
            t.column("venueName", .text).notNull()
            t.column("city", .text)
            t.column("state", .text)
            t.column("country", .text).notNull().defaults(to: "USA")
            t.column("locationRaw", .text)
            t.column("setlistStatus", .text)
            t.column("setlistRaw", .text)
            t.column("songList", .text)
            t.column("lineupStatus", .text)
            t.column("lineupRaw", .text)
            t.column("memberList", .text)
            t.column("showSequence", .integer).notNull().defaults(to: 1)
            t.column("recordingsRaw", .text)
            t.column("recordingCount", .integer).notNull().defaults(to: 0)
            t.column("bestRecordingId", .text)
            t.column("averageRating", .double)
            t.column("totalReviews", .integer).notNull().defaults(to: 0)
            t.column("isInLibrary", .boolean).notNull().defaults(to: false)
            t.column("libraryAddedAt", .integer)
            t.column("coverImageUrl", .text)
            t.column("createdAt", .integer).notNull()
            t.column("updatedAt", .integer).notNull()
        }
        try db.create(index: "idx_shows_date", on: "shows", columns: ["date"])
        try db.create(index: "idx_shows_year", on: "shows", columns: ["year"])
        try db.create(index: "idx_shows_yearMonth", on: "shows", columns: ["yearMonth"])
        try db.create(index: "idx_shows_venueName", on: "shows", columns: ["venueName"])
        try db.create(index: "idx_shows_city", on: "shows", columns: ["city"])
        try db.create(index: "idx_shows_state", on: "shows", columns: ["state"])

        // MARK: recordings
        try db.create(table: "recordings") { t in
            t.column("identifier", .text).primaryKey()
            t.column("show_id", .text).notNull()
                .references("shows", column: "showId", onDelete: .cascade)
            t.column("source_type", .text)
            t.column("rating", .double).notNull().defaults(to: 0.0)
            t.column("raw_rating", .double).notNull().defaults(to: 0.0)
            t.column("review_count", .integer).notNull().defaults(to: 0)
            t.column("confidence", .double).notNull().defaults(to: 0.0)
            t.column("high_ratings", .integer).notNull().defaults(to: 0)
            t.column("low_ratings", .integer).notNull().defaults(to: 0)
            t.column("taper", .text)
            t.column("source", .text)
            t.column("lineage", .text)
            t.column("source_type_string", .text)
            t.column("collection_timestamp", .integer).notNull()
        }
        try db.create(index: "idx_recordings_show_id", on: "recordings", columns: ["show_id"])
        try db.create(index: "idx_recordings_source_type", on: "recordings", columns: ["source_type"])
        try db.create(index: "idx_recordings_rating", on: "recordings", columns: ["rating"])
        try db.create(index: "idx_recordings_show_rating", on: "recordings", columns: ["show_id", "rating"])

        // MARK: show_search (FTS4)
        // Raw SQL to ensure exact tokenizer args: tokenchars=-. treats hyphens and dots as word chars
        // so that dates like "5-8-77" match as a single token.
        try db.execute(sql: """
            CREATE VIRTUAL TABLE "show_search" USING fts4(
                "showId",
                "searchText",
                tokenize=unicode61 "tokenchars=-."
            )
            """)

        // MARK: dead_collections
        try db.create(table: "dead_collections") { t in
            t.column("id", .text).primaryKey()
            t.column("name", .text).notNull()
            t.column("description", .text).notNull()
            t.column("tagsJson", .text).notNull()
            t.column("showIdsJson", .text).notNull()
            t.column("totalShows", .integer).notNull()
            t.column("primaryTag", .text)
            t.column("createdAt", .integer).notNull()
            t.column("updatedAt", .integer).notNull()
        }
        try db.create(index: "idx_dead_collections_primaryTag", on: "dead_collections", columns: ["primaryTag"])
        try db.create(index: "idx_dead_collections_totalShows", on: "dead_collections", columns: ["totalShows"])

        // MARK: library_shows
        try db.create(table: "library_shows") { t in
            t.column("showId", .text).primaryKey()
                .references("shows", column: "showId", onDelete: .cascade)
            t.column("addedToLibraryAt", .integer).notNull()
            t.column("isPinned", .boolean).notNull().defaults(to: false)
            t.column("libraryNotes", .text)
            t.column("preferredRecordingId", .text)
            t.column("downloadedRecordingId", .text)
            t.column("downloadedFormat", .text)
            t.column("customRating", .double)
            t.column("lastAccessedAt", .integer)
            t.column("tags", .text)
        }
        try db.create(index: "idx_library_shows_addedToLibraryAt", on: "library_shows", columns: ["addedToLibraryAt"])
        try db.create(index: "idx_library_shows_isPinned", on: "library_shows", columns: ["isPinned"])

        // MARK: recent_shows
        try db.create(table: "recent_shows") { t in
            t.column("showId", .text).primaryKey()
            t.column("lastPlayedTimestamp", .integer).notNull()
            t.column("firstPlayedTimestamp", .integer).notNull()
            t.column("totalPlayCount", .integer).notNull().defaults(to: 0)
        }
        try db.create(index: "idx_recent_shows_lastPlayedTimestamp", on: "recent_shows", columns: ["lastPlayedTimestamp"])

        // MARK: data_version
        try db.create(table: "data_version") { t in
            t.column("id", .integer).primaryKey()
            t.column("dataVersion", .text).notNull()
            t.column("packageName", .text).notNull()
            t.column("versionType", .text).notNull()
            t.column("description", .text)
            t.column("importedAt", .integer).notNull()
            t.column("gitCommit", .text)
            t.column("gitTag", .text)
            t.column("buildTimestamp", .text)
            t.column("totalShows", .integer).notNull().defaults(to: 0)
            t.column("totalVenues", .integer).notNull().defaults(to: 0)
            t.column("totalFiles", .integer).notNull().defaults(to: 0)
            t.column("totalSizeBytes", .integer).notNull().defaults(to: 0)
        }
    }

    // MARK: - Download Tasks Table

    private static func createDownloadTasksTable(_ db: Database) throws {
        try db.create(table: "download_tasks", ifNotExists: true) { t in
            t.column("identifier", .text).primaryKey()
            t.column("showId", .text).notNull()
            t.column("recordingId", .text).notNull()
            t.column("trackFilename", .text).notNull()
            t.column("remoteURL", .text).notNull()
            t.column("state", .text).notNull()
            t.column("bytesDownloaded", .integer).notNull().defaults(to: 0)
            t.column("totalBytes", .integer).notNull().defaults(to: 0)
            t.column("resumeData", .blob)
            t.column("errorMessage", .text)
            t.column("createdAt", .integer).notNull()
            t.column("updatedAt", .integer).notNull()
        }
        try db.create(index: "idx_download_tasks_showId", on: "download_tasks", columns: ["showId"], ifNotExists: true)
        try db.create(index: "idx_download_tasks_state", on: "download_tasks", columns: ["state"], ifNotExists: true)
    }
}
