package com.grateful.deadly.core.database.service

import android.util.Log
import com.grateful.deadly.core.model.AppDatabase
import com.grateful.deadly.core.database.dao.DataVersionDao
import com.grateful.deadly.core.database.dao.ShowDao
import com.grateful.deadly.core.database.dao.RecordingDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseHealthService @Inject constructor(
    @AppDatabase private val showDao: ShowDao,
    @AppDatabase private val recordingDao: RecordingDao,
    @AppDatabase private val dataVersionDao: DataVersionDao
) {
    
    companion object {
        private const val TAG = "DatabaseHealthService"
    }
    
    /**
     * Check if the database is healthy (fully initialized).
     *
     * Gates on a committed `data_version` row — both import paths write it LAST
     * (after all shows/recordings/FTS), so its presence proves the import ran to
     * completion. Counting rows alone is NOT enough: an import killed mid-recordings
     * leaves shows>0 && recordings>0 but a permanently partial catalog. Requiring the
     * end-written marker closes that silent-incomplete bug (ADR-0013).
     */
    suspend fun isDatabaseHealthy(): Boolean {
        return try {
            Log.d(TAG, "Checking database health...")
            val hasDataVersion = dataVersionDao.hasDataVersion()
            val showCount = showDao.getShowCount()
            val recordingCount = recordingDao.getRecordingCount()

            Log.d(TAG, "Database health check: dataVersion=$hasDataVersion, $showCount shows, $recordingCount recordings")

            val isHealthy = hasDataVersion && showCount > 0 && recordingCount > 0
            Log.d(TAG, if (isHealthy) "✅ Database is healthy" else "❌ Database is empty or incomplete")

            isHealthy
        } catch (e: Exception) {
            Log.e(TAG, "❌ Database health check failed - tables may not exist", e)
            false
        }
    }
    
    /**
     * Get database record counts
     */
    suspend fun getDatabaseCounts(): DatabaseCounts {
        return try {
            val showCount = showDao.getShowCount()
            val recordingCount = recordingDao.getRecordingCount()
            
            Log.d(TAG, "Database counts: $showCount shows, $recordingCount recordings")
            DatabaseCounts(showCount, recordingCount)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get database counts", e)
            DatabaseCounts(0, 0)
        }
    }
}

/**
 * Database count information
 */
data class DatabaseCounts(
    val showCount: Int,
    val recordingCount: Int
)