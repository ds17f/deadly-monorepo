package com.grateful.deadly.core.player.di

import com.grateful.deadly.core.api.player.PanelContentService
import com.grateful.deadly.core.api.player.PlayerService
import com.grateful.deadly.core.player.service.PanelContentServiceImpl
import com.grateful.deadly.core.player.service.PlayerServiceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {

    @Binds
    abstract fun bindPlayerService(
        playerServiceImpl: PlayerServiceImpl
    ): PlayerService

    @Binds
    abstract fun bindPanelContentService(
        panelContentServiceImpl: PanelContentServiceImpl
    ): PanelContentService
}