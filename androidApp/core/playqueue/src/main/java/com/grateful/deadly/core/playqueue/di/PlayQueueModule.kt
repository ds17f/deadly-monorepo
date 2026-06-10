package com.grateful.deadly.core.playqueue.di

import com.grateful.deadly.core.api.playqueue.PlayQueueService
import com.grateful.deadly.core.playqueue.service.PlayQueueServiceImpl
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

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayQueueModule {

    @Binds
    @Singleton
    abstract fun bindPlayQueueService(impl: PlayQueueServiceImpl): PlayQueueService

    companion object {
        @Provides
        @Singleton
        @Named("PlayQueueApplicationScope")
        fun providePlayQueueApplicationScope(): CoroutineScope =
            CoroutineScope(Dispatchers.IO + SupervisorJob())
    }
}
