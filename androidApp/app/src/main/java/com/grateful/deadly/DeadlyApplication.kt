package com.grateful.deadly

import android.app.Application
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import com.grateful.deadly.core.connect.ConnectPlaybackHandler
import com.grateful.deadly.core.media.download.DeadlyMediaDownloadServiceHost
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@UnstableApi
@HiltAndroidApp
class DeadlyApplication : Application(), DeadlyMediaDownloadServiceHost {

    @Inject
    lateinit var _downloadManager: DownloadManager

    @Inject
    lateinit var _downloadNotificationHelper: DownloadNotificationHelper

    @Inject
    lateinit var _connectPlaybackHandler: ConnectPlaybackHandler

    override fun getMediaDownloadManager(): DownloadManager = _downloadManager

    override fun getDownloadNotificationHelper(): DownloadNotificationHelper = _downloadNotificationHelper

    override fun onCreate() {
        super.onCreate()
    }
}
