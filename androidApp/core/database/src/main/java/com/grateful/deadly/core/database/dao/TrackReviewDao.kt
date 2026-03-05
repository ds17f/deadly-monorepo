package com.grateful.deadly.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grateful.deadly.core.database.entities.TrackReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackReviewDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(review: TrackReviewEntity)

    @Query("SELECT * FROM track_reviews WHERE showId = :showId ORDER BY trackNumber ASC, trackTitle ASC")
    suspend fun getReviewsForShow(showId: String): List<TrackReviewEntity>

    @Query("SELECT * FROM track_reviews WHERE showId = :showId ORDER BY trackNumber ASC, trackTitle ASC")
    fun getReviewsForShowFlow(showId: String): Flow<List<TrackReviewEntity>>

    @Query("SELECT * FROM track_reviews WHERE showId = :showId AND trackTitle = :trackTitle AND (recordingId = :recordingId OR (recordingId IS NULL AND :recordingId IS NULL)) LIMIT 1")
    suspend fun getReview(showId: String, trackTitle: String, recordingId: String?): TrackReviewEntity?

    @Query("SELECT * FROM track_reviews WHERE showId = :showId AND trackTitle = :trackTitle AND (recordingId = :recordingId OR (recordingId IS NULL AND :recordingId IS NULL)) LIMIT 1")
    fun getReviewFlow(showId: String, trackTitle: String, recordingId: String?): Flow<TrackReviewEntity?>

    @Query("DELETE FROM track_reviews WHERE showId = :showId AND trackTitle = :trackTitle AND (recordingId = :recordingId OR (recordingId IS NULL AND :recordingId IS NULL))")
    suspend fun deleteReview(showId: String, trackTitle: String, recordingId: String?)

    @Query("DELETE FROM track_reviews WHERE showId = :showId")
    suspend fun deleteReviewsForShow(showId: String)

    @Query("SELECT COUNT(*) FROM track_reviews WHERE showId = :showId")
    suspend fun getReviewCountForShow(showId: String): Int

    @Query("SELECT * FROM track_reviews WHERE thumbs = 1 ORDER BY updatedAt DESC")
    suspend fun getThumbsUpTracks(): List<TrackReviewEntity>
}
