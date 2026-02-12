package com.grateful.deadly.core.miniplayer.di

import com.grateful.deadly.core.api.miniplayer.MiniPlayerService
import com.grateful.deadly.core.miniplayer.service.MiniPlayerServiceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * V2 MiniPlayer Hilt DI Module
 * 
 * Provides dependency injection configuration for MiniPlayer services.
 * Binds MiniPlayerService interface to real implementation.
 * 
 * MiniPlayerServiceImpl now uses direct MediaControllerRepository delegation
 * instead of the removed PlaybackStateService abstraction layer.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MiniPlayerModule {
    
    /**
     * Bind MiniPlayerService interface to real implementation
     * Enables clean dependency injection throughout V2 feature modules
     */
    @Binds
    abstract fun bindMiniPlayerService(
        miniPlayerServiceImpl: MiniPlayerServiceImpl
    ): MiniPlayerService
}