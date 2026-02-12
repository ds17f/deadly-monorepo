package com.grateful.deadly.core.home.di

import com.grateful.deadly.core.api.home.HomeService
import com.grateful.deadly.core.home.HomeServiceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Home service implementations.
 * 
 * Provides production HomeService implementation with real data integration.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HomeModule {
    
    /**
     * Binds the production HomeServiceImpl to the HomeService interface.
     * Provides real recent shows tracking, database-driven history, and curated collections.
     */
    @Binds
    @Singleton
    abstract fun bindHomeService(
        impl: HomeServiceImpl
    ): HomeService
}