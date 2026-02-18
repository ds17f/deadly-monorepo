package com.grateful.deadly.feature.splash.service

import android.util.Log
import com.grateful.deadly.feature.splash.model.Phase
import com.grateful.deadly.feature.splash.model.Progress
import com.grateful.deadly.core.database.service.DatabaseManager
import com.grateful.deadly.core.database.service.DatabaseImportResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for coordinating Database initialization during splash screen
 */
@Singleton
class SplashService @Inject constructor(
    private val databaseManager: DatabaseManager
) {
    companion object {
        private const val TAG = "SplashService"
    }
    
    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()
    
    // Track the start time of the initialization process
    private var initStartTimeMs: Long = 0L
    
    /**
     * Convert DatabaseManager progress to splash progress
     */
    fun getProgress(): Flow<Progress> {
        return databaseManager.progress.map { dbProgress ->
            val phase = when (dbProgress.phase) {
                "IDLE" -> Phase.IDLE
                "CHECKING" -> Phase.CHECKING
                "UPGRADING" -> Phase.UPGRADING
                "USING_LOCAL" -> Phase.USING_LOCAL
                "DOWNLOADING" -> Phase.DOWNLOADING
                "EXTRACTING" -> Phase.EXTRACTING
                "IMPORTING_SHOWS" -> Phase.IMPORTING_SHOWS
                "COMPUTING_VENUES" -> Phase.COMPUTING_VENUES
                "IMPORTING_RECORDINGS" -> Phase.IMPORTING_RECORDINGS
                "COMPLETED" -> Phase.COMPLETED
                "ERROR" -> Phase.ERROR
                else -> Phase.IDLE
            }
            
            // Initialize start time when we begin processing
            if (initStartTimeMs == 0L && phase in listOf(Phase.CHECKING, Phase.UPGRADING, Phase.USING_LOCAL, Phase.DOWNLOADING, Phase.EXTRACTING, Phase.IMPORTING_SHOWS)) {
                initStartTimeMs = System.currentTimeMillis()
                Log.d(TAG, "Database initialization started at ${initStartTimeMs}")
            }
            
            // Map progress based on phase type
            when (phase) {
                Phase.IMPORTING_RECORDINGS -> Progress(
                    phase = phase,
                    totalShows = 0,
                    processedShows = 0,
                    currentShow = "",
                    totalRecordings = dbProgress.totalItems,
                    processedRecordings = dbProgress.processedItems,
                    currentRecording = dbProgress.currentItem,
                    startTimeMs = initStartTimeMs,
                    error = dbProgress.error
                )
                Phase.COMPUTING_VENUES -> Progress(
                    phase = phase,
                    totalShows = 0,
                    processedShows = 0,
                    currentShow = "",
                    totalVenues = dbProgress.totalItems,
                    processedVenues = dbProgress.processedItems,
                    startTimeMs = initStartTimeMs,
                    error = dbProgress.error
                )
                else -> Progress(
                    phase = phase,
                    totalShows = dbProgress.totalItems,
                    processedShows = dbProgress.processedItems,
                    currentShow = dbProgress.currentItem,
                    startTimeMs = initStartTimeMs,
                    error = dbProgress.error
                )
            }
        }
    }
    
    /**
     * Initialize database with progress tracking
     */
    suspend fun initializeDatabase(): InitResult {
        return try {
            Log.d(TAG, "Starting Database initialization")

            val result = databaseManager.initializeDataIfNeeded()
            
            when (result) {
                is DatabaseImportResult.Success -> {
                    Log.d(TAG, "Database initialization completed: ${result.showsImported} shows, ${result.venuesImported} venues")
                    InitResult.Success(result.showsImported, result.venuesImported)
                }
                is DatabaseImportResult.Error -> {
                    Log.e(TAG, "Database initialization failed: ${result.error}")
                    InitResult.Error(result.error)
                }
                is DatabaseImportResult.RequiresUserChoice -> {
                    Log.d(TAG, "Multiple database sources available, requiring user choice")
                    // Update UI to show source selection
                    updateUiState(
                        showProgress = false,
                        showSourceSelection = true,
                        availableSources = result.availableSources.sources,
                        message = "Choose database source"
                    )
                    InitResult.Error("User choice required") // Will be handled by source selection UI
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Database initialization exception", e)
            InitResult.Error(e.message ?: "Initialization failed")
        }
    }
    
    /**
     * Check if data is already initialized
     */
    suspend fun isDataInitialized(): Boolean {
        return try {
            databaseManager.isDataInitialized()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check data initialization status", e)
            false
        }
    }
    
    /**
     * Update splash UI state
     */
    fun updateUiState(
        isReady: Boolean = _uiState.value.isReady,
        showError: Boolean = _uiState.value.showError,
        showProgress: Boolean = _uiState.value.showProgress,
        showSourceSelection: Boolean = _uiState.value.showSourceSelection,
        availableSources: List<DatabaseManager.DatabaseSource> = _uiState.value.availableSources,
        message: String = _uiState.value.message,
        errorMessage: String? = _uiState.value.errorMessage,
        progress: Progress = _uiState.value.progress
    ) {
        _uiState.value = SplashUiState(
            isReady = isReady,
            showError = showError,
            showProgress = showProgress,
            showSourceSelection = showSourceSelection,
            availableSources = availableSources,
            message = message,
            errorMessage = errorMessage,
            progress = progress
        )
    }
    
    /**
     * Retry Database initialization
     */
    fun retryInitialization(coroutineScope: CoroutineScope) {
        coroutineScope.launch {
            updateUiState(
                showError = false,
                showProgress = true,
                message = "Retrying Database initialization...",
                errorMessage = null
            )
            
            val result = initializeDatabase()
            when (result) {
                is InitResult.Success -> {
                    updateUiState(
                        isReady = true,
                        showProgress = false,
                        message = "Database ready: ${result.showsImported} shows loaded"
                    )
                }
                is InitResult.Error -> {
                    updateUiState(
                        showError = true,
                        showProgress = false,
                        message = "Database initialization failed",
                        errorMessage = result.error
                    )
                }
            }
        }
    }
    
    /**
     * Skip initialization and proceed
     */
    fun skipInitialization() {
        updateUiState(
            isReady = true,
            showError = false,
            showProgress = false,
            message = "Skipped Database initialization"
        )
    }
    
    /**
     * Abort current initialization and proceed to main screen
     */
    fun abortInitialization() {
        updateUiState(
            isReady = true,
            showError = false,
            showProgress = false,
            showSourceSelection = false,
            message = "Database import aborted"
        )
    }
    
    /**
     * User selected a database source for initialization
     */
    fun selectDatabaseSource(source: DatabaseManager.DatabaseSource, coroutineScope: CoroutineScope) {
        coroutineScope.launch {
            Log.d(TAG, "User selected database source: $source")
            
            // Hide source selection and show progress
            updateUiState(
                showSourceSelection = false,
                showProgress = true,
                message = when (source) {
                    DatabaseManager.DatabaseSource.ZIP_BACKUP -> "Restoring from backup..."
                    DatabaseManager.DatabaseSource.DATA_IMPORT -> "Importing fresh data..."
                }
            )
            
            try {
                val result = databaseManager.initializeFromSource(source)
                
                when (result) {
                    is DatabaseImportResult.Success -> {
                        updateUiState(
                            isReady = true,
                            showProgress = false,
                            message = when (source) {
                                DatabaseManager.DatabaseSource.ZIP_BACKUP -> "Database restored from backup"
                                DatabaseManager.DatabaseSource.DATA_IMPORT -> "Database ready: ${result.showsImported} shows loaded"
                            }
                        )
                    }
                    is DatabaseImportResult.Error -> {
                        updateUiState(
                            showError = true,
                            showProgress = false,
                            message = "Database initialization failed",
                            errorMessage = result.error
                        )
                    }
                    is DatabaseImportResult.RequiresUserChoice -> {
                        // This shouldn't happen when selecting a specific source
                        updateUiState(
                            showError = true,
                            showProgress = false,
                            message = "Unexpected error: user choice required again",
                            errorMessage = "Internal error"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize from selected source: $source", e)
                updateUiState(
                    showError = true,
                    showProgress = false,
                    message = "Database initialization failed",
                    errorMessage = e.message
                )
            }
        }
    }
}

/**
 * UI state for Splash screen
 */
data class SplashUiState(
    val isReady: Boolean = false,
    val showError: Boolean = false,
    val showProgress: Boolean = false,
    val showSourceSelection: Boolean = false,
    val availableSources: List<DatabaseManager.DatabaseSource> = emptyList(),
    val message: String = "Loading database...",
    val errorMessage: String? = null,
    val progress: Progress = Progress(
        phase = Phase.IDLE,
        totalShows = 0,
        processedShows = 0,
        currentShow = ""
    )
)

/**
 * Result of Database initialization
 */
sealed class InitResult {
    data class Success(val showsImported: Int, val venuesImported: Int) : InitResult()
    data class Error(val error: String) : InitResult()
}