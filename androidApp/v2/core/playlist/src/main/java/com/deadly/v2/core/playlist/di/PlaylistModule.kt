package com.deadly.v2.core.playlist.di

import com.deadly.v2.core.api.playlist.PlaylistService
import com.deadly.v2.core.playlist.service.PlaylistServiceImpl
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
 * PlaylistModule - Hilt dependency injection for Playlist
 * 
 * Updated to use real PlaylistServiceImpl with V2 domain architecture.
 * Uses SingletonComponent to access ShowRepository from database layer.
 * Provides application-scoped CoroutineScope for background prefetch operations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlaylistModule {

    @Binds
    abstract fun bindPlaylistService(
        impl: PlaylistServiceImpl
    ): PlaylistService
    
    companion object {
        
        @Provides
        @Singleton
        @Named("PlaylistApplicationScope")
        fun providePlaylistApplicationScope(): CoroutineScope {
            return CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }
    }
}