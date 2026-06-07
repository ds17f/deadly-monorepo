package com.grateful.deadly.core.notifications.di

import com.grateful.deadly.core.notifications.NotificationApiService
import com.grateful.deadly.core.notifications.NotificationApiServiceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {

    @Binds
    @Singleton
    abstract fun bindNotificationApiService(
        impl: NotificationApiServiceImpl,
    ): NotificationApiService
}
