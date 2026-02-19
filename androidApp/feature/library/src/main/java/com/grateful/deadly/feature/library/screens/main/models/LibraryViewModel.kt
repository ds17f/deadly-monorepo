package com.grateful.deadly.feature.library.screens.main.models

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grateful.deadly.core.api.library.LibraryService
import com.grateful.deadly.core.database.AppPreferences
import com.grateful.deadly.core.database.migration.MigrationData
import com.grateful.deadly.core.database.migration.MigrationImportService
import com.grateful.deadly.core.database.migration.MigrationLibraryShow
import com.grateful.deadly.core.database.migration.MigrationRecentShow
import com.grateful.deadly.core.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Named

/**
 * LibraryViewModel - ViewModel for library management
 * 
 * Uses service-oriented architecture with direct delegation to LibraryService.
 * Provides reactive UI state management with comprehensive error handling
 * and real-time updates for library operations.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryService: LibraryService,
    private val appPreferences: AppPreferences,
    private val migrationImportService: MigrationImportService,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    companion object {
        private const val TAG = "LibraryViewModel"
    }
    
    private val _uiState = MutableStateFlow(
        LibraryUiState(
            isLoading = true,
            shows = emptyList(),
            stats = null
        )
    )
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    val displayMode: StateFlow<LibraryDisplayMode> = appPreferences.libraryDisplayMode
        .map { if (it == "GRID") LibraryDisplayMode.GRID else LibraryDisplayMode.LIST }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = if (appPreferences.libraryDisplayMode.value == "GRID") LibraryDisplayMode.GRID else LibraryDisplayMode.LIST
        )

    fun setDisplayMode(mode: LibraryDisplayMode) {
        appPreferences.setLibraryDisplayMode(mode.name)
    }

    init {
        Log.d(TAG, "LibraryViewModel initialized")
        loadLibrary()
    }
    
    /**
     * Load library shows and create reactive UI state
     */
    private fun loadLibrary() {
        viewModelScope.launch {
            Log.d(TAG, "Loading library")
            
            try {
                // Load library data
                libraryService.loadLibraryShows()
                    .onSuccess {
                        Log.d(TAG, "Library loaded successfully")
                        
                        // Create reactive UI state from service flows
                        combine(
                            libraryService.getCurrentShows(),
                            libraryService.getLibraryStats()
                        ) { shows, stats ->
                            LibraryUiState(
                                isLoading = false,
                                error = null,
                                shows = shows.map { libraryShow ->
                                    mapToLibraryShowViewModel(libraryShow)
                                },
                                stats = stats
                            )
                        }.collect { newState ->
                            _uiState.value = newState
                            Log.d(TAG, "UI state updated: ${newState.shows.size} shows")
                        }
                    }
                    .onFailure { error ->
                        Log.e(TAG, "Failed to load library", error)
                        _uiState.value = LibraryUiState(
                            isLoading = false,
                            error = error.message ?: "Failed to load library",
                            shows = emptyList(),
                            stats = null
                        )
                    }
                    
            } catch (e: Exception) {
                Log.e(TAG, "Error during library load", e)
                _uiState.value = LibraryUiState(
                    isLoading = false,
                    error = e.message ?: "Unknown error",
                    shows = emptyList(),
                    stats = null
                )
            }
        }
    }
    
    /**
     * Add show to library
     */
    fun addToLibrary(showId: String) {
        viewModelScope.launch {
            Log.d(TAG, "Adding show '$showId' to library")
            libraryService.addToLibrary(showId)
                .onSuccess {
                    Log.d(TAG, "Successfully added show '$showId' to library")
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to add show '$showId' to library", error)
                    // TODO: Show user feedback
                }
        }
    }
    
    /**
     * Remove show from library
     */
    fun removeFromLibrary(showId: String) {
        viewModelScope.launch {
            Log.d(TAG, "Removing show '$showId' from library")
            libraryService.removeFromLibrary(showId)
                .onSuccess {
                    Log.d(TAG, "Successfully removed show '$showId' from library")
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to remove show '$showId' from library", error)
                    // TODO: Show user feedback
                }
        }
    }
    
    /**
     * Clear entire library
     */
    fun clearLibrary() {
        viewModelScope.launch {
            Log.d(TAG, "Clearing library")
            libraryService.clearLibrary()
                .onSuccess {
                    Log.d(TAG, "Successfully cleared library")
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to clear library", error)
                    // TODO: Show user feedback
                }
        }
    }
    
    /**
     * Pin show for priority display
     */
    fun pinShow(showId: String) {
        viewModelScope.launch {
            Log.d(TAG, "Pinning show '$showId'")
            libraryService.pinShow(showId)
                .onSuccess {
                    Log.d(TAG, "Successfully pinned show '$showId'")
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to pin show '$showId'", error)
                    // TODO: Show user feedback
                }
        }
    }
    
    /**
     * Unpin show
     */
    fun unpinShow(showId: String) {
        viewModelScope.launch {
            Log.d(TAG, "Unpinning show '$showId'")
            libraryService.unpinShow(showId)
                .onSuccess {
                    Log.d(TAG, "Successfully unpinned show '$showId'")
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to unpin show '$showId'", error)
                    // TODO: Show user feedback
                }
        }
    }
    
    /**
     * Download show using the preferred recording if one has been set
     */
    fun downloadShow(showId: String) {
        viewModelScope.launch {
            Log.d(TAG, "Downloading show '$showId'")
            val preferredRecordingId = libraryService.getPreferredRecordingId(showId)
            libraryService.downloadShow(showId, preferredRecordingId)
                .onSuccess {
                    Log.d(TAG, "Successfully started download for show '$showId' (recording=${preferredRecordingId ?: "best"})")
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to download show '$showId'", error)
                    // TODO: Show user feedback
                }
        }
    }
    
    /**
     * Cancel show download
     */
    fun cancelDownload(showId: String) {
        viewModelScope.launch {
            Log.d(TAG, "Cancelling download for show '$showId'")
            libraryService.cancelShowDownloads(showId)
                .onSuccess {
                    Log.d(TAG, "Successfully cancelled download for show '$showId'")
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to cancel download for show '$showId'", error)
                    // TODO: Show user feedback
                }
        }
    }
    
    /**
     * Share show
     */
    fun shareShow(showId: String) {
        viewModelScope.launch {
            Log.d(TAG, "Sharing show '$showId'")
            libraryService.shareShow(showId)
                .onSuccess {
                    Log.d(TAG, "Successfully shared show '$showId'")
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to share show '$showId'", error)
                    // TODO: Show user feedback
                }
        }
    }
    
    /**
     * Populate test data for development
     */
    fun populateTestData() {
        viewModelScope.launch {
            Log.d(TAG, "Populating test data")
            libraryService.populateTestData()
                .onSuccess {
                    Log.d(TAG, "Successfully populated test data")
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to populate test data", error)
                }
        }
    }
    
    /**
     * Import library from JSON file
     */
    fun importLibrary(uri: Uri, onComplete: (Result<String>) -> Unit) {
        viewModelScope.launch {
            Log.d(TAG, "Importing library from URI")
            try {
                val jsonString = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().readText()
                    } ?: throw IllegalStateException("Could not open file")
                }
                val result = withContext(Dispatchers.IO) {
                    migrationImportService.importFromJson(jsonString)
                }
                Log.d(TAG, "Library import completed: ${result.libraryImported} imported, ${result.skipped} skipped")
                onComplete(Result.success("Imported ${result.libraryImported} shows"))
            } catch (e: Exception) {
                Log.e(TAG, "Library import failed", e)
                onComplete(Result.failure(e))
            }
        }
    }

    /**
     * Export library to JSON file
     */
    fun exportLibrary(onComplete: (Result<String>) -> Unit) {
        viewModelScope.launch {
            Log.d(TAG, "Exporting library")
            try {
                val currentShows = _uiState.value.shows

                // Create migration data format
                val libraryShows = currentShows.map { show ->
                    MigrationLibraryShow(
                        date = show.date,
                        venue = show.venue,
                        location = show.location,
                        addedAt = show.addedToLibraryAt,
                        preferredRecordingId = null
                    )
                }

                val migrationData = MigrationData(
                    version = 1,
                    format = "deadly-migration",
                    createdAt = System.currentTimeMillis(),
                    appVersion = "2.7.1",
                    library = libraryShows,
                    recentPlays = emptyList(),
                    lastPlayed = null
                )

                // Serialize to JSON
                val json = Json { prettyPrint = true }
                val jsonString = json.encodeToString(MigrationData.serializer(), migrationData)

                // Write to Downloads folder
                val downloadsDir = File("/storage/emulated/0/Download/")
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }

                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val dateString = dateFormat.format(Date())
                val filename = "deadly-library-export-$dateString.json"
                val exportFile = File(downloadsDir, filename)

                exportFile.writeText(jsonString)

                Log.d(TAG, "Library exported successfully to: ${exportFile.absolutePath}")
                onComplete(Result.success(filename))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export library", e)
                onComplete(Result.failure(e))
            }
        }
    }

    /**
     * Retry loading library after error
     */
    fun retry() {
        Log.d(TAG, "Retrying library load")
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            error = null
        )
        loadLibrary()
    }
    
    /**
     * Map domain LibraryShow to UI LibraryShowViewModel
     */
    private fun mapToLibraryShowViewModel(libraryShow: LibraryShow): LibraryShowViewModel {
        return LibraryShowViewModel(
            showId = libraryShow.showId,
            date = libraryShow.date,
            displayDate = libraryShow.displayDate,
            venue = libraryShow.venue,
            location = libraryShow.location,
            rating = libraryShow.averageRating,
            reviewCount = libraryShow.totalReviews,
            addedToLibraryAt = libraryShow.addedToLibraryAt,
            isPinned = libraryShow.isPinned,
            downloadStatus = libraryShow.downloadStatus,
            isDownloaded = libraryShow.isDownloaded,
            isDownloading = libraryShow.isDownloading,
            libraryStatusDescription = libraryShow.libraryStatusDescription,
            bestRecordingId = libraryShow.show.bestRecordingId,
            coverImageUrl = libraryShow.show.coverImageUrl,
            recordingCount = libraryShow.recordingCount
        )
    }
}