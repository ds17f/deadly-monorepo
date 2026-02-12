package com.grateful.deadly.core.media.di

import android.content.Context
import com.grateful.deadly.core.media.repository.MediaControllerRepository
import com.grateful.deadly.core.network.archive.service.ArchiveService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for V2 media components
 */
@Module
@InstallIn(SingletonComponent::class)
object MediaModule {
    
    @Provides
    @Singleton
    fun provideMediaControllerRepository(
        @ApplicationContext context: Context,
        archiveService: ArchiveService
    ): MediaControllerRepository {
        return MediaControllerRepository(context, archiveService)
    }
}