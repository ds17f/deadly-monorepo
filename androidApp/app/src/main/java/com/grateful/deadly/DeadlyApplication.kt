package com.grateful.deadly

import android.app.Application
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import com.grateful.deadly.core.api.usersync.FavoritesPushService
import com.grateful.deadly.core.database.AppPreferences
import com.grateful.deadly.core.media.download.DeadlyMediaDownloadServiceHost
import com.grateful.deadly.core.usersync.UserSyncCoordinator
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@UnstableApi
@HiltAndroidApp
class DeadlyApplication : Application(), DeadlyMediaDownloadServiceHost {

    @Inject
    lateinit var _downloadManager: DownloadManager

    @Inject
    lateinit var _downloadNotificationHelper: DownloadNotificationHelper

    @Inject
    lateinit var userSyncCoordinator: UserSyncCoordinator

    @Inject
    lateinit var favoritesPushService: FavoritesPushService

    @Inject
    lateinit var appPreferences: AppPreferences

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun getMediaDownloadManager(): DownloadManager = _downloadManager

    override fun getDownloadNotificationHelper(): DownloadNotificationHelper = _downloadNotificationHelper

    override fun onCreate() {
        super.onCreate()
        userSyncCoordinator.start()

        // One-time: push all pre-existing local data (favorites + top recents)
        // to the server so devices that predate granular push aren't stranded.
        if (!appPreferences.getLocalBackfilledV1()) {
            appPreferences.setLocalBackfilledV1(true)
            appScope.launch {
                favoritesPushService.enqueueAllLocalAndFlush()
            }
        }
    }
}
