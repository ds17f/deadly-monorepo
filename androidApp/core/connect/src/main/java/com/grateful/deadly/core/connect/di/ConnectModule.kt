package com.grateful.deadly.core.connect.di

import com.grateful.deadly.core.connect.ConnectService
import com.grateful.deadly.core.connect.ConnectServiceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ConnectModule {

    @Binds
    @Singleton
    abstract fun bindConnectService(impl: ConnectServiceImpl): ConnectService
}
