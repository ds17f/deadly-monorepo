package com.grateful.deadly.core.api.favorites

import com.grateful.deadly.core.model.*
import kotlinx.coroutines.flow.StateFlow

/**
 * Favorites Service - Clean service interface for favorites functionality
 *
 * Following architecture patterns with service-oriented design,
 * this interface defines all favorites operations with reactive StateFlow
 * for UI state management and proper Result handling for operations.
 */
interface FavoritesService {

    /**
     * Load favorite shows into service state
     */
    suspend fun loadFavoriteShows(): Result<Unit>

    /**
     * Get current favorite shows as reactive state
     */
    fun getCurrentShows(): StateFlow<List<FavoriteShow>>

    /**
     * Get current favorites statistics
     */
    fun getFavoritesStats(): StateFlow<FavoritesStats>

    /**
     * Add a show to the user's favorites
     */
    suspend fun addToFavorites(showId: String): Result<Unit>

    /**
     * Remove a show from the user's favorites
     */
    suspend fun removeFromFavorites(showId: String): Result<Unit>

    /**
     * Clear all shows from the user's favorites
     */
    suspend fun clearFavorites(): Result<Unit>

    /**
     * Check if a show is in the user's favorites (reactive)
     */
    fun isShowFavorite(showId: String): StateFlow<Boolean>

    /**
     * Pin a show for priority display
     */
    suspend fun pinShow(showId: String): Result<Unit>

    /**
     * Unpin a previously pinned show
     */
    suspend fun unpinShow(showId: String): Result<Unit>

    /**
     * Check if a show is pinned (reactive)
     */
    fun isShowPinned(showId: String): StateFlow<Boolean>

    /**
     * Set the preferred recording for a favorite show
     */
    suspend fun setPreferredRecording(showId: String, recordingId: String?)

    /**
     * Get the preferred recording ID for a favorite show, or null if not set
     */
    suspend fun getPreferredRecordingId(showId: String): String?

    /**
     * Get the downloaded recording ID for a favorite show, or null if not downloaded
     */
    suspend fun getDownloadedRecordingId(showId: String): String?

    /**
     * Update which recording is tracked as downloaded for a show
     */
    suspend fun setDownloadedRecording(showId: String, recordingId: String?, format: String? = null)

    /**
     * Download a show
     */
    suspend fun downloadShow(showId: String, recordingId: String? = null): Result<Unit>

    /**
     * Cancel show downloads
     */
    suspend fun cancelShowDownloads(showId: String): Result<Unit>

    /**
     * Pause show downloads
     */
    fun pauseShowDownloads(showId: String)

    /**
     * Resume show downloads
     */
    fun resumeShowDownloads(showId: String)

    /**
     * Get download status for a show (reactive)
     */
    fun getDownloadStatus(showId: String): StateFlow<FavoritesDownloadStatus>

    /**
     * Share a show
     */
    suspend fun shareShow(showId: String): Result<Unit>

    /**
     * Populate favorites with test data for development
     * Only implemented in stub - no-op in real implementations
     */
    suspend fun populateTestData(): Result<Unit> {
        return Result.success(Unit)
    }
}
