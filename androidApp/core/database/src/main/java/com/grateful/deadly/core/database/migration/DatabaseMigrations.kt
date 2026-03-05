package com.grateful.deadly.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room schema migrations for the Deadly database.
 *
 * Migrations are applied in order. fallbackToDestructiveMigration() remains
 * as a safety net for fresh installs or skipped versions, but these explicit
 * migrations preserve user data (library, recent plays) across upgrades.
 */
object DatabaseMigrations {

    /**
     * v12 → v13: Add cover image URL to shows table.
     *
     * Stores the best resolved image URL (ticket front > ticket unknown > photo)
     * so the UI can display show-specific cover art instead of archive.org waveforms.
     */
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE shows ADD COLUMN coverImageUrl TEXT DEFAULT NULL")
        }
    }

    /**
     * v13 → v14: Add download tracking columns to library_shows.
     *
     * Tracks which recording and format were downloaded for offline playback.
     * Download state itself is managed by Media3's DownloadManager, not Room.
     */
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE library_shows ADD COLUMN downloadedRecordingId TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE library_shows ADD COLUMN downloadedFormat TEXT DEFAULT NULL")
        }
    }

    /**
     * v14 → v15: Add preferred recording ID to library_shows.
     *
     * Persists the user's preferred recording selection per show so it
     * survives navigation and app restarts.
     */
    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE library_shows ADD COLUMN preferredRecordingId TEXT DEFAULT NULL")
        }
    }

    /**
     * v15 → v16: Add review system tables and columns.
     *
     * - Adds recordingQuality and playingQuality columns to library_shows
     * - Creates track_reviews table for per-track ratings and notes
     * - Creates show_player_tags table for tagging standout musicians
     */
    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // New columns on library_shows
            db.execSQL("ALTER TABLE library_shows ADD COLUMN recordingQuality INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE library_shows ADD COLUMN playingQuality INTEGER DEFAULT NULL")

            // Track reviews table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS track_reviews (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    showId TEXT NOT NULL,
                    trackTitle TEXT NOT NULL,
                    trackNumber INTEGER,
                    recordingId TEXT,
                    thumbs INTEGER,
                    starRating INTEGER,
                    notes TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY (showId) REFERENCES shows(showId) ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_track_reviews_showId_trackTitle_recordingId ON track_reviews(showId, trackTitle, recordingId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_track_reviews_showId ON track_reviews(showId)")

            // Show player tags table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS show_player_tags (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    showId TEXT NOT NULL,
                    playerName TEXT NOT NULL,
                    instruments TEXT,
                    isStandout INTEGER NOT NULL DEFAULT 1,
                    notes TEXT,
                    createdAt INTEGER NOT NULL,
                    FOREIGN KEY (showId) REFERENCES shows(showId) ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_show_player_tags_showId_playerName ON show_player_tags(showId, playerName)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_show_player_tags_showId ON show_player_tags(showId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_show_player_tags_playerName ON show_player_tags(playerName)")
        }
    }

    /**
     * v16 → v17: Decouple show reviews from library_shows.
     *
     * Creates a standalone show_reviews table referencing shows(showId) with CASCADE delete.
     * Migrates existing review data from library_shows columns. Old columns remain on
     * library_shows (SQLite doesn't support DROP COLUMN easily) but are no longer read/written.
     */
    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS show_reviews (
                    showId TEXT PRIMARY KEY NOT NULL,
                    notes TEXT,
                    customRating REAL,
                    recordingQuality INTEGER,
                    playingQuality INTEGER,
                    reviewedRecordingId TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY (showId) REFERENCES shows(showId) ON DELETE CASCADE
                )
            """.trimIndent())

            // Migrate existing review data from library_shows
            db.execSQL("""
                INSERT OR IGNORE INTO show_reviews (showId, notes, customRating, recordingQuality, playingQuality, createdAt, updatedAt)
                SELECT showId, libraryNotes, customRating, recordingQuality, playingQuality,
                       CAST(strftime('%s','now') AS INTEGER) * 1000,
                       CAST(strftime('%s','now') AS INTEGER) * 1000
                FROM library_shows
                WHERE libraryNotes IS NOT NULL OR customRating IS NOT NULL
                   OR recordingQuality IS NOT NULL OR playingQuality IS NOT NULL
            """.trimIndent())
        }
    }
}
