package com.grateful.deadly.core.library.di

import com.grateful.deadly.core.api.library.LibraryService
import com.grateful.deadly.core.library.service.LibraryServiceImpl
import com.grateful.deadly.core.library.repository.LibraryRepository
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
 * LibraryModule - Hilt dependency injection for Library services
 * 
 * Following architecture patterns with service-oriented design.
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