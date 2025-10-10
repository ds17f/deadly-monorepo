package com.deadly.v2.feature.library.screens.main.models

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deadly.v2.core.api.library.LibraryService
import com.deadly.v2.core.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

/**
 * V2 LibraryViewModel - ViewModel for library management following V2 patterns
 * 
 * Uses service-oriented architecture with direct delegation to LibraryService.
 * Provides reactive UI state management with comprehensive error handling
 * and real-time updates for library operations.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryService: LibraryService
) : ViewModel() {
    
    companion object {
        private const val TAG = "V2LibraryViewModel"
    }
    
    private val _uiState = MutableStateFlow(
        LibraryUiState(
            isLoading = true,
            shows = emptyList(),
            stats = null
        )
    )
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    
    init {
        Log.d(TAG, "V2 LibraryViewModel initialized")
        loadLibrary()
    }
    
    /**
     * Load library shows and create reactive UI state
     */
    private fun loadLibrary() {
        viewModelScope.launch {
            Log.d(TAG, "Loading library via V2 service")
            
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
     * Download show
     */
    fun downloadShow(showId: String) {
        viewModelScope.launch {
            Log.d(TAG, "Downloading show '$showId'")
            libraryService.downloadShow(showId)
                .onSuccess {
                    Log.d(TAG, "Successfully started download for show '$showId'")
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
            libraryStatusDescription = libraryShow.libraryStatusDescription
        )
    }
}