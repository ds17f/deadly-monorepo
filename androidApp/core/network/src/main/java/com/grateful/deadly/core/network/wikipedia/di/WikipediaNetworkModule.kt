package com.grateful.deadly.core.network.wikipedia.di

import com.grateful.deadly.core.network.wikipedia.api.WikipediaApi
import com.grateful.deadly.core.network.wikipedia.service.WikipediaService
import com.grateful.deadly.core.network.wikipedia.service.WikipediaServiceImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Wikipedia

@Module
@InstallIn(SingletonComponent::class)
abstract class WikipediaNetworkModule {

    @Binds
    @Singleton
    abstract fun bindWikipediaService(
        impl: WikipediaServiceImpl
    ): WikipediaService

    companion object {

        @Provides
        @Singleton
        @Wikipedia
        fun provideWikipediaRetrofit(): Retrofit {
            val json = Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", "DeadlyApp/1.0 (https://github.com/ds17f/deadly-monorepo)")
                            .build()
                    )
                })
                .build()
            return Retrofit.Builder()
                .baseUrl("https://en.wikipedia.org/")
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
        }

        @Provides
        @Singleton
        fun provideWikipediaApi(@Wikipedia retrofit: Retrofit): WikipediaApi {
            return retrofit.create(WikipediaApi::class.java)
        }
    }
}
