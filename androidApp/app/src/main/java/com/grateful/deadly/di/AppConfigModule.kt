package com.grateful.deadly.di

import com.grateful.deadly.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object AppConfigModule {

    @Provides
    @Named("geniusAccessToken")
    fun provideGeniusAccessToken(): String = BuildConfig.GENIUS_ACCESS_TOKEN
}
