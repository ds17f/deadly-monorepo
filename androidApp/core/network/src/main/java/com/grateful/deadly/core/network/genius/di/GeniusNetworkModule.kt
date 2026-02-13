package com.grateful.deadly.core.network.genius.di

import com.grateful.deadly.core.network.genius.api.GeniusApi
import com.grateful.deadly.core.network.genius.service.GeniusService
import com.grateful.deadly.core.network.genius.service.GeniusServiceImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Genius

@Module
@InstallIn(SingletonComponent::class)
abstract class GeniusNetworkModule {

    @Binds
    @Singleton
    abstract fun bindGeniusService(
        impl: GeniusServiceImpl
    ): GeniusService

    companion object {

        @Provides
        @Singleton
        @Genius
        fun provideGeniusOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                })
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        @Provides
        @Singleton
        @Genius
        fun provideGeniusRetrofit(@Genius okHttpClient: OkHttpClient): Retrofit {
            val json = Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
            return Retrofit.Builder()
                .baseUrl("https://api.genius.com/")
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
        }

        @Provides
        @Singleton
        fun provideGeniusApi(@Genius retrofit: Retrofit): GeniusApi {
            return retrofit.create(GeniusApi::class.java)
        }
    }
}
