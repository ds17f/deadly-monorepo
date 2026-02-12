package com.deadly.v2.core.database.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for extracting ZIP files with progress tracking
 */
@Singleton
class ZipExtractionService @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ZipExtractionService"
        private const val BUFFER_SIZE = 8192
    }

    /**
     * Extract a ZIP file to a directory with progress callbacks
     */
    suspend fun extractZipFile(
        zipFile: File,
        destinationDir: File,
        progressCallback: ((ExtractionProgress) -> Unit)? = null
    ): ExtractionResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting extraction of ${zipFile.name} to ${destinationDir.absolutePath}")
            
            if (!zipFile.exists()) {
                return@withContext ExtractionResult.Error("ZIP file does not exist: ${zipFile.absolutePath}")
            }
            
            if (!zipFile.canRead()) {
                return@withContext ExtractionResult.Error("Cannot read ZIP file: ${zipFile.absolutePath}")
            }
            
            // Create destination directory
            if (!destinationDir.exists()) {
                if (!destinationDir.mkdirs()) {
                    return@withContext ExtractionResult.Error("Failed to create destination directory: ${destinationDir.absolutePath}")
                }
            }
            
            // First pass: count entries for progress tracking
            val totalEntries = countZipEntries(zipFile)
            Log.d(TAG, "ZIP file contains $totalEntries entries")
            
            progressCallback?.invoke(ExtractionProgress(0, totalEntries, "Starting extraction..."))
            
            var extractedEntries = 0
            val extractedFiles = mutableListOf<File>()
            
            ZipInputStream(FileInputStream(zipFile)).use { zipInputStream ->
                var entry = zipInputStream.nextEntry
                
                while (entry != null) {
                    val entryName = entry.name
                    val entryFile = File(destinationDir, entryName)
                    
                    // Security check: prevent path traversal attacks
                    if (!entryFile.canonicalPath.startsWith(destinationDir.canonicalPath)) {
                        Log.w(TAG, "Skipping potentially dangerous entry: $entryName")
                        entry = zipInputStream.nextEntry
                        continue
                    }
                    
                    if (entry.isDirectory) {
                        // Create directory
                        if (!entryFile.exists() && !entryFile.mkdirs()) {
                            Log.w(TAG, "Failed to create directory: ${entryFile.absolutePath}")
                        }
                    } else {
                        // Extract file
                        entryFile.parentFile?.let { parentDir ->
                            if (!parentDir.exists() && !parentDir.mkdirs()) {
                                Log.w(TAG, "Failed to create parent directory: ${parentDir.absolutePath}")
                            }
                        }
                        
                        progressCallback?.invoke(
                            ExtractionProgress(
                                extractedEntries, 
                                totalEntries, 
                                "Extracting files..."
                            )
                        )
                        
                        FileOutputStream(entryFile).use { outputStream ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var bytesRead = zipInputStream.read(buffer)
                            while (bytesRead != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                bytesRead = zipInputStream.read(buffer)
                            }
                        }
                        
                        extractedFiles.add(entryFile)
                        Log.d(TAG, "Extracted: ${entryFile.name} (${entryFile.length()} bytes)")
                    }
                    
                    extractedEntries++
                    zipInputStream.closeEntry()
                    entry = zipInputStream.nextEntry
                }
            }
            
            progressCallback?.invoke(ExtractionProgress(totalEntries, totalEntries, "Extraction completed successfully"))
            
            Log.d(TAG, "Successfully extracted $extractedEntries entries from ${zipFile.name}")
            ExtractionResult.Success(extractedFiles, destinationDir)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract ZIP file: ${zipFile.name}", e)
            ExtractionResult.Error("Extraction failed: ${e.message}")
        }
    }
    
    /**
     * Count entries in ZIP file for progress tracking
     */
    private suspend fun countZipEntries(zipFile: File): Int = withContext(Dispatchers.IO) {
        try {
            var count = 0
            ZipInputStream(FileInputStream(zipFile)).use { zipInputStream ->
                while (zipInputStream.nextEntry != null) {
                    count++
                    zipInputStream.closeEntry()
                }
            }
            count
        } catch (e: Exception) {
            Log.w(TAG, "Failed to count ZIP entries, using default", e)
            1 // Fallback to avoid division by zero
        }
    }
    
    /**
     * Extract data.zip and find the main data files
     */
    suspend fun extractDataZip(
        dataZipFile: File,
        progressCallback: ((ExtractionProgress) -> Unit)? = null
    ): DataExtractionResult = withContext(Dispatchers.IO) {
        try {
            // Create extraction directory
            val extractionDir = File(context.filesDir, "v2_data_extraction")
            if (extractionDir.exists()) {
                extractionDir.deleteRecursively()
            }
            
            val result = extractZipFile(dataZipFile, extractionDir, progressCallback)
            
            return@withContext when (result) {
                is ExtractionResult.Success -> {
                    // Look for expected V2 data directories
                    val showsDir = findDirectory(extractionDir, "shows")
                    val recordingsDir = findDirectory(extractionDir, "recordings")
                    
                    if (showsDir != null && recordingsDir != null) {
                        val showsCount = showsDir.listFiles()?.size ?: 0
                        val recordingsCount = recordingsDir.listFiles()?.size ?: 0
                        Log.d(TAG, "Found V2 data directories: shows/ ($showsCount files), recordings/ ($recordingsCount files)")
                        DataExtractionResult.Success(showsDir, recordingsDir, extractionDir)
                    } else {
                        Log.e(TAG, "V2 data directories not found in extraction. Available: ${extractionDir.listFiles()?.map { it.name } ?: emptyList()}")
                        DataExtractionResult.Error("Required V2 data directories (shows/, recordings/) not found in ZIP")
                    }
                }
                is ExtractionResult.Error -> {
                    DataExtractionResult.Error(result.error)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract data ZIP", e)
            DataExtractionResult.Error("Data extraction failed: ${e.message}")
        }
    }
    
    /**
     * Extract database backup ZIP and find the database file
     */
    suspend fun extractDatabaseZip(
        databaseZipFile: File,
        progressCallback: ((ExtractionProgress) -> Unit)? = null
    ): DatabaseExtractionResult = withContext(Dispatchers.IO) {
        try {
            // Create extraction directory
            val extractionDir = File(context.filesDir, "v2_database_extraction")
            if (extractionDir.exists()) {
                extractionDir.deleteRecursively()
            }
            
            val result = extractZipFile(databaseZipFile, extractionDir, progressCallback)
            
            return@withContext when (result) {
                is ExtractionResult.Success -> {
                    // Look for database file (any .db file)
                    val databaseFile = result.extractedFiles.find { it.name.endsWith(".db") }
                    
                    if (databaseFile != null) {
                        Log.d(TAG, "Found database file: ${databaseFile.name} (${databaseFile.length()} bytes)")
                        DatabaseExtractionResult.Success(databaseFile, extractionDir)
                    } else {
                        Log.e(TAG, "Database file not found in extraction. Files: ${result.extractedFiles.map { it.name }}")
                        DatabaseExtractionResult.Error("Database file (.db) not found in ZIP")
                    }
                }
                is ExtractionResult.Error -> {
                    DatabaseExtractionResult.Error(result.error)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract database ZIP", e)
            DatabaseExtractionResult.Error("Database extraction failed: ${e.message}")
        }
    }
    
    /**
     * Find a file by name in the list of extracted files
     */
    private fun findFile(files: List<File>, name: String): File? {
        return files.find { it.name.equals(name, ignoreCase = true) }
    }
    
    /**
     * Find a directory by name in the extraction directory
     */
    private fun findDirectory(parentDir: File, name: String): File? {
        return parentDir.listFiles()?.find { 
            it.isDirectory && it.name.equals(name, ignoreCase = true) 
        }
    }
    
    /**
     * Clean up extraction directories
     */
    suspend fun cleanupExtractions(): Unit = withContext(Dispatchers.IO) {
        try {
            val dataDir = File(context.filesDir, "v2_data_extraction")
            val dbDir = File(context.filesDir, "v2_database_extraction")
            
            if (dataDir.exists()) {
                dataDir.deleteRecursively()
                Log.d(TAG, "Cleaned up data extraction directory")
            }
            
            if (dbDir.exists()) {
                dbDir.deleteRecursively()
                Log.d(TAG, "Cleaned up database extraction directory")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cleanup extraction directories", e)
        }
    }
}

/**
 * Progress information for ZIP extraction
 */
data class ExtractionProgress(
    val extractedEntries: Int,
    val totalEntries: Int,
    val currentItem: String
) {
    val progressPercentage: Float
        get() = if (totalEntries > 0) (extractedEntries.toFloat() / totalEntries.toFloat()) * 100f else 0f
}

/**
 * Result of ZIP extraction
 */
sealed class ExtractionResult {
    data class Success(val extractedFiles: List<File>, val extractionDirectory: File) : ExtractionResult()
    data class Error(val error: String) : ExtractionResult()
}

/**
 * Result of data ZIP extraction
 */
sealed class DataExtractionResult {
    data class Success(
        val showsDirectory: File, 
        val recordingsDirectory: File,
        val extractionDirectory: File
    ) : DataExtractionResult()
    data class Error(val error: String) : DataExtractionResult()
}

/**
 * Result of database ZIP extraction
 */
sealed class DatabaseExtractionResult {
    data class Success(val databaseFile: File, val extractionDirectory: File) : DatabaseExtractionResult()
    data class Error(val error: String) : DatabaseExtractionResult()
}