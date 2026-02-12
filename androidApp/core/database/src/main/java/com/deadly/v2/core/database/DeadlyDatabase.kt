package com.deadly.v2.core.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.deadly.v2.core.database.entities.ShowEntity
import com.deadly.v2.core.database.entities.ShowSearchEntity
import com.deadly.v2.core.database.entities.RecordingEntity
import com.deadly.v2.core.database.entities.DataVersionEntity
import com.deadly.v2.core.database.entities.LibraryShowEntity
import com.deadly.v2.core.database.entities.RecentShowEntity
import com.deadly.v2.core.database.entities.DeadCollectionEntity
import com.deadly.v2.core.database.dao.ShowDao
import com.deadly.v2.core.database.dao.ShowSearchDao
import com.deadly.v2.core.database.dao.RecordingDao
import com.deadly.v2.core.database.dao.DataVersionDao
import com.deadly.v2.core.database.dao.LibraryDao
import com.deadly.v2.core.database.dao.RecentShowDao
import com.deadly.v2.core.database.dao.CollectionsDao

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
    version = 11,
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