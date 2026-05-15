package com.grateful.deadly

import android.app.Application
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.grateful.deadly.core.media.download.DeadlyMediaDownloadServiceHost
import com.grateful.deadly.core.network.hermetic.BaseOkHttp
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

@UnstableApi
@HiltAndroidApp
class DeadlyApplication : Application(), DeadlyMediaDownloadServiceHost, SingletonImageLoader.Factory {

    @Inject
    lateinit var _downloadManager: DownloadManager

    @Inject
    lateinit var _downloadNotificationHelper: DownloadNotificationHelper

    @Inject
    @BaseOkHttp
    lateinit var baseHttpClient: Lazy<OkHttpClient>

    override fun getMediaDownloadManager(): DownloadManager = _downloadManager

    override fun getDownloadNotificationHelper(): DownloadNotificationHelper = _downloadNotificationHelper

    /**
     * Provide Coil's singleton ImageLoader using our shared @BaseOkHttp client so
     * the HermeticInterceptor catches image fetches (ticket artwork, show
     * covers, etc.) when hermetic mode is on. Lazy so the OkHttp client isn't
     * built during Application.onCreate().
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { baseHttpClient.get() }))
            }
            .build()

    override fun onCreate() {
        super.onCreate()
    }
}
