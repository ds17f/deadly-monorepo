package com.deadly.v2.core.recent.di

import com.deadly.v2.core.api.recent.RecentShowsService
import com.deadly.v2.core.recent.service.RecentShowsServiceImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module for RecentShows service dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RecentModule {
    
    /**
     * Bind RecentShowsService interface to implementation
     */
    @Binds
    @Singleton
    abstract fun bindRecentShowsService(
        impl: RecentShowsServiceImpl
    ): RecentShowsService
    
    companion object {
        /**
         * Provide dedicated CoroutineScope for RecentShows operations
         * Uses IO dispatcher for database operations
         */
        @Provides
        @Singleton
        @Named("RecentShowsApplicationScope")
        fun provideRecentShowsApplicationScope(): CoroutineScope {
            return CoroutineScope(Dispatchers.IO + SupervisorJob())
        }
    }
}