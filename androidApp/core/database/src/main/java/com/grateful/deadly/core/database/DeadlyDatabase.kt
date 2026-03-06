package com.grateful.deadly.core.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.grateful.deadly.core.database.migration.DatabaseMigrations
import com.grateful.deadly.core.database.entities.ShowEntity
import com.grateful.deadly.core.database.entities.ShowSearchEntity
import com.grateful.deadly.core.database.entities.RecordingEntity
import com.grateful.deadly.core.database.entities.DataVersionEntity
import com.grateful.deadly.core.database.entities.LibraryShowEntity
import com.grateful.deadly.core.database.entities.RecentShowEntity
import com.grateful.deadly.core.database.entities.DeadCollectionEntity
import com.grateful.deadly.core.database.entities.TrackReviewEntity
import com.grateful.deadly.core.database.entities.ShowPlayerTagEntity
import com.grateful.deadly.core.database.entities.ShowReviewEntity
import com.grateful.deadly.core.database.entities.RecordingPreferenceEntity
import com.grateful.deadly.core.database.dao.ShowDao
import com.grateful.deadly.core.database.dao.ShowSearchDao
import com.grateful.deadly.core.database.dao.RecordingDao
import com.grateful.deadly.core.database.dao.DataVersionDao
import com.grateful.deadly.core.database.dao.LibraryDao
import com.grateful.deadly.core.database.dao.RecentShowDao
import com.grateful.deadly.core.database.dao.CollectionsDao
import com.grateful.deadly.core.database.dao.TrackReviewDao
import com.grateful.deadly.core.database.dao.ShowPlayerTagDao
import com.grateful.deadly.core.database.dao.ShowReviewDao
import com.grateful.deadly.core.database.dao.RecordingPreferenceDao

@Database(
    entities = [
        ShowEntity::class,
        ShowSearchEntity::class,
        RecordingEntity::class,
        DataVersionEntity::class,
        LibraryShowEntity::class,
        RecentShowEntity::class,
        DeadCollectionEntity::class,
        TrackReviewEntity::class,
        ShowPlayerTagEntity::class,
        ShowReviewEntity::class,
        RecordingPreferenceEntity::class
    ],
    version = 18,
    exportSchema = false
)
abstract class DeadlyDatabase : RoomDatabase() {
    
    abstract fun showDao(): ShowDao
    abstract fun showSearchDao(): ShowSearchDao
    abstract fun recordingDao(): RecordingDao
    abstract fun dataVersionDao(): DataVersionDao
    abstract fun libraryDao(): LibraryDao
    abstract fun recentShowDao(): RecentShowDao
    abstract fun collectionsDao(): CollectionsDao
    abstract fun trackReviewDao(): TrackReviewDao
    abstract fun showPlayerTagDao(): ShowPlayerTagDao
    abstract fun showReviewDao(): ShowReviewDao
    abstract fun recordingPreferenceDao(): RecordingPreferenceDao
    
    companion object {
        const val DATABASE_NAME = "deadly_db"
        
        fun create(context: Context): DeadlyDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                DeadlyDatabase::class.java,
                DATABASE_NAME
            )
            .addMigrations(DatabaseMigrations.MIGRATION_12_13, DatabaseMigrations.MIGRATION_13_14, DatabaseMigrations.MIGRATION_14_15, DatabaseMigrations.MIGRATION_15_16, DatabaseMigrations.MIGRATION_16_17, DatabaseMigrations.MIGRATION_17_18)
            .fallbackToDestructiveMigration() // Safety net for fresh installs or skipped versions
            .build()
        }
    }
}