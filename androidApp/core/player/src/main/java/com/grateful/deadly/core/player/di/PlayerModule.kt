package com.grateful.deadly.core.player.di

import com.grateful.deadly.core.api.player.PlayerService
import com.grateful.deadly.core.player.service.PlayerServiceImpl
import com.grateful.deadly.core.player.service.ShareService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Player Hilt DI Module
 * 
 * Provides dependency injection configuration for Player services.
 * Binds PlayerService interface to real implementation.
 * 
 * Follows the same pattern as MiniPlayerModule.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {
    
    /**
     * Bind PlayerService interface to real implementation
     * Enables clean dependency injection throughout feature modules
     */
    @Binds
    abstract fun bindPlayerService(
        playerServiceImpl: PlayerServiceImpl
    ): PlayerService
}