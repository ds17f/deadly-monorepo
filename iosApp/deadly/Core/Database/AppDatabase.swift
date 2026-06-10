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

    /// Exclusive write that runs OUTSIDE GRDB's automatic transaction. Required
    /// for statements SQLite forbids inside a transaction — notably `ATTACH`/
    /// `DETACH` (see `SeedImportService`). The block manages its own transaction
    /// for the actual writes. Barrier semantics block concurrent reads/writes for
    /// the duration, which suits the one-shot catalog seed import.
    func writeWithoutTransaction<T>(_ block: (Database) throws -> T) throws -> T {
        try dbWriter.barrierWriteWithoutTransaction(block)
    }

    func observe<Reducer: ValueReducer>(
        _ observation: ValueObservation<Reducer>
    ) -> AsyncValueObservation<Reducer.Value> where Reducer.Value: Equatable & Sendable {
        observation.values(in: dbWriter, bufferingPolicy: .bufferingNewest(1))
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
        migrator.registerMigration("v4-reviews") { db in
            try AppDatabase.createReviewsTables(db)
        }
        migrator.registerMigration("v5-show-reviews-table") { db in
            try AppDatabase.createShowReviewsTable(db)
        }
        migrator.registerMigration("v6-recording-preferences") { db in
            try AppDatabase.createRecordingPreferencesTable(db)
        }
        migrator.registerMigration("v7-rename-library-to-favorites") { db in
            try AppDatabase.renameLibraryToFavorites(db)
        }
        migrator.registerMigration("v8-favorite-songs") { db in
            try AppDatabase.replaceTrackReviewsWithFavoriteSongs(db)
        }
        migrator.registerMigration("v9-best-source-type") { db in
            try db.execute(sql: "ALTER TABLE shows ADD COLUMN bestSourceType TEXT")
        }
        migrator.registerMigration("v10-backfill-best-source-type") { db in
            try db.execute(sql: """
                UPDATE shows
                SET bestSourceType = (
                    SELECT CASE
                        WHEN SUM(CASE WHEN r.source_type = 'SBD' THEN 1 ELSE 0 END) > 0 THEN 'SBD'
                        WHEN SUM(CASE WHEN r.source_type = 'FM' THEN 1 ELSE 0 END) > 0 THEN 'FM'
                        WHEN SUM(CASE WHEN r.source_type = 'MATRIX' THEN 1 ELSE 0 END) > 0 THEN 'MATRIX'
                        WHEN SUM(CASE WHEN r.source_type = 'REMASTER' THEN 1 ELSE 0 END) > 0 THEN 'REMASTER'
                        WHEN SUM(CASE WHEN r.source_type = 'AUD' THEN 1 ELSE 0 END) > 0 THEN 'AUD'
                        ELSE NULL
                    END
                    FROM recordings r
                    WHERE r.show_id = shows.showId
                )
                WHERE bestSourceType IS NULL
            """)
        }
        migrator.registerMigration("v11-sync-columns") { db in
            try AppDatabase.addSyncColumns(db)
        }
        migrator.registerMigration("v12-sync-outbox") { db in
            try AppDatabase.createSyncOutboxTable(db)
        }
        migrator.registerMigration("v13-sync-columns-camelcase") { db in
            try AppDatabase.renameSyncColumnsToCamelCase(db)
        }
        migrator.registerMigration("v14-favorite-songs-natural-key") { db in
            try AppDatabase.relaxFavoriteSongsNaturalKey(db)
        }
        migrator.registerMigration("v15-play-queue") { db in
            try AppDatabase.createPlayQueueTable(db)
        }
        try migrator.migrate(dbWriter)
    }

    /// Persistent show queue (ADR-0010). Local-only, never synced. Ordered by
    /// `position` ascending (head = MIN).
    private static func createPlayQueueTable(_ db: Database) throws {
        try db.create(table: "play_queue", ifNotExists: true) { t in
            t.autoIncrementedPrimaryKey("id")
            t.column("showId", .text).notNull()
            t.column("recordingId", .text)
            t.column("position", .integer).notNull()
            t.column("resumeTrackIndex", .integer)
            t.column("resumePositionMs", .integer)
            t.column("addedAt", .integer).notNull()
        }
        try db.create(index: "idx_play_queue_position", on: "play_queue", columns: ["position"], ifNotExists: true)
    }

    /// Outbox of pending server pushes. One row per (kind, refId) — re-enqueue
    /// is a no-op via the UNIQUE constraint. Flusher reads current local row
    /// state at push time and decides PUT vs DELETE based on deleted_at.
    private static func createSyncOutboxTable(_ db: Database) throws {
        try db.create(table: "sync_outbox", ifNotExists: true) { t in
            t.autoIncrementedPrimaryKey("id")
            t.column("kind", .text).notNull()
            t.column("refId", .text).notNull()
            t.column("createdAt", .integer).notNull()
            t.column("lastAttemptAt", .integer)
            t.column("attemptCount", .integer).notNull().defaults(to: 0)
            t.column("lastError", .text)
            t.uniqueKey(["kind", "refId"])
        }
        try db.create(index: "idx_sync_outbox_kind", on: "sync_outbox", columns: ["kind"], ifNotExists: true)
    }

    /// Additive sync support: per-row updated_at (LWW comparator) and
    /// deleted_at (tombstone). Matches the server contract in
    /// api/src/db/userdata.ts. Singletons don't need tombstones.
    ///
    /// SQLite ALTER TABLE requires a *constant* default, so we add the
    /// updated_at columns with DEFAULT 0 then backfill existing rows
    /// to the current timestamp in a follow-up UPDATE.
    private static func addSyncColumns(_ db: Database) throws {
        func addColumn(_ table: String, _ name: String, _ sql: String) throws {
            let cols = try Row.fetchAll(db, sql: "PRAGMA table_info(\(table))")
            let has = cols.contains { ($0["name"] as? String) == name }
            if !has {
                try db.execute(sql: "ALTER TABLE \(table) ADD COLUMN \(name) \(sql)")
            }
        }

        try addColumn("favorite_shows", "updatedAt", "INTEGER NOT NULL DEFAULT 0")
        try addColumn("favorite_shows", "deletedAt", "INTEGER")
        try addColumn("favorite_songs", "updatedAt", "INTEGER NOT NULL DEFAULT 0")
        try addColumn("favorite_songs", "deletedAt", "INTEGER")
        try addColumn("show_reviews", "deletedAt", "INTEGER")
        try addColumn("recording_preferences", "deletedAt", "INTEGER")
        try addColumn("recent_shows", "deletedAt", "INTEGER")

        let now = "CAST(strftime('%s','now') AS INTEGER)"
        try db.execute(sql: "UPDATE favorite_shows SET updatedAt = \(now) WHERE updatedAt = 0")
        try db.execute(sql: "UPDATE favorite_songs SET updatedAt = \(now) WHERE updatedAt = 0")
    }

    /// Fix for an earlier v11 that created snake_case columns
    /// (`updated_at`, `deleted_at`) while the Swift records and DAOs
    /// use camelCase. Rename in place when the bad columns are present.
    private static func renameSyncColumnsToCamelCase(_ db: Database) throws {
        func renameIfPresent(_ table: String, _ from: String, _ to: String) throws {
            let cols = try Row.fetchAll(db, sql: "PRAGMA table_info(\(table))")
            let names = cols.compactMap { $0["name"] as? String }
            if names.contains(from) && !names.contains(to) {
                try db.execute(sql: "ALTER TABLE \(table) RENAME COLUMN \(from) TO \(to)")
            }
        }
        try renameIfPresent("favorite_shows", "updated_at", "updatedAt")
        try renameIfPresent("favorite_shows", "deleted_at", "deletedAt")
        try renameIfPresent("favorite_songs", "updated_at", "updatedAt")
        try renameIfPresent("favorite_songs", "deleted_at", "deletedAt")
        try renameIfPresent("show_reviews", "deleted_at", "deletedAt")
        try renameIfPresent("recording_preferences", "deleted_at", "deletedAt")
        try renameIfPresent("recent_shows", "deleted_at", "deletedAt")
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

    // MARK: - Reviews Tables

    private static func createReviewsTables(_ db: Database) throws {
        // Add review columns to library_shows
        let columns = try Row.fetchAll(db, sql: "PRAGMA table_info(library_shows)")
        let columnNames = Set(columns.compactMap { $0["name"] as? String })
        if !columnNames.contains("recordingQuality") {
            try db.execute(sql: "ALTER TABLE library_shows ADD COLUMN recordingQuality INTEGER")
        }
        if !columnNames.contains("playingQuality") {
            try db.execute(sql: "ALTER TABLE library_shows ADD COLUMN playingQuality INTEGER")
        }

        // track_reviews
        try db.create(table: "track_reviews", ifNotExists: true) { t in
            t.autoIncrementedPrimaryKey("id")
            t.column("showId", .text).notNull()
                .references("shows", column: "showId", onDelete: .cascade)
            t.column("trackTitle", .text).notNull()
            t.column("trackNumber", .integer)
            t.column("recordingId", .text)
            t.column("thumbs", .integer)
            t.column("starRating", .integer)
            t.column("notes", .text)
            t.column("createdAt", .integer).notNull()
            t.column("updatedAt", .integer).notNull()
            t.uniqueKey(["showId", "trackTitle", "recordingId"])
        }
        try db.create(index: "idx_track_reviews_showId", on: "track_reviews", columns: ["showId"], ifNotExists: true)

        // show_player_tags
        try db.create(table: "show_player_tags", ifNotExists: true) { t in
            t.autoIncrementedPrimaryKey("id")
            t.column("showId", .text).notNull()
                .references("shows", column: "showId", onDelete: .cascade)
            t.column("playerName", .text).notNull()
            t.column("instruments", .text)
            t.column("isStandout", .boolean).notNull().defaults(to: true)
            t.column("notes", .text)
            t.column("createdAt", .integer).notNull()
            t.uniqueKey(["showId", "playerName"])
        }
        try db.create(index: "idx_show_player_tags_showId", on: "show_player_tags", columns: ["showId"], ifNotExists: true)
        try db.create(index: "idx_show_player_tags_playerName", on: "show_player_tags", columns: ["playerName"], ifNotExists: true)
    }

    // MARK: - Recording Preferences Table

    private static func createRecordingPreferencesTable(_ db: Database) throws {
        try db.create(table: "recording_preferences", ifNotExists: true) { t in
            t.column("showId", .text).primaryKey()
                .references("shows", column: "showId", onDelete: .cascade)
            t.column("recordingId", .text).notNull()
            t.column("updatedAt", .integer).notNull()
        }

        // Migrate existing preferred recordings from library_shows
        try db.execute(sql: """
            INSERT OR IGNORE INTO recording_preferences (showId, recordingId, updatedAt)
            SELECT showId, preferredRecordingId,
                   CAST(strftime('%s','now') AS INTEGER) * 1000
            FROM library_shows
            WHERE preferredRecordingId IS NOT NULL
        """)
    }

    // MARK: - Rename Library to Favorites

    private static func renameLibraryToFavorites(_ db: Database) throws {
        try db.execute(sql: "ALTER TABLE library_shows RENAME TO favorite_shows")
        try db.execute(sql: "ALTER TABLE shows RENAME COLUMN isInLibrary TO isFavorite")
        try db.execute(sql: "ALTER TABLE shows RENAME COLUMN libraryAddedAt TO favoritedAt")
        try db.execute(sql: "ALTER TABLE favorite_shows RENAME COLUMN addedToLibraryAt TO addedToFavoritesAt")
        try db.execute(sql: "ALTER TABLE favorite_shows RENAME COLUMN libraryNotes TO notes")
    }

    /// Drops `recordingId` from the favorite_songs natural-key uniqueness.
    /// Server identifies song favorites by (user, showId, trackTitle); mobile
    /// was using (showId, trackTitle, recordingId), which created phantom
    /// duplicates whenever sync round-tripped a row whose stored recordingId
    /// differed from what the local UI was viewing with. recordingId stays on
    /// the row as a property (used by the favorites screen for navigation).
    ///
    /// Dedupe rule: per (showId, trackTitle), keep the row with the highest
    /// (deletedAt IS NULL, updatedAt) — live rows preferred, then most recent.
    private static func relaxFavoriteSongsNaturalKey(_ db: Database) throws {
        // Step 1: dedupe. SQLite doesn't allow a self-join in DELETE the easy
        // way, so we materialize the winners first.
        try db.execute(sql: """
            CREATE TEMP TABLE favorite_songs_keep AS
            SELECT id FROM favorite_songs s1
             WHERE id = (
                 SELECT id FROM favorite_songs s2
                  WHERE s2.showId = s1.showId AND s2.trackTitle = s1.trackTitle
                  ORDER BY (s2.deletedAt IS NULL) DESC, s2.updatedAt DESC, s2.id DESC
                  LIMIT 1
             )
        """)
        try db.execute(sql: """
            DELETE FROM favorite_songs
             WHERE id NOT IN (SELECT id FROM favorite_songs_keep)
        """)
        try db.execute(sql: "DROP TABLE favorite_songs_keep")

        // Step 2: swap the unique index. SQLite has no DROP CONSTRAINT — the
        // original unique key was declared inline on CREATE TABLE, so it
        // lives as an autogenerated index. List indexes and drop any whose
        // SQL mentions all three columns; then create the new (showId,trackTitle) one.
        let oldIndexes = try Row.fetchAll(db, sql: """
            SELECT name, sql FROM sqlite_master
             WHERE type = 'index' AND tbl_name = 'favorite_songs'
        """)
        for row in oldIndexes {
            guard let name = row["name"] as? String,
                  let sql = row["sql"] as? String? ?? nil else { continue }
            // skip autoindex on primary key (sql is null), keep idx_favorite_songs_showId
            if name == "idx_favorite_songs_showId" { continue }
            if sql.contains("recordingId") && sql.contains("trackTitle") {
                try db.execute(sql: "DROP INDEX IF EXISTS \(name)")
            }
        }
        // Some SQLite versions store the auto-generated index under
        // sqlite_autoindex_favorite_songs_N with NULL sql. Drop those too if
        // the table has more than one autoindex (the primary key gets one).
        // The safest path: rebuild the table without the inline unique key.
        // GRDB's table builder makes this simple via a "create alongside, copy, swap" dance.
        try db.execute(sql: "PRAGMA foreign_keys = OFF")
        try db.execute(sql: """
            CREATE TABLE favorite_songs_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                showId TEXT NOT NULL REFERENCES shows(showId) ON DELETE CASCADE,
                trackTitle TEXT NOT NULL,
                trackNumber INTEGER,
                recordingId TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL DEFAULT 0,
                deletedAt INTEGER
            )
        """)
        try db.execute(sql: """
            INSERT INTO favorite_songs_new (id, showId, trackTitle, trackNumber, recordingId, createdAt, updatedAt, deletedAt)
            SELECT id, showId, trackTitle, trackNumber, recordingId, createdAt, updatedAt, deletedAt FROM favorite_songs
        """)
        try db.execute(sql: "DROP TABLE favorite_songs")
        try db.execute(sql: "ALTER TABLE favorite_songs_new RENAME TO favorite_songs")
        try db.execute(sql: "CREATE UNIQUE INDEX idx_favorite_songs_showId_trackTitle ON favorite_songs(showId, trackTitle)")
        try db.execute(sql: "CREATE INDEX idx_favorite_songs_showId ON favorite_songs(showId)")
        try db.execute(sql: "PRAGMA foreign_keys = ON")
    }

    // MARK: - Replace track_reviews with favorite_songs

    private static func replaceTrackReviewsWithFavoriteSongs(_ db: Database) throws {
        try db.create(table: "favorite_songs") { t in
            t.autoIncrementedPrimaryKey("id")
            t.column("showId", .text).notNull()
                .references("shows", column: "showId", onDelete: .cascade)
            t.column("trackTitle", .text).notNull()
            t.column("trackNumber", .integer)
            t.column("recordingId", .text)
            t.column("createdAt", .integer).notNull()
            t.uniqueKey(["showId", "trackTitle", "recordingId"])
        }
        try db.create(index: "idx_favorite_songs_showId", on: "favorite_songs", columns: ["showId"])

        // Migrate favorited tracks (thumbs = 1)
        try db.execute(sql: """
            INSERT OR IGNORE INTO favorite_songs (showId, trackTitle, trackNumber, recordingId, createdAt)
            SELECT showId, trackTitle, trackNumber, recordingId, updatedAt
            FROM track_reviews
            WHERE thumbs = 1
        """)

        try db.drop(table: "track_reviews")
    }

    // MARK: - Show Reviews Table

    private static func createShowReviewsTable(_ db: Database) throws {
        try db.create(table: "show_reviews", ifNotExists: true) { t in
            t.column("showId", .text).primaryKey()
                .references("shows", column: "showId", onDelete: .cascade)
            t.column("notes", .text)
            t.column("customRating", .double)
            t.column("recordingQuality", .integer)
            t.column("playingQuality", .integer)
            t.column("reviewedRecordingId", .text)
            t.column("createdAt", .integer).notNull()
            t.column("updatedAt", .integer).notNull()
        }

        // Migrate existing review data from library_shows
        try db.execute(sql: """
            INSERT OR IGNORE INTO show_reviews (showId, notes, customRating, recordingQuality, playingQuality, createdAt, updatedAt)
            SELECT showId, libraryNotes, customRating, recordingQuality, playingQuality,
                   CAST(strftime('%s','now') AS INTEGER) * 1000,
                   CAST(strftime('%s','now') AS INTEGER) * 1000
            FROM library_shows
            WHERE libraryNotes IS NOT NULL OR customRating IS NOT NULL
               OR recordingQuality IS NOT NULL OR playingQuality IS NOT NULL
        """)
    }
}
