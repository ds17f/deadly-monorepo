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
}
