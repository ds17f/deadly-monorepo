package com.deadly.v2.core.network.archive.di

import com.deadly.v2.core.network.archive.api.ArchiveApiService
import com.deadly.v2.core.network.archive.service.ArchiveService
import com.deadly.v2.core.network.archive.service.ArchiveServiceImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier for Archive.org Retrofit instance
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ArchiveRetrofit

/**
 * Hilt module for V2 Archive service dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ArchiveModule {
    
    @Binds
    @Singleton
    abstract fun bindArchiveService(
        impl: ArchiveServiceImpl
    ): ArchiveService
    
    companion object {
        
        @Provides
        @Singleton
        @ArchiveRetrofit
        fun provideArchiveRetrofit(): Retrofit {
            val json = Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
            
            return Retrofit.Builder()
                .baseUrl("https://archive.org/")
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
        }
        
        @Provides
        @Singleton
        fun provideArchiveApiService(
            @ArchiveRetrofit retrofit: Retrofit
        ): ArchiveApiService {
            return retrofit.create(ArchiveApiService::class.java)
        }
    }
}