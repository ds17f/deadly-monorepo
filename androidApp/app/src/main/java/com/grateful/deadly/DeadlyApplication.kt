package com.grateful.deadly

import android.app.Application
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import com.grateful.deadly.core.database.service.DatabaseManager
import com.grateful.deadly.core.media.download.DeadlyMediaDownloadServiceHost
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
    lateinit var databaseManager: DatabaseManager

    @Inject
    lateinit var _downloadManager: DownloadManager

    @Inject
    lateinit var _downloadNotificationHelper: DownloadNotificationHelper

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun getMediaDownloadManager(): DownloadManager = _downloadManager

    override fun getDownloadNotificationHelper(): DownloadNotificationHelper = _downloadNotificationHelper

    override fun onCreate() {
        super.onCreate()

        // Initialize database
        applicationScope.launch {
            try {
                android.util.Log.d("DeadlyApplication", "Initializing database...")
                val result = databaseManager.initializeDataIfNeeded()
                when (result) {
                    is com.grateful.deadly.core.database.service.DatabaseImportResult.Success -> {
                        android.util.Log.d(
                            "DeadlyApplication",
                            "✅ Database initialized: ${result.showsImported} shows, ${result.venuesImported} venues"
                        )
                    }
                    is com.grateful.deadly.core.database.service.DatabaseImportResult.Error -> {
                        android.util.Log.e(
                            "DeadlyApplication",
                            "❌ Database initialization failed: ${result.error}"
                        )
                    }
                    is com.grateful.deadly.core.database.service.DatabaseImportResult.RequiresUserChoice -> {
                        android.util.Log.d(
                            "DeadlyApplication",
                            "Database requires user choice - will be handled by splash screen"
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DeadlyApplication", "❌ Failed to initialize database", e)
            }
        }
    }
}
