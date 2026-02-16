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
}
