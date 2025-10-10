package com.grateful.deadly

import android.app.Application
import com.deadly.v2.core.database.service.DatabaseManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class DeadlyApplication : Application() {

    @Inject
    lateinit var v2DatabaseManager: DatabaseManager

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        // Initialize V2 database
        applicationScope.launch {
            try {
                android.util.Log.d("DeadlyApplication", "Initializing V2 database...")
                val result = v2DatabaseManager.initializeV2DataIfNeeded()
                when (result) {
                    is com.deadly.v2.core.database.service.DatabaseImportResult.Success -> {
                        android.util.Log.d(
                            "DeadlyApplication",
                            "✅ V2 database initialized: ${result.showsImported} shows, ${result.venuesImported} venues"
                        )
                    }
                    is com.deadly.v2.core.database.service.DatabaseImportResult.Error -> {
                        android.util.Log.e(
                            "DeadlyApplication",
                            "❌ V2 database initialization failed: ${result.error}"
                        )
                    }
                    is com.deadly.v2.core.database.service.DatabaseImportResult.RequiresUserChoice -> {
                        android.util.Log.d(
                            "DeadlyApplication",
                            "V2 database requires user choice - will be handled by splash screen"
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DeadlyApplication", "❌ Failed to initialize V2 database", e)
            }
        }
    }
}
