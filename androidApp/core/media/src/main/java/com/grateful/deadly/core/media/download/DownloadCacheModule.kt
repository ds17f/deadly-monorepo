package com.grateful.deadly.core.media.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.Executor
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadCache

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UpstreamDataSourceFactory

@UnstableApi
@Module
@InstallIn(SingletonComponent::class)
object DownloadCacheModule {

    const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "deadly_show_downloads"
    private const val DOWNLOAD_CACHE_DIR = "media_downloads"
    private const val USER_AGENT = "DeadlyApp/2.0 (Android)"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val MAX_PARALLEL_DOWNLOADS = 2

    @Provides
    @Singleton
    fun provideStandaloneDatabaseProvider(
        @ApplicationContext context: Context
    ): StandaloneDatabaseProvider {
        return StandaloneDatabaseProvider(context)
    }

    @Provides
    @Singleton
    @DownloadCache
    fun provideDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: StandaloneDatabaseProvider
    ): Cache {
        val cacheDir = File(context.filesDir, DOWNLOAD_CACHE_DIR)
        return SimpleCache(cacheDir, NoOpCacheEvictor(), databaseProvider)
    }

    @Provides
    @Singleton
    @UpstreamDataSourceFactory
    fun provideUpstreamDataSourceFactory(): DataSource.Factory {
        return DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(READ_TIMEOUT_MS)
    }

    @Provides
    @Singleton
    fun provideCacheDataSourceFactory(
        @DownloadCache cache: Cache,
        @UpstreamDataSourceFactory upstreamFactory: DataSource.Factory
    ): CacheDataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    @Provides
    @Singleton
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        @DownloadCache cache: Cache,
        @UpstreamDataSourceFactory upstreamFactory: DataSource.Factory,
        databaseProvider: StandaloneDatabaseProvider
    ): DownloadManager {
        return DownloadManager(
            context,
            databaseProvider,
            cache,
            upstreamFactory,
            Executor { it.run() }
        ).apply {
            maxParallelDownloads = MAX_PARALLEL_DOWNLOADS
        }
    }

    @Provides
    @Singleton
    fun provideDownloadNotificationHelper(
        @ApplicationContext context: Context
    ): DownloadNotificationHelper {
        createNotificationChannel(context)
        return DownloadNotificationHelper(context, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                "Show Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for offline show downloads"
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
