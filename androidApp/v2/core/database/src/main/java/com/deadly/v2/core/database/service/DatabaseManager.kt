package com.deadly.v2.core.database.service

import android.content.Context
import android.util.Log
import com.deadly.v2.core.model.V2Database
import com.deadly.v2.core.database.dao.DataVersionDao
import com.deadly.v2.core.network.github.service.GitHubDataService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simplified database manager for V2 architecture
 */
@Singleton
class DatabaseManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @V2Database private val dataVersionDao: DataVersionDao,
    private val dataImportService: DataImportService,
    private val gitHubDataService: GitHubDataService,
    private val databaseHealthService: DatabaseHealthService,
    private val fileDiscoveryService: FileDiscoveryService,
    private val downloadService: DownloadService,
    private val zipExtractionService: ZipExtractionService,
    private val databaseRestoreService: DatabaseRestoreService
) {
    
    companion object {
        private const val TAG = "DatabaseManager"
    }
    
    private val _progress = MutableStateFlow(DatabaseProgress())
    val progress: Flow<DatabaseProgress> = _progress.asStateFlow()
    
    enum class DatabaseSource {
        ZIP_BACKUP,
        DATA_IMPORT
    }
    
    data class DatabaseProgress(
        val phase: String = "IDLE",
        val totalItems: Int = 0,
        val processedItems: Int = 0,
        val currentItem: String = "",
        val error: String? = null
    )
    
    data class AvailableSources(
        val sources: List<DatabaseSource>
    )
    
    /**
     * Check if V2 data is already initialized using health service
     */
    suspend fun isV2DataInitialized(): Boolean {
        return try {
            Log.d(TAG, "Checking if V2 data is initialized...")
            databaseHealthService.isDatabaseHealthy()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check if V2 data is initialized", e)
            false
        }
    }
    
    /**
     * Initialize V2 data if needed
     */
    suspend fun initializeV2DataIfNeeded(): DatabaseImportResult {
        return try {
            if (isV2DataInitialized()) {
                Log.i(TAG, "V2 data already initialized")
                // Get actual counts from database
                val healthInfo = databaseHealthService.getDatabaseCounts()
                return DatabaseImportResult.Success(healthInfo.showCount, healthInfo.recordingCount)
            }
            
            // Check for available data sources
            val availableSources = findAvailableDataSources()
            
            when {
                availableSources.sources.isEmpty() -> {
                    Log.w(TAG, "❌ No data sources available for initialization")
                    DatabaseImportResult.Error("No data sources available. Please ensure you have network access to download files or manually place data files in the app directory.")
                }
                availableSources.sources.size == 1 -> {
                    // Auto-select the only available source
                    val singleSource = availableSources.sources.first()
                    Log.i(TAG, "✅ Single data source available: $singleSource - proceeding automatically")
                    initializeFromSource(singleSource)
                }
                else -> {
                    // Multiple sources available - require user choice
                    Log.i(TAG, "⚠️ Multiple data sources available: ${availableSources.sources.joinToString(", ")} - requiring user choice")
                    DatabaseImportResult.RequiresUserChoice(availableSources)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize V2 data", e)
            DatabaseImportResult.Error(e.message ?: "Initialization failed")
        }
    }
    
    /**
     * Clear V2 database data
     */
    suspend fun clearV2Database() {
        try {
            Log.i(TAG, "Clearing V2 database data")
            dataVersionDao.deleteAll()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear V2 database", e)
            throw e
        }
    }
    
    /**
     * Initialize from a specific source (local or remote)
     * Only downloads if file doesn't exist locally
     */
    suspend fun initializeFromSource(source: DatabaseSource): DatabaseImportResult {
        return try {
            Log.i(TAG, "Initializing from source: $source")
            _progress.value = DatabaseProgress(phase = "CHECKING", currentItem = "Checking data sources...")
            
            when (source) {
                DatabaseSource.DATA_IMPORT -> {
                    Log.d(TAG, "Processing DATA_IMPORT source...")
                    
                    // Check for local data file first
                    var dataFile = findLocalDataFile()
                    
                    if (dataFile != null) {
                        Log.i(TAG, "✅ Using local data file: ${dataFile.name} (${dataFile.length()} bytes)")
                        _progress.value = DatabaseProgress(phase = "USING_LOCAL", currentItem = "Using local data...")
                    } else {
                        // No local file, download from remote
                        Log.i(TAG, "No local data file found, downloading from remote...")
                        _progress.value = DatabaseProgress(phase = "DOWNLOADING", currentItem = "Downloading data...")
                        
                        val downloadResult = downloadService.downloadFileByType(
                            FileDiscoveryService.FileType.DATA_ZIP
                        ) { progress ->
                            _progress.value = DatabaseProgress(
                                phase = "DOWNLOADING",
                                currentItem = "Downloading... ${(progress.progressPercent * 100).toInt()}%",
                                totalItems = (progress.totalBytes / 1024 / 1024).toInt(), // MB
                                processedItems = (progress.downloadedBytes / 1024 / 1024).toInt() // MB
                            )
                        }
                        
                        when (downloadResult) {
                            is DownloadResult.Success -> {
                                dataFile = downloadResult.downloadedFile
                                Log.i(TAG, "✅ Data file downloaded successfully: ${dataFile.absolutePath}")
                            }
                            is DownloadResult.Error -> {
                                return DatabaseImportResult.Error("Failed to download data file: ${downloadResult.error}")
                            }
                        }
                    }
                    
                    // Extract ZIP file
                    Log.i(TAG, "Extracting data.zip file...")
                    _progress.value = DatabaseProgress(phase = "EXTRACTING", currentItem = "Extracting data...")
                    
                    val extractionResult = zipExtractionService.extractDataZip(dataFile) { progress ->
                        _progress.value = DatabaseProgress(
                            phase = "EXTRACTING",
                            totalItems = progress.totalEntries,
                            processedItems = progress.extractedEntries,
                            currentItem = progress.currentItem
                        )
                    }
                    
                    when (extractionResult) {
                        is DataExtractionResult.Success -> {
                            Log.i(TAG, "✅ Data extraction successful")
                            
                            // Import the extracted data
                            Log.i(TAG, "Importing extracted data...")
                            val importResult = dataImportService.importFromExtractedFiles(
                                showsDirectory = extractionResult.showsDirectory,
                                recordingsDirectory = extractionResult.recordingsDirectory
                            ) { progress ->
                                _progress.value = DatabaseProgress(
                                    phase = progress.phase,
                                    totalItems = progress.totalItems,
                                    processedItems = progress.processedItems,
                                    currentItem = progress.currentItem
                                )
                            }
                            
                            // Cleanup extraction directory
                            zipExtractionService.cleanupExtractions()
                            
                            if (importResult.success) {
                                _progress.value = DatabaseProgress(phase = "COMPLETED", currentItem = "Import completed successfully")
                                Log.i(TAG, "✅ Data import completed: ${importResult.importedShows} shows, ${importResult.importedRecordings} recordings")
                                DatabaseImportResult.Success(importResult.importedShows, importResult.importedRecordings)
                            } else {
                                DatabaseImportResult.Error("Data import failed: ${importResult.message}")
                            }
                        }
                        is DataExtractionResult.Error -> {
                            DatabaseImportResult.Error("Data extraction failed: ${extractionResult.error}")
                        }
                    }
                }
                
                DatabaseSource.ZIP_BACKUP -> {
                    Log.d(TAG, "Processing ZIP_BACKUP source...")
                    
                    // Check for local database file first
                    var dbFile = findLocalDatabaseFile()
                    
                    if (dbFile != null) {
                        Log.i(TAG, "✅ Using local database file: ${dbFile.name} (${dbFile.length()} bytes)")
                        _progress.value = DatabaseProgress(phase = "USING_LOCAL", currentItem = "Using local database...")
                    } else {
                        // No local file, download from remote
                        Log.i(TAG, "No local database file found, downloading from remote...")
                        _progress.value = DatabaseProgress(phase = "DOWNLOADING", currentItem = "Downloading backup...")
                        
                        val downloadResult = downloadService.downloadFileByType(
                            FileDiscoveryService.FileType.DATABASE_ZIP
                        ) { progress ->
                            _progress.value = DatabaseProgress(
                                phase = "DOWNLOADING",
                                currentItem = "Downloading... ${(progress.progressPercent * 100).toInt()}%",
                                totalItems = (progress.totalBytes / 1024 / 1024).toInt(), // MB
                                processedItems = (progress.downloadedBytes / 1024 / 1024).toInt() // MB
                            )
                        }
                        
                        when (downloadResult) {
                            is DownloadResult.Success -> {
                                dbFile = downloadResult.downloadedFile
                                Log.i(TAG, "✅ Database file downloaded successfully: ${dbFile.absolutePath}")
                            }
                            is DownloadResult.Error -> {
                                return DatabaseImportResult.Error("Failed to download database file: ${downloadResult.error}")
                            }
                        }
                    }
                    
                    // Extract ZIP file containing database
                    Log.i(TAG, "Extracting database backup ZIP file...")
                    _progress.value = DatabaseProgress(phase = "EXTRACTING", currentItem = "Extracting backup...")
                    
                    val extractionResult = zipExtractionService.extractDatabaseZip(dbFile) { progress ->
                        _progress.value = DatabaseProgress(
                            phase = "EXTRACTING",
                            totalItems = progress.totalEntries,
                            processedItems = progress.extractedEntries,
                            currentItem = progress.currentItem
                        )
                    }
                    
                    when (extractionResult) {
                        is DatabaseExtractionResult.Success -> {
                            Log.i(TAG, "✅ Database extraction successful")
                            
                            // Restore the extracted database
                            Log.i(TAG, "Restoring database from backup...")
                            val restoreResult = databaseRestoreService.restoreFromBackup(
                                extractedDatabaseFile = extractionResult.databaseFile
                            ) { progress ->
                                _progress.value = DatabaseProgress(
                                    phase = "RESTORING",
                                    totalItems = progress.total,
                                    processedItems = progress.progress,
                                    currentItem = progress.currentItem
                                )
                            }
                            
                            // Cleanup extraction directory
                            zipExtractionService.cleanupExtractions()
                            
                            when (restoreResult) {
                                is RestoreResult.Success -> {
                                    _progress.value = DatabaseProgress(phase = "COMPLETED", currentItem = "Restore completed successfully")
                                    Log.i(TAG, "✅ Database restore completed: ${restoreResult.showCount} shows, ${restoreResult.recordingCount} recordings")
                                    DatabaseImportResult.Success(restoreResult.showCount, restoreResult.recordingCount)
                                }
                                is RestoreResult.Error -> {
                                    DatabaseImportResult.Error("Database restore failed: ${restoreResult.error}")
                                }
                            }
                        }
                        is DatabaseExtractionResult.Error -> {
                            DatabaseImportResult.Error("Database extraction failed: ${extractionResult.error}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize from source: $source", e)
            _progress.value = DatabaseProgress(phase = "ERROR", error = e.message)
            DatabaseImportResult.Error(e.message ?: "Import failed")
        }
    }
    
    private suspend fun findAvailableDataSources(): AvailableSources {
        val sources = mutableListOf<DatabaseSource>()
        
        Log.d(TAG, "Discovering available data sources...")
        val discoveryResult = fileDiscoveryService.discoverAvailableFiles()
        
        // Check for data import files (local or remote)
        val hasDataFile = discoveryResult.localFiles.any { it.type == FileDiscoveryService.FileType.DATA_ZIP } ||
                         discoveryResult.remoteFiles.any { it.type == FileDiscoveryService.FileType.DATA_ZIP }
        
        if (hasDataFile) {
            Log.d(TAG, "Data source available: DATA_IMPORT")
            sources.add(DatabaseSource.DATA_IMPORT)
        }
        
        // Check for database backup files (local or remote)  
        val hasDbFile = discoveryResult.localFiles.any { it.type == FileDiscoveryService.FileType.DATABASE_ZIP } ||
                       discoveryResult.remoteFiles.any { it.type == FileDiscoveryService.FileType.DATABASE_ZIP }
        
        if (hasDbFile) {
            Log.d(TAG, "Data source available: ZIP_BACKUP")
            sources.add(DatabaseSource.ZIP_BACKUP)
        }
        
        Log.d(TAG, "Available sources: ${sources.joinToString(", ")}")
        return AvailableSources(sources)
    }
    
    private fun findDataImportFile(): File? {
        // Look for data.json in assets or external storage
        val assetFile = File(context.filesDir, "data.json")
        return if (assetFile.exists()) assetFile else null
    }
    
    private fun findLocalDataFile(): File? {
        // Look for data.zip files in the files directory
        val filesDir = context.filesDir
        return filesDir.listFiles()?.find { file ->
            file.isFile && file.name.lowercase().let { name ->
                name.startsWith("data") && name.endsWith(".zip")
            }
        }
    }
    
    private fun findLocalDatabaseFile(): File? {
        // Look for database.zip files in the files directory  
        val filesDir = context.filesDir
        return filesDir.listFiles()?.find { file ->
            file.isFile && file.name.lowercase().let { name ->
                name.contains("db") && name.endsWith(".zip")
            }
        }
    }
    
    
    /**
     * Test GitHub API integration
     */
    suspend fun testGitHubIntegration() {
        Log.d(TAG, "Testing GitHub API integration...")
        val release = gitHubDataService.getLatestRelease()
        if (release != null) {
            Log.d(TAG, "✅ GitHub API works! Latest release: ${release.tagName} with ${release.assets.size} assets")
            release.assets.forEach { asset ->
                Log.d(TAG, "  📦 Asset: ${asset.name} (${asset.size} bytes)")
            }
        } else {
            Log.e(TAG, "❌ GitHub API failed to fetch release")
        }
    }
    
    /**
     * Test database health checking
     */
    suspend fun testDatabaseHealth() {
        Log.d(TAG, "Testing database health checking...")
        val isHealthy = databaseHealthService.isDatabaseHealthy()
        Log.d(TAG, if (isHealthy) "✅ Database health check works!" else "✅ Database health check works - detected empty database")
        
        // Test initialization check
        val isInitialized = isV2DataInitialized()
        Log.d(TAG, "Database initialization status: $isInitialized")
    }
    
    /**
     * Test file discovery functionality
     */
    suspend fun testFileDiscovery() {
        Log.d(TAG, "Testing file discovery...")
        val discoveryResult = fileDiscoveryService.discoverAvailableFiles()
        
        Log.d(TAG, "Local files found: ${discoveryResult.localFiles.size}")
        discoveryResult.localFiles.forEach { localFile ->
            Log.d(TAG, "  📁 Local: ${localFile.file.name} (${localFile.type}, ${localFile.sizeBytes} bytes)")
        }
        
        Log.d(TAG, "Remote files found: ${discoveryResult.remoteFiles.size}")
        discoveryResult.remoteFiles.forEach { remoteFile ->
            Log.d(TAG, "  ☁️ Remote: ${remoteFile.name} (${remoteFile.type}, ${remoteFile.sizeBytes} bytes)")
        }
        
        // Test source discovery
        val availableSources = findAvailableDataSources()
        Log.d(TAG, "✅ File discovery works! Available sources: ${availableSources.sources.joinToString(", ")}")
    }
    
    /**
     * Test download functionality (downloads a small file for testing)
     */
    suspend fun testDownloadFunctionality() {
        Log.d(TAG, "Testing download functionality...")
        
        try {
            // Test downloading the data.zip file
            Log.d(TAG, "Attempting to download data.zip for testing...")
            
            val result = downloadService.downloadFileByType(
                FileDiscoveryService.FileType.DATA_ZIP
            ) { progress ->
                if (progress.downloadedBytes % (1024 * 1024) == 0L || progress.isCompleted) {
                    Log.d(TAG, "Download progress: ${progress.fileName} - ${progress.progressPercent * 100}% (${progress.downloadedBytes}/${progress.totalBytes} bytes)")
                }
            }
            
            when (result) {
                is DownloadResult.Success -> {
                    Log.d(TAG, "✅ Download test successful! File saved to: ${result.downloadedFile.absolutePath}")
                    Log.d(TAG, "Downloaded file size: ${result.downloadedFile.length()} bytes")
                    
                    // Clean up the test download
                    Log.d(TAG, "Cleaning up test download...")
                    val cleanedUp = downloadService.cleanupDownloadedFile(result.downloadedFile.name)
                    Log.d(TAG, if (cleanedUp) "✅ Test file cleaned up" else "❌ Failed to clean up test file")
                }
                is DownloadResult.Error -> {
                    Log.e(TAG, "❌ Download test failed: ${result.error}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Download test exception", e)
        }
    }
}

/**
 * Database import result types
 */
sealed class DatabaseImportResult {
    data class Success(val showsImported: Int, val venuesImported: Int) : DatabaseImportResult()
    data class Error(val error: String) : DatabaseImportResult()
    data class RequiresUserChoice(val availableSources: DatabaseManager.AvailableSources) : DatabaseImportResult()
}