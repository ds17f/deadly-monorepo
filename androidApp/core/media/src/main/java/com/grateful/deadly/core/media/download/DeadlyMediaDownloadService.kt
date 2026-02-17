package com.grateful.deadly.core.media.download

import android.app.Notification
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import com.grateful.deadly.core.media.R

@UnstableApi
class DeadlyMediaDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DownloadCacheModule.DOWNLOAD_NOTIFICATION_CHANNEL_ID,
    R.string.download_channel_name,
    R.string.download_channel_description
) {

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 2
        private const val JOB_ID = 1
    }

    override fun getDownloadManager(): DownloadManager {
        val app = application as DeadlyMediaDownloadServiceHost
        return app.getMediaDownloadManager()
    }

    override fun getScheduler(): Scheduler {
        return PlatformScheduler(this, JOB_ID)
    }

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notRequirements: Int
    ): Notification {
        val app = application as DeadlyMediaDownloadServiceHost
        val helper = app.getDownloadNotificationHelper()
        return helper.buildProgressNotification(
            this,
            R.drawable.ic_download,
            null,
            null,
            downloads,
            notRequirements
        )
    }
}

/**
 * Interface that Application must implement to provide download dependencies.
 * This avoids the Hilt injection limitation with Media3 DownloadService.
 */
interface DeadlyMediaDownloadServiceHost {
    fun getMediaDownloadManager(): DownloadManager
    fun getDownloadNotificationHelper(): DownloadNotificationHelper
}
