package com.deadly.v2.core.database.service

import android.content.Context
import android.util.Log
import com.deadly.v2.core.database.DeadlyDatabase
import com.deadly.v2.core.model.V2Database
import com.deadly.v2.core.database.dao.DataVersionDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for restoring database from backup ZIP files
 */
@Singleton
class DatabaseRestoreService @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val database: DeadlyDatabase,
    @V2Database private val dataVersionDao: DataVersionDao
) {
    companion object {
        private const val TAG = "DatabaseRestoreService"
        private const val DATABASE_FILE_NAME = "dead_archive_v2.db"
    }
    
    /**
     * Restore database from extracted backup file with progress tracking
     */
    suspend fun restoreFromBackup(
        extractedDatabaseFile: File,
        progressCallback: ((RestoreProgress) -> Unit)? = null
    ): RestoreResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting database restore from backup: ${extractedDatabaseFile.name}")
            
            progressCallback?.invoke(RestoreProgress("VALIDATING", 0, 100, "Validating backup file..."))
            
            if (!extractedDatabaseFile.exists()) {
                return@withContext RestoreResult.Error("Backup database file does not exist")
            }
            
            if (!extractedDatabaseFile.canRead()) {
                return@withContext RestoreResult.Error("Cannot read backup database file")
            }
            
            val backupFileSize = extractedDatabaseFile.length()
            if (backupFileSize == 0L) {
                return@withContext RestoreResult.Error("Backup database file is empty")
            }
            
            Log.d(TAG, "Backup file validation passed: ${backupFileSize} bytes")
            
            progressCallback?.invoke(RestoreProgress("PREPARING", 10, 100, "Preparing database..."))
            
            // Close existing database connections
            database.close()
            
            // Get the current database file path
            val currentDbFile = context.getDatabasePath(DATABASE_FILE_NAME)
            
            // Create backup of current database if it exists
            var currentBackupFile: File? = null
            if (currentDbFile.exists()) {
                currentBackupFile = File(context.filesDir, "current_db_backup.db")
                progressCallback?.invoke(RestoreProgress("BACKING_UP", 20, 100, "Backing up current database..."))
                
                try {
                    currentDbFile.copyTo(currentBackupFile, overwrite = true)
                    Log.d(TAG, "Created backup of current database")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to backup current database", e)
                    // Continue anyway - this is just a safety measure
                }
            }
            
            progressCallback?.invoke(RestoreProgress("COPYING", 30, 100, "Copying backup database..."))
            
            try {
                // Copy the backup database to replace current database
                copyFileWithProgress(
                    extractedDatabaseFile, 
                    currentDbFile,
                    progressStart = 30,
                    progressEnd = 80
                ) { progress ->
                    progressCallback?.invoke(RestoreProgress("COPYING", progress, 100, "Copying database..."))
                }
                
                Log.d(TAG, "Successfully copied backup database to: ${currentDbFile.absolutePath}")
                
                progressCallback?.invoke(RestoreProgress("VERIFYING", 80, 100, "Verifying restored database..."))
                
                // Verify the restored database
                val verification = verifyRestoredDatabase(currentDbFile)
                
                when (verification) {
                    is DatabaseVerification.Success -> {
                        // Clean up temporary backup
                        currentBackupFile?.delete()
                        
                        progressCallback?.invoke(RestoreProgress("COMPLETED", 100, 100, "Database restored successfully"))
                        
                        Log.d(TAG, "Database restore completed successfully: ${verification.showCount} shows, ${verification.recordingCount} recordings")
                        
                        RestoreResult.Success(verification.showCount, verification.recordingCount)
                    }
                    is DatabaseVerification.Error -> {
                        // Restore failed - restore original database if we have a backup
                        currentBackupFile?.let { backup ->
                            try {
                                backup.copyTo(currentDbFile, overwrite = true)
                                backup.delete()
                                Log.d(TAG, "Restored original database after failed verification")
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to restore original database", e)
                            }
                        }
                        
                        RestoreResult.Error("Database verification failed: ${verification.error}")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy backup database", e)
                
                // Restore original database if we have a backup
                currentBackupFile?.let { backup ->
                    try {
                        backup.copyTo(currentDbFile, overwrite = true)
                        backup.delete()
                        Log.d(TAG, "Restored original database after copy failure")
                    } catch (restoreException: Exception) {
                        Log.e(TAG, "Failed to restore original database", restoreException)
                    }
                }
                
                RestoreResult.Error("Failed to copy backup database: ${e.message}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Database restore failed", e)
            RestoreResult.Error("Restore failed: ${e.message}")
        }
    }
    
    /**
     * Copy file with progress tracking
     */
    private suspend fun copyFileWithProgress(
        source: File,
        destination: File,
        progressStart: Int,
        progressEnd: Int,
        progressCallback: ((Int) -> Unit)? = null
    ): Unit = withContext(Dispatchers.IO) {
        val totalBytes = source.length()
        var copiedBytes = 0L
        val buffer = ByteArray(8192)
        
        destination.parentFile?.mkdirs()
        
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                var bytesRead = input.read(buffer)
                while (bytesRead != -1) {
                    output.write(buffer, 0, bytesRead)
                    copiedBytes += bytesRead
                    
                    // Calculate progress within the given range
                    val fileProgress = if (totalBytes > 0) (copiedBytes.toFloat() / totalBytes.toFloat()) else 1f
                    val overallProgress = progressStart + ((progressEnd - progressStart) * fileProgress).toInt()
                    progressCallback?.invoke(overallProgress)
                    
                    bytesRead = input.read(buffer)
                }
            }
        }
    }
    
    /**
     * Verify that the restored database is valid and accessible
     */
    private suspend fun verifyRestoredDatabase(databaseFile: File): DatabaseVerification = withContext(Dispatchers.IO) {
        try {
            // Try to open and read from the database
            val tempDatabase = DeadlyDatabase.create(context)
            
            try {
                val showCount = tempDatabase.showDao().getShowCount()
                val recordingCount = tempDatabase.recordingDao().getRecordingCount()
                
                if (showCount == 0 && recordingCount == 0) {
                    DatabaseVerification.Error("Restored database appears to be empty")
                } else {
                    Log.d(TAG, "Database verification successful: $showCount shows, $recordingCount recordings")
                    DatabaseVerification.Success(showCount, recordingCount)
                }
            } finally {
                tempDatabase.close()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Database verification failed", e)
            DatabaseVerification.Error("Database verification failed: ${e.message}")
        }
    }
    
    /**
     * Validate backup file before attempting restore
     */
    suspend fun validateBackupFile(backupFile: File): ValidationResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Validating backup file: ${backupFile.name}")
            
            if (!backupFile.exists()) {
                return@withContext ValidationResult.Error("Backup file does not exist")
            }
            
            if (!backupFile.canRead()) {
                return@withContext ValidationResult.Error("Cannot read backup file")
            }
            
            val fileSize = backupFile.length()
            if (fileSize == 0L) {
                return@withContext ValidationResult.Error("Backup file is empty")
            }
            
            // Basic file header check for SQLite database
            val header = ByteArray(16)
            FileInputStream(backupFile).use { input ->
                val bytesRead = input.read(header)
                if (bytesRead < 16) {
                    return@withContext ValidationResult.Error("Backup file is too small to be a valid database")
                }
            }
            
            // Check SQLite magic number
            val sqliteHeader = "SQLite format 3\u0000".toByteArray()
            if (!header.contentEquals(sqliteHeader)) {
                return@withContext ValidationResult.Error("Backup file does not appear to be a SQLite database")
            }
            
            Log.d(TAG, "Backup file validation passed: ${fileSize} bytes")
            ValidationResult.Success(fileSize)
            
        } catch (e: Exception) {
            Log.e(TAG, "Backup file validation failed", e)
            ValidationResult.Error("Validation failed: ${e.message}")
        }
    }
}

/**
 * Progress information for database restore
 */
data class RestoreProgress(
    val phase: String,
    val progress: Int,
    val total: Int,
    val currentItem: String
) {
    val progressPercentage: Float
        get() = if (total > 0) (progress.toFloat() / total.toFloat()) * 100f else 0f
}

/**
 * Result of database restore
 */
sealed class RestoreResult {
    data class Success(val showCount: Int, val recordingCount: Int) : RestoreResult()
    data class Error(val error: String) : RestoreResult()
}

/**
 * Result of database verification
 */
sealed class DatabaseVerification {
    data class Success(val showCount: Int, val recordingCount: Int) : DatabaseVerification()
    data class Error(val error: String) : DatabaseVerification()
}

/**
 * Result of backup file validation
 */
sealed class ValidationResult {
    data class Success(val fileSize: Long) : ValidationResult()
    data class Error(val error: String) : ValidationResult()
}