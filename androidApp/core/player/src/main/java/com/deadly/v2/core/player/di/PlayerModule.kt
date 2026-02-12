package com.deadly.v2.core.player.di

import com.deadly.v2.core.api.player.PlayerService
import com.deadly.v2.core.player.service.PlayerServiceImpl
import com.deadly.v2.core.player.service.ShareService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * V2 Player Hilt DI Module
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
     * Enables clean dependency injection throughout V2 feature modules
     */
    @Binds
    abstract fun bindPlayerService(
        playerServiceImpl: PlayerServiceImpl
    ): PlayerService
}