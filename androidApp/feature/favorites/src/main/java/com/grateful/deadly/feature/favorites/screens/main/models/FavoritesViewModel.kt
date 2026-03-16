package com.grateful.deadly.feature.favorites.screens.main.models

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grateful.deadly.core.api.favorites.FavoritesService
import com.grateful.deadly.core.api.favorites.ReviewService
import com.grateful.deadly.core.database.AppPreferences
import com.grateful.deadly.core.database.service.BackupImportExportService
import com.grateful.deadly.core.model.*
import com.grateful.deadly.core.model.FavoriteTrack
import com.grateful.deadly.core.model.ShowReview
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
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * FavoritesViewModel - ViewModel for favorites management
 *
 * Uses service-oriented architecture with direct delegation to FavoritesService.
 * Provides reactive UI state management with comprehensive error handling
 * and real-time updates for favorites operations.
 */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoritesService: FavoritesService,
    private val reviewService: ReviewService,
    val appPreferences: AppPreferences,
    private val backupImportExportService: BackupImportExportService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "FavoritesViewModel"
    }

    private val _uiState = MutableStateFlow(
        FavoritesUiState(
            isLoading = true,
            shows = emptyList(),
            stats = null
        )
    )
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    private val _favoriteSongs = MutableStateFlow<List<FavoriteTrack>>(emptyList())
    val favoriteSongs: StateFlow<List<FavoriteTrack>> = _favoriteSongs.asStateFlow()

    private val _songsLoading = MutableStateFlow(false)
    val songsLoading: StateFlow<Boolean> = _songsLoading.asStateFlow()

    val displayMode: StateFlow<FavoritesDisplayMode> = appPreferences.favoritesDisplayMode
        .map { if (it == "GRID") FavoritesDisplayMode.GRID else FavoritesDisplayMode.LIST }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = if (appPreferences.favoritesDisplayMode.value == "GRID") FavoritesDisplayMode.GRID else FavoritesDisplayMode.LIST
        )

    fun setDisplayMode(mode: FavoritesDisplayMode) {
        appPreferences.setFavoritesDisplayMode(mode.name)
    }

    init {
        Log.d(TAG, "FavoritesViewModel initialized")
        loadFavorites()
        loadFavoriteSongs()
    }

    /**
     * Load favorite shows and create reactive UI state
     */
    private fun loadFavorites() {
        viewModelScope.launch {
            Log.d(TAG, "Loading favorites")

            try {
                // Load favorites data
                favoritesService.loadFavoriteShows()
                    .onSuccess {
                        Log.d(TAG, "Favorites loaded successfully")

                        // Create reactive UI state from service flows
                        combine(
                            favoritesService.getCurrentShows(),
                            favoritesService.getFavoritesStats()
                        ) { shows, stats ->
                            FavoritesUiState(
                                isLoading = false,
                                error = null,
                                shows = shows.map { favoriteShow ->
                                    mapToFavoriteShowViewModel(favoriteShow)
                                },
                                stats = stats
                            )
                        }.collect { newState ->
                            _uiState.value = newState
                            Log.d(TAG, "UI state updated: ${newState.shows.size} shows")
                        }
                    }
                    .onFailure { error ->
                        Log.e(TAG, "Failed to load favorites", error)
                        _uiState.value = FavoritesUiState(
                            isLoading = false,
                            error = error.message ?: "Failed to load favorites",
                            shows = emptyList(),
                            stats = null
                        )
                    }

            } catch (e: Exception) {
                Log.e(TAG, "Error during favorites load", e)
                _uiState.value = FavoritesUiState(
                    isLoading = false,
                    error = e.message ?: "Unknown error",
                    shows = emptyList(),
                    stats = null
                )
            }
        }
    }

    /**
     * Load favorite songs (tracks with thumbs == 1)
     */
    private fun loadFavoriteSongs() {
        viewModelScope.launch {
            _songsLoading.value = true
            try {
                _favoriteSongs.value = reviewService.getFavoriteTracks()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load favorite songs", e)
            } finally {
                _songsLoading.value = false
            }
        }
    }

    /**
     * Reload favorite songs (e.g. after tab switch)
     */
    fun refreshFavoriteSongs() {
        loadFavoriteSongs()
    }

    /**
     * Add show to favorites
     */
    fun addToFavorites(showId: String) {
        viewModelScope.launch {
            Log.d(TAG, "Adding show '$showId' to favorites")
            favoritesService.addToFavorites(showId)
                .onSuccess {
                    Log.d(TAG, "Successfully added show '$showId' to favorites")
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to add show '$showId' to favorites", error)
                    // TODO: Show user feedback
                }
        }
    }

    /**
     * Remove show from favorites
     */
    fun removeFromFavorites(showId: String) {
        viewModelScope.launch {
            Log.d(TAG, "Removing show '$showId' from favorites")
            favoritesService.removeFromFavorites(showId)
                .onSuccess {
                    Log.d(TAG, "Successfully removed show '$showId' from favorites")
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to remove show '$showId' from favorites", error)
                    // TODO: Show user feedback
                }
        }
    }

    /**
     * Clear all favorites
     */
    fun clearFavorites() {
        viewModelScope.launch {
            Log.d(TAG, "Clearing favorites")
            favoritesService.clearFavorites()
                .onSuccess {
                    Log.d(TAG, "Successfully cleared favorites")
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to clear favorites", error)
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
            favoritesService.pinShow(showId)
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
            favoritesService.unpinShow(showId)
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
            val preferredRecordingId = favoritesService.getPreferredRecordingId(showId)
            favoritesService.downloadShow(showId, preferredRecordingId)
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
            favoritesService.cancelShowDownloads(showId)
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
            favoritesService.shareShow(showId)
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
            favoritesService.populateTestData()
                .onSuccess {
                    Log.d(TAG, "Successfully populated test data")
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to populate test data", error)
                }
        }
    }

    /**
     * Import backup from JSON file (supports v3 and legacy migration formats)
     */
    fun importFavorites(uri: Uri, onComplete: (Result<String>) -> Unit) {
        viewModelScope.launch {
            Log.d(TAG, "Importing backup from URI")
            try {
                val jsonString = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().readText()
                    } ?: throw IllegalStateException("Could not open file")
                }
                val result = withContext(Dispatchers.IO) {
                    backupImportExportService.importBackup(jsonString)
                }
                Log.d(TAG, "Backup import completed: ${result.favoritesImported} favorites, ${result.reviewsImported} reviews, ${result.preferencesImported} prefs")
                val summary = buildString {
                    append("Imported ${result.favoritesImported} favorites")
                    if (result.reviewsImported > 0) append(", ${result.reviewsImported} reviews")
                    if (result.tracksImported > 0) append(", ${result.tracksImported} tracks")
                    if (result.preferencesImported > 0) append(", ${result.preferencesImported} recording prefs")
                }
                onComplete(Result.success(summary))
            } catch (e: Exception) {
                Log.e(TAG, "Backup import failed", e)
                onComplete(Result.failure(e))
            }
        }
    }

    // Review support

    private val _currentReview = MutableStateFlow(ShowReview(showId = ""))
    val currentReview: StateFlow<ShowReview> = _currentReview.asStateFlow()

    fun loadReview(showId: String) {
        viewModelScope.launch {
            _currentReview.value = reviewService.getShowReview(showId) ?: ShowReview(showId = showId)
        }
    }

    fun saveReview(
        showId: String,
        notes: String?,
        rating: Float?,
        recordingQuality: Int?,
        playingQuality: Int?,
        standoutPlayers: List<String>
    ) {
        viewModelScope.launch {
            reviewService.updateShowNotes(showId, notes)
            reviewService.updateShowRating(showId, rating)
            reviewService.updateRecordingQuality(showId, recordingQuality)
            reviewService.updatePlayingQuality(showId, playingQuality)

            // Sync standout player tags
            val existingTags = reviewService.getPlayerTags(showId)
            val existingNames = existingTags.map { it.playerName }.toSet()

            // Remove players no longer selected
            for (name in existingNames - standoutPlayers.toSet()) {
                reviewService.removePlayerTag(showId, name)
            }
            // Add newly selected players
            for (name in standoutPlayers.toSet() - existingNames) {
                reviewService.upsertPlayerTag(showId, name, isStandout = true)
            }
        }
    }

    /**
     * Export backup to JSON file (v3 format)
     */
    fun exportFavorites(onComplete: (Result<String>) -> Unit) {
        viewModelScope.launch {
            Log.d(TAG, "Exporting backup (v3)")
            try {
                val jsonString = withContext(Dispatchers.IO) {
                    backupImportExportService.export()
                }

                // Write to Downloads folder
                val downloadsDir = File("/storage/emulated/0/Download/")
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }

                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val dateString = dateFormat.format(Date())
                val filename = "the-deadly-backup-$dateString.json"
                val exportFile = File(downloadsDir, filename)

                withContext(Dispatchers.IO) {
                    exportFile.writeText(jsonString)
                }

                Log.d(TAG, "Backup exported successfully to: ${exportFile.absolutePath}")
                onComplete(Result.success(filename))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export backup", e)
                onComplete(Result.failure(e))
            }
        }
    }

    /**
     * Share a favorite show as a text message (just the URL)
     */
    fun shareAsMessage(show: FavoriteShowViewModel) {
        val url = if (show.bestRecordingId != null) {
            "https://share.thedeadly.app/shows/${show.showId}/recording/${show.bestRecordingId}"
        } else {
            "https://share.thedeadly.app/shows/${show.showId}"
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(Intent.createChooser(intent, "Share via").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    /**
     * Retry loading favorites after error
     */
    fun retry() {
        Log.d(TAG, "Retrying favorites load")
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            error = null
        )
        loadFavorites()
    }

    /**
     * Map domain FavoriteShow to UI FavoriteShowViewModel
     */
    private fun mapToFavoriteShowViewModel(favoriteShow: FavoriteShow): FavoriteShowViewModel {
        return FavoriteShowViewModel(
            showId = favoriteShow.showId,
            date = favoriteShow.date,
            displayDate = favoriteShow.displayDate,
            venue = favoriteShow.venue,
            location = favoriteShow.location,
            rating = favoriteShow.averageRating,
            reviewCount = favoriteShow.totalReviews,
            addedToFavoritesAt = favoriteShow.addedToFavoritesAt,
            isPinned = favoriteShow.isPinned,
            downloadStatus = favoriteShow.downloadStatus,
            isDownloaded = favoriteShow.isDownloaded,
            isDownloading = favoriteShow.isDownloading,
            statusDescription = favoriteShow.statusDescription,
            bestRecordingId = favoriteShow.show.bestRecordingId,
            bestSourceType = favoriteShow.show.bestSourceType,
            coverImageUrl = favoriteShow.show.coverImageUrl,
            recordingCount = favoriteShow.recordingCount,
            hasReview = favoriteShow.hasReview,
            customRating = favoriteShow.customRating,
            lineupMembers = favoriteShow.show.lineup?.members?.map { it.name } ?: emptyList()
        )
    }
}
