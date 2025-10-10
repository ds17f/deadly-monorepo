package com.deadly.v2.core.database.service

import android.util.Log
import com.deadly.v2.core.model.V2Database
import com.deadly.v2.core.database.dao.ShowDao
import com.deadly.v2.core.database.dao.RecordingDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseHealthService @Inject constructor(
    @V2Database private val showDao: ShowDao,
    @V2Database private val recordingDao: RecordingDao
) {
    
    companion object {
        private const val TAG = "DatabaseHealthService"
    }
    
    /**
     * Check if the database is healthy (contains data)
     * Database is considered healthy if both show and recording tables have records
     */
    suspend fun isDatabaseHealthy(): Boolean {
        return try {
            Log.d(TAG, "Checking database health...")
            val showCount = showDao.getShowCount()
            val recordingCount = recordingDao.getRecordingCount()
            
            Log.d(TAG, "Database health check: $showCount shows, $recordingCount recordings")
            
            val isHealthy = showCount > 0 && recordingCount > 0
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