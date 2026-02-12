package com.grateful.deadly.core.database.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.grateful.deadly.core.database.DeadlyDatabase
import com.grateful.deadly.core.model.V2Database
import com.grateful.deadly.core.database.dao.ShowDao
import com.grateful.deadly.core.database.dao.ShowSearchDao
import com.grateful.deadly.core.database.dao.RecordingDao
import com.grateful.deadly.core.database.dao.DataVersionDao
import com.grateful.deadly.core.database.dao.LibraryDao
import com.grateful.deadly.core.database.dao.RecentShowDao
import com.grateful.deadly.core.database.dao.CollectionsDao
import com.grateful.deadly.core.database.repository.ShowRepositoryImpl
import com.grateful.deadly.core.domain.repository.ShowRepository
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideDeadlyDatabase(@ApplicationContext context: Context): DeadlyDatabase {
        return DeadlyDatabase.create(context)
    }
    
    @Provides
    @V2Database
    fun provideShowDao(database: DeadlyDatabase): ShowDao {
        return database.showDao()
    }
    
    @Provides
    @V2Database
    fun provideShowSearchDao(database: DeadlyDatabase): ShowSearchDao {
        return database.showSearchDao()
    }
    
    @Provides
    @V2Database
    fun provideRecordingDao(database: DeadlyDatabase): RecordingDao {
        return database.recordingDao()
    }
    
    @Provides
    @V2Database
    fun provideDataVersionDao(database: DeadlyDatabase): DataVersionDao {
        return database.dataVersionDao()
    }
    
    @Provides
    @V2Database
    fun provideLibraryDao(database: DeadlyDatabase): LibraryDao {
        return database.libraryDao()
    }
    
    @Provides
    @V2Database
    fun provideRecentShowDao(database: DeadlyDatabase): RecentShowDao {
        return database.recentShowDao()
    }
    
    @Provides
    @V2Database
    fun provideCollectionsDao(database: DeadlyDatabase): CollectionsDao {
        return database.collectionsDao()
    }
    
    // Services are automatically provided by @Singleton @Inject constructor:
    // - DataImportService
    // - ShowMappers (uses Json from core:network module)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseBindsModule {
    
    @Binds
    abstract fun bindShowRepository(
        impl: ShowRepositoryImpl
    ): ShowRepository
}