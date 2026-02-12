package com.deadly.v2.core.network.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module(includes = [com.deadly.v2.core.network.github.di.GitHubNetworkModule::class])
@InstallIn(SingletonComponent::class)
object NetworkModule