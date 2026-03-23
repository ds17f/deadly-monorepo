package com.grateful.deadly.core.favorites.service

import android.util.Log
import com.grateful.deadly.core.database.AnalyticsService
import com.grateful.deadly.core.api.favorites.FavoritesService
import com.grateful.deadly.core.domain.repository.ShowRepository
import com.grateful.deadly.core.media.download.MediaDownloadManager
import com.grateful.deadly.core.media.repository.MediaControllerRepository
import com.grateful.deadly.core.model.*
import com.grateful.deadly.core.favorites.repository.FavoritesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * FavoritesServiceImpl - Production implementation
 *
 * Phase 3 Implementation:
 * - Database entities (FavoriteShowEntity)
 * - Database access (FavoritesDao)
 * - Repository layer (FavoritesRepository)
 * - Direct delegation architecture
 *
 *
 */
@Singleton
class FavoritesServiceImpl @Inject constructor(
    private val showRepository: ShowRepository,
    private val mediaControllerRepository: MediaControllerRepository,
    private val favoritesRepository: FavoritesRepository,
    private val shareService: ShareService,
    private val mediaDownloadManager: MediaDownloadManager,
    private val analyticsService: AnalyticsService,
    @Named("FavoritesApplicationScope") private val coroutineScope: CoroutineScope
) : FavoritesService {

    companion object {
        private const val TAG = "FavoritesServiceImpl"
    }

    // Real reactive StateFlows backed by database
    private val _currentShows: StateFlow<List<FavoriteShow>> = favoritesRepository
        .getFavoriteShowsFlow()
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _favoritesStats: StateFlow<FavoritesStats> = favoritesRepository
        .getFavoritesStatsFlow()
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FavoritesStats(0, 0, 0, 0)
        )

    init {
        Log.d(TAG, "FavoritesServiceImpl initialized with database architecture")
        Log.d(TAG, "Dependencies: ShowRepository=${showRepository::class.simpleName}, FavoritesRepository=${favoritesRepository::class.simpleName}")
    }

    // Load favorite shows (reactive via database StateFlow)
    override suspend fun loadFavoriteShows(): Result<Unit> {
        Log.d(TAG, "loadFavoriteShows() - loading from database")
        // StateFlow automatically loads data reactively from database
        return Result.success(Unit)
    }

    // Get current shows from database StateFlow
    override fun getCurrentShows(): StateFlow<List<FavoriteShow>> {
        Log.d(TAG, "getCurrentShows() - returning database StateFlow")
        return _currentShows
    }

    // Get favorites statistics from database StateFlow
    override fun getFavoritesStats(): StateFlow<FavoritesStats> {
        Log.d(TAG, "getFavoritesStats() - returning database StateFlow")
        return _favoritesStats
    }

    // Add show to favorites
    override suspend fun addToFavorites(showId: String): Result<Unit> {
        Log.d(TAG, "addToFavorites('$showId') - using FavoritesRepository")
        val result = favoritesRepository.addShowToFavorites(showId)
        if (result.isSuccess) analyticsService.track("feature_use", mapOf("feature" to "add_favorite"))
        return result
    }

    // Remove show from favorites
    override suspend fun removeFromFavorites(showId: String): Result<Unit> {
        Log.d(TAG, "removeFromFavorites('$showId') - using FavoritesRepository")
        val result = favoritesRepository.removeShowFromFavorites(showId)
        if (result.isSuccess) analyticsService.track("feature_use", mapOf("feature" to "remove_favorite"))
        return result
    }

    // Clear entire favorites
    override suspend fun clearFavorites(): Result<Unit> {
        Log.d(TAG, "clearFavorites() - using FavoritesRepository")
        return favoritesRepository.clearFavorites()
    }

    // Check if show is a favorite
    override fun isShowFavorite(showId: String): StateFlow<Boolean> {
        Log.d(TAG, "isShowFavorite('$showId') - returning database StateFlow")
        return favoritesRepository.isShowFavoriteFlow(showId)
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false
            )
    }

    // Pin show
    override suspend fun pinShow(showId: String): Result<Unit> {
        Log.d(TAG, "pinShow('$showId') - using FavoritesRepository")
        return favoritesRepository.pinShow(showId)
    }

    // Unpin show
    override suspend fun unpinShow(showId: String): Result<Unit> {
        Log.d(TAG, "unpinShow('$showId') - using FavoritesRepository")
        return favoritesRepository.unpinShow(showId)
    }

    // Check if show is pinned
    override fun isShowPinned(showId: String): StateFlow<Boolean> {
        Log.d(TAG, "isShowPinned('$showId') - returning database StateFlow")
        return favoritesRepository.isShowPinnedFlow(showId)
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false
            )
    }

    override suspend fun setPreferredRecording(showId: String, recordingId: String?) {
        Log.d(TAG, "setPreferredRecording('$showId', '$recordingId')")
        favoritesRepository.setPreferredRecording(showId, recordingId)
    }

    override suspend fun getPreferredRecordingId(showId: String): String? {
        return favoritesRepository.getPreferredRecordingId(showId)
    }

    override suspend fun getDownloadedRecordingId(showId: String): String? {
        return favoritesRepository.getDownloadedRecordingId(showId)
    }

    override suspend fun setDownloadedRecording(showId: String, recordingId: String?, format: String?) {
        Log.d(TAG, "setDownloadedRecording('$showId', '$recordingId')")
        favoritesRepository.setDownloadedRecording(showId, recordingId, format)
    }

    override suspend fun downloadShow(showId: String, recordingId: String?): Result<Unit> {
        Log.d(TAG, "downloadShow('$showId', recording=$recordingId)")
        analyticsService.track("feature_use", mapOf("feature" to "download_show"))
        // Auto-add to favorites if not already present
        if (!favoritesRepository.isShowFavorite(showId)) {
            favoritesRepository.addShowToFavorites(showId)
        }
        val result = mediaDownloadManager.downloadShow(showId, recordingId)
        if (result.isSuccess) {
            // Track which recording was downloaded so conflict detection works later
            val resolvedId = recordingId ?: showRepository.getBestRecordingForShow(showId)?.identifier
            if (resolvedId != null) {
                favoritesRepository.setDownloadedRecording(showId, resolvedId)
            }
        }
        return result
    }

    override suspend fun cancelShowDownloads(showId: String): Result<Unit> {
        Log.d(TAG, "cancelShowDownloads('$showId')")
        return try {
            mediaDownloadManager.removeShowDownloads(showId)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling downloads for $showId", e)
            Result.failure(e)
        }
    }

    override fun pauseShowDownloads(showId: String) {
        Log.d(TAG, "pauseShowDownloads('$showId')")
        mediaDownloadManager.pauseShowDownloads(showId)
    }

    override fun resumeShowDownloads(showId: String) {
        Log.d(TAG, "resumeShowDownloads('$showId')")
        mediaDownloadManager.resumeShowDownloads(showId)
    }

    override fun getDownloadStatus(showId: String): StateFlow<FavoritesDownloadStatus> {
        return mediaDownloadManager.observeShowDownloadProgress(showId)
            .map { it.status }
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = mediaDownloadManager.getShowDownloadStatus(showId)
            )
    }

    // Share show using ShareService
    override suspend fun shareShow(showId: String): Result<Unit> {
        Log.d(TAG, "shareShow('$showId') - using ShareService")

        return try {
            // Get show data from repository
            val show = showRepository.getShowById(showId)
            if (show == null) {
                Log.w(TAG, "Show not found for sharing: $showId")
                return Result.failure(Exception("Show not found: $showId"))
            }

            // Get best recording for the show
            val recording = showRepository.getBestRecordingForShow(showId)
            if (recording == null) {
                Log.w(TAG, "No recording found for sharing show: $showId")
                return Result.failure(Exception("No recording found for show: $showId"))
            }

            Log.d(TAG, "Sharing show: ${show.displayTitle} with recording: ${recording.identifier}")
            shareService.shareShow(show, recording)

            Log.d(TAG, "Successfully shared show: ${show.displayTitle}")
            analyticsService.track("feature_use", mapOf("feature" to "share_show"))
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Error sharing show '$showId'", e)
            Result.failure(e)
        }
    }
}
