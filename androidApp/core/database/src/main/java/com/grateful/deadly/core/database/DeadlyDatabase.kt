package com.grateful.deadly.core.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.grateful.deadly.core.database.entities.ShowEntity
import com.grateful.deadly.core.database.entities.ShowSearchEntity
import com.grateful.deadly.core.database.entities.RecordingEntity
import com.grateful.deadly.core.database.entities.DataVersionEntity
import com.grateful.deadly.core.database.entities.LibraryShowEntity
import com.grateful.deadly.core.database.entities.RecentShowEntity
import com.grateful.deadly.core.database.entities.DeadCollectionEntity
import com.grateful.deadly.core.database.dao.ShowDao
import com.grateful.deadly.core.database.dao.ShowSearchDao
import com.grateful.deadly.core.database.dao.RecordingDao
import com.grateful.deadly.core.database.dao.DataVersionDao
import com.grateful.deadly.core.database.dao.LibraryDao
import com.grateful.deadly.core.database.dao.RecentShowDao
import com.grateful.deadly.core.database.dao.CollectionsDao

@Database(
    entities = [
        ShowEntity::class,
        ShowSearchEntity::class,
        RecordingEntity::class,
        DataVersionEntity::class,
        LibraryShowEntity::class,
        RecentShowEntity::class,
        DeadCollectionEntity::class
    ],
    version = 12,
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
    
    companion object {
        const val DATABASE_NAME = "deadly_db"
        
        fun create(context: Context): DeadlyDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                DeadlyDatabase::class.java,
                DATABASE_NAME
            )
            .fallbackToDestructiveMigration() // Clean rebuild for V2 simplification
            .build()
        }
    }
}