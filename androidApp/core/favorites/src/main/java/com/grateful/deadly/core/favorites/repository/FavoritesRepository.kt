package com.grateful.deadly.core.favorites.repository

import android.util.Log
import com.grateful.deadly.core.database.dao.FavoritesDao
import com.grateful.deadly.core.database.dao.RecordingPreferenceDao
import com.grateful.deadly.core.database.dao.ShowReviewDao
import com.grateful.deadly.core.database.entities.FavoriteShowEntity
import com.grateful.deadly.core.database.entities.RecordingPreferenceEntity
import com.grateful.deadly.core.domain.repository.ShowRepository
import com.grateful.deadly.core.media.download.MediaDownloadManager
import com.grateful.deadly.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton
import com.grateful.deadly.core.model.AppDatabase

/**
 * FavoritesRepository - Implementation integrating database and domain layers
 *
 * Combines FavoritesDao (favorites-specific data) with ShowRepository (show data)
 * to provide rich FavoriteShow domain models for the service layer.
 *
 * Follows architecture patterns with reactive Flow-based operations.
 */
@Singleton
class FavoritesRepository @Inject constructor(
    @AppDatabase private val favoritesDao: FavoritesDao,
    @AppDatabase private val showReviewDao: ShowReviewDao,
    @AppDatabase private val recordingPreferenceDao: RecordingPreferenceDao,
    private val showRepository: ShowRepository,
    private val mediaDownloadManager: MediaDownloadManager
) {

    companion object {
        private const val TAG = "FavoritesRepository"
    }

    /**
     * Get all favorite shows as reactive flow with complete show data
     */
    fun getFavoriteShowsFlow(): Flow<List<FavoriteShow>> {
        Log.d(TAG, "getFavoriteShowsFlow() - combining favorites data with show data and download changes")

        // Combine with download change events so any download state change triggers re-evaluation
        val downloadChanges = mediaDownloadManager.observeDownloadChanges()
            .onStart { emit(Unit) }

        return combine(
            favoritesDao.getAllFavoriteShowsFlow(),
            showRepository.getAllShowsFlow(),
            downloadChanges
        ) { favoriteEntities, allShows, _ ->
            Log.d(TAG, "Combining ${favoriteEntities.size} favorite entries with ${allShows.size} shows")

            // Create map of showId -> Show for efficient lookup
            val showsMap = allShows.associateBy { it.id }

            // Fetch review data for all favorite shows
            val showIds = favoriteEntities.map { it.showId }
            val reviewsMap = if (showIds.isNotEmpty()) {
                showReviewDao.getByShowIds(showIds).associateBy { it.showId }
            } else emptyMap()

            // Convert to FavoriteShows, filtering out shows that no longer exist
            favoriteEntities.mapNotNull { favoriteEntity ->
                showsMap[favoriteEntity.showId]?.let { show ->
                    val review = reviewsMap[favoriteEntity.showId]
                    FavoriteShow(
                        show = show,
                        addedToFavoritesAt = favoriteEntity.addedToFavoritesAt,
                        isPinned = favoriteEntity.isPinned,
                        downloadStatus = mediaDownloadManager.getShowDownloadStatus(show.id),
                        notes = review?.notes,
                        customRating = review?.customRating,
                        recordingQuality = review?.recordingQuality,
                        playingQuality = review?.playingQuality
                    )
                }
            }
        }
    }

    /**
     * Add show to favorites
     */
    suspend fun addShowToFavorites(showId: String): Result<Unit> {
        Log.d(TAG, "addShowToFavorites('$showId')")

        return try {
            // Verify show exists in ShowRepository
            val show = showRepository.getShowById(showId)
            if (show == null) {
                Log.w(TAG, "Show '$showId' not found in ShowRepository")
                return Result.failure(Exception("Show not found"))
            }

            // Create favorite entity
            val favoriteEntity = FavoriteShowEntity(
                showId = showId,
                addedToFavoritesAt = System.currentTimeMillis(),
                isPinned = false
            )

            favoritesDao.addToFavorites(favoriteEntity)
            Log.d(TAG, "Successfully added show '$showId' to favorites")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Error adding show '$showId' to favorites", e)
            Result.failure(e)
        }
    }

    /**
     * Remove show from favorites
     */
    suspend fun removeShowFromFavorites(showId: String): Result<Unit> {
        Log.d(TAG, "removeShowFromFavorites('$showId')")

        return try {
            favoritesDao.removeFromFavoritesById(showId)
            Log.d(TAG, "Successfully removed show '$showId' from favorites")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Error removing show '$showId' from favorites", e)
            Result.failure(e)
        }
    }

    /**
     * Check if show is a favorite (reactive)
     */
    fun isShowFavoriteFlow(showId: String): Flow<Boolean> {
        return favoritesDao.isShowFavoriteFlow(showId)
    }

    /**
     * Check if show is a favorite (one-time)
     */
    suspend fun isShowFavorite(showId: String): Boolean {
        return favoritesDao.isShowFavorite(showId)
    }

    /**
     * Pin show
     */
    suspend fun pinShow(showId: String): Result<Unit> {
        Log.d(TAG, "pinShow('$showId')")

        return try {
            favoritesDao.updatePinStatus(showId, true)
            Log.d(TAG, "Successfully pinned show '$showId'")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Error pinning show '$showId'", e)
            Result.failure(e)
        }
    }

    /**
     * Unpin show
     */
    suspend fun unpinShow(showId: String): Result<Unit> {
        Log.d(TAG, "unpinShow('$showId')")

        return try {
            favoritesDao.updatePinStatus(showId, false)
            Log.d(TAG, "Successfully unpinned show '$showId'")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Error unpinning show '$showId'", e)
            Result.failure(e)
        }
    }

    /**
     * Check if show is pinned (reactive)
     */
    fun isShowPinnedFlow(showId: String): Flow<Boolean> {
        return favoritesDao.isShowPinnedFlow(showId)
    }

    /**
     * Get favorites statistics
     */
    fun getFavoritesStatsFlow(): Flow<FavoritesStats> {
        val downloadChanges = mediaDownloadManager.observeDownloadChanges()
            .onStart { emit(Unit) }

        return combine(
            favoritesDao.getFavoriteShowCountFlow(),
            favoritesDao.getPinnedShowCountFlow(),
            downloadChanges
        ) { totalShows, pinnedShows, _ ->
            FavoritesStats(
                totalShows = totalShows,
                totalDownloaded = mediaDownloadManager.getDownloadedShowCount(),
                totalStorageUsed = mediaDownloadManager.getTotalStorageUsed(),
                totalPinned = pinnedShows
            )
        }
    }

    /**
     * Set preferred recording for a show (independent of favorites membership)
     */
    suspend fun setPreferredRecording(showId: String, recordingId: String?) {
        Log.d(TAG, "setPreferredRecording('$showId', '$recordingId')")
        if (recordingId != null) {
            recordingPreferenceDao.upsert(
                RecordingPreferenceEntity(
                    showId = showId,
                    recordingId = recordingId,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            recordingPreferenceDao.delete(showId)
        }
    }

    /**
     * Get preferred recording ID for a show
     */
    suspend fun getPreferredRecordingId(showId: String): String? {
        return recordingPreferenceDao.getRecordingId(showId)
    }

    /**
     * Get downloaded recording ID for a favorite show
     */
    suspend fun getDownloadedRecordingId(showId: String): String? {
        return favoritesDao.getDownloadedRecordingId(showId)
    }

    /**
     * Update the downloaded recording ID for a favorite show
     */
    suspend fun setDownloadedRecording(showId: String, recordingId: String?, format: String? = null) {
        Log.d(TAG, "setDownloadedRecording('$showId', '$recordingId')")
        favoritesDao.updateDownloadedRecording(showId, recordingId, format)
    }

    /**
     * Clear entire favorites
     */
    suspend fun clearFavorites(): Result<Unit> {
        Log.d(TAG, "clearFavorites()")

        return try {
            favoritesDao.clearFavorites()
            Log.d(TAG, "Successfully cleared favorites")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Error clearing favorites", e)
            Result.failure(e)
        }
    }
}
