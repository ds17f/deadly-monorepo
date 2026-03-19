package com.grateful.deadly.core.auth.di

import android.content.Context
import com.grateful.deadly.core.api.auth.AuthService
import com.grateful.deadly.core.auth.AuthServiceImpl
import com.grateful.deadly.core.database.AppPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideAuthService(
        @ApplicationContext context: Context,
        appPreferences: AppPreferences,
        @Named("googleAndroidClientId") googleClientId: String,
    ): AuthService = AuthServiceImpl(
        context = context,
        appPreferences = appPreferences,
        googleClientId = googleClientId,
    )
}
