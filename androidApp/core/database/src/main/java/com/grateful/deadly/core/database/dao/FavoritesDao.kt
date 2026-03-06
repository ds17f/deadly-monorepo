package com.grateful.deadly.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.grateful.deadly.core.database.entities.FavoriteShowEntity
import kotlinx.coroutines.flow.Flow

/**
 * Favorites DAO - Database access for favorite show operations
 *
 * Provides reactive Flow-based queries and full CRUD operations
 * for user's favorites management with pin support and sorting.
 */
@Dao
interface FavoritesDao {

    // Core CRUD operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToFavorites(favoriteShow: FavoriteShowEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addMultipleToFavorites(favoriteShows: List<FavoriteShowEntity>)

    @Delete
    suspend fun removeFromFavorites(favoriteShow: FavoriteShowEntity)

    @Query("DELETE FROM favorite_shows WHERE showId = :showId")
    suspend fun removeFromFavoritesById(showId: String)

    @Update
    suspend fun updateFavoriteShow(favoriteShow: FavoriteShowEntity)

    // Reactive queries for UI
    @Query("SELECT * FROM favorite_shows ORDER BY isPinned DESC, addedToFavoritesAt DESC")
    fun getAllFavoriteShowsFlow(): Flow<List<FavoriteShowEntity>>

    @Query("SELECT * FROM favorite_shows WHERE isPinned = 1 ORDER BY addedToFavoritesAt DESC")
    fun getPinnedFavoriteShowsFlow(): Flow<List<FavoriteShowEntity>>

    @Query("SELECT * FROM favorite_shows ORDER BY isPinned DESC, addedToFavoritesAt DESC")
    suspend fun getAllFavoriteShows(): List<FavoriteShowEntity>

    // Individual show queries
    @Query("SELECT * FROM favorite_shows WHERE showId = :showId")
    suspend fun getFavoriteShowById(showId: String): FavoriteShowEntity?

    @Query("SELECT * FROM favorite_shows WHERE showId = :showId")
    fun getFavoriteShowByIdFlow(showId: String): Flow<FavoriteShowEntity?>

    // Status checks
    @Query("SELECT EXISTS(SELECT 1 FROM favorite_shows WHERE showId = :showId)")
    suspend fun isShowFavorite(showId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_shows WHERE showId = :showId)")
    fun isShowFavoriteFlow(showId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_shows WHERE showId = :showId AND isPinned = 1)")
    suspend fun isShowPinned(showId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_shows WHERE showId = :showId AND isPinned = 1)")
    fun isShowPinnedFlow(showId: String): Flow<Boolean>

    // Pin management
    @Query("UPDATE favorite_shows SET isPinned = :isPinned WHERE showId = :showId")
    suspend fun updatePinStatus(showId: String, isPinned: Boolean)

    @Query("UPDATE favorite_shows SET notes = :notes WHERE showId = :showId")
    suspend fun updateNotes(showId: String, notes: String?)

    @Query("UPDATE favorite_shows SET customRating = :rating WHERE showId = :showId")
    suspend fun updateCustomRating(showId: String, rating: Float?)

    @Query("UPDATE favorite_shows SET recordingQuality = :quality WHERE showId = :showId")
    suspend fun updateRecordingQuality(showId: String, quality: Int?)

    @Query("UPDATE favorite_shows SET playingQuality = :quality WHERE showId = :showId")
    suspend fun updatePlayingQuality(showId: String, quality: Int?)

    // Statistics
    @Query("SELECT COUNT(*) FROM favorite_shows")
    suspend fun getFavoriteShowCount(): Int

    @Query("SELECT COUNT(*) FROM favorite_shows WHERE isPinned = 1")
    fun getPinnedShowCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM favorite_shows")
    fun getFavoriteShowCountFlow(): Flow<Int>

    // Download tracking
    @Query("UPDATE favorite_shows SET downloadedRecordingId = :recordingId, downloadedFormat = :format WHERE showId = :showId")
    suspend fun updateDownloadedRecording(showId: String, recordingId: String?, format: String?)

    @Query("SELECT downloadedRecordingId FROM favorite_shows WHERE showId = :showId")
    suspend fun getDownloadedRecordingId(showId: String): String?

    // Bulk operations
    @Query("DELETE FROM favorite_shows")
    suspend fun clearFavorites()

    @Query("UPDATE favorite_shows SET isPinned = 0")
    suspend fun unpinAllShows()
}
