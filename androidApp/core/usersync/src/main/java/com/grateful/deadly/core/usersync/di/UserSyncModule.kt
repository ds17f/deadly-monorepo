package com.grateful.deadly.core.usersync.di

import com.grateful.deadly.core.api.usersync.UserSyncService
import com.grateful.deadly.core.usersync.UserSyncServiceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UserSyncModule {

    @Binds
    @Singleton
    abstract fun bindUserSyncService(impl: UserSyncServiceImpl): UserSyncService
}
