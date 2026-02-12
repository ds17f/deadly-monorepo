package com.deadly.v2.core.collections.di

import com.deadly.v2.core.api.collections.DeadCollectionsService
import com.deadly.v2.core.collections.DeadCollectionsServiceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for DeadCollections service implementations.
 * 
 * Provides production DeadCollectionsServiceImpl with real data integration.
 * Uses ShowRepository to provide curated collections with actual show data.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DeadCollectionsModule {
    
    /**
     * Binds the production DeadCollectionsServiceImpl to the DeadCollectionsService interface.
     * Provides curated Grateful Dead collections with real show data from database.
     */
    @Binds
    @Singleton
    abstract fun bindDeadCollectionsService(
        impl: DeadCollectionsServiceImpl
    ): DeadCollectionsService
}