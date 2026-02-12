package com.deadly.v2.core.library.di

import com.deadly.v2.core.api.library.LibraryService
import com.deadly.v2.core.library.service.LibraryServiceImpl
import com.deadly.v2.core.library.repository.LibraryRepository
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
 * V2 LibraryModule - Hilt dependency injection for V2 Library services
 * 
 * Following V2 architecture patterns with service-oriented design.
 * Uses SingletonComponent for application-scoped services with the
 * real LibraryServiceImpl implementation.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryModule {

    @Binds
    @Singleton  
    abstract fun bindLibraryService(
        impl: LibraryServiceImpl
    ): LibraryService
    
    companion object {
        
        /**
         * Provide application-scoped CoroutineScope for LibraryService operations
         */
        @Provides
        @Singleton
        @Named("LibraryApplicationScope")
        fun provideLibraryApplicationScope(): CoroutineScope {
            return CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }
    }
}