package com.grateful.deadly.core.search.di

import com.grateful.deadly.core.api.search.SearchService
import com.grateful.deadly.core.search.SearchServiceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Search implementations.
 * 
 * Provides real FTS search service implementation using Room database.
 * Replaced stub-first approach with production-ready FTS5 search.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SearchModule {
    
    /**
     * Binds the real SearchServiceImpl to the SearchService interface.
     * Uses FTS5 full-text search against the Room database.
     */
    @Binds
    @Singleton
    abstract fun bindSearchService(
        impl: SearchServiceImpl
    ): SearchService
}