package com.grateful.deadly.core.connect.di

import android.content.Context
import com.grateful.deadly.core.api.auth.AuthService
import com.grateful.deadly.core.api.connect.ConnectService
import com.grateful.deadly.core.api.player.PlayerService
import com.grateful.deadly.core.api.playlist.PlaylistService
import com.grateful.deadly.core.connect.ConnectPlaybackBridge
import com.grateful.deadly.core.connect.ConnectServiceImpl
import com.grateful.deadly.core.database.AppPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ConnectModule {

    @Provides
    @Singleton
    fun provideConnectServiceImpl(
        @ApplicationContext context: Context,
        authService: AuthService,
        appPreferences: AppPreferences,
    ): ConnectServiceImpl = ConnectServiceImpl(
        context = context,
        authService = authService,
        appPreferences = appPreferences,
    )

    @Provides
    @Singleton
    fun provideConnectService(impl: ConnectServiceImpl): ConnectService = impl

    @Provides
    @Singleton
    fun provideConnectPlaybackBridge(
        connectService: ConnectService,
        playerService: PlayerService,
        playlistService: PlaylistService,
    ): ConnectPlaybackBridge = ConnectPlaybackBridge(
        connectService = connectService,
        playerService = playerService,
        playlistService = playlistService,
    ).also { bridge ->
        // Start the bridge — wire up local ↔ remote once Hilt provides all deps
        bridge.start(connectService as ConnectServiceImpl)
    }
}
