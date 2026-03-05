package com.grateful.deadly.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grateful.deadly.core.database.entities.ShowReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShowReviewDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(review: ShowReviewEntity)

    @Query("SELECT * FROM show_reviews WHERE showId = :showId")
    suspend fun getByShowId(showId: String): ShowReviewEntity?

    @Query("SELECT * FROM show_reviews WHERE showId = :showId")
    fun getByShowIdFlow(showId: String): Flow<ShowReviewEntity?>

    @Query("SELECT * FROM show_reviews")
    suspend fun getAll(): List<ShowReviewEntity>

    @Query("SELECT * FROM show_reviews WHERE showId IN (:showIds)")
    suspend fun getByShowIds(showIds: List<String>): List<ShowReviewEntity>

    @Query("UPDATE show_reviews SET notes = :notes, updatedAt = :updatedAt WHERE showId = :showId")
    suspend fun updateNotes(showId: String, notes: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE show_reviews SET customRating = :rating, updatedAt = :updatedAt WHERE showId = :showId")
    suspend fun updateCustomRating(showId: String, rating: Float?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE show_reviews SET recordingQuality = :quality, reviewedRecordingId = :recordingId, updatedAt = :updatedAt WHERE showId = :showId")
    suspend fun updateRecordingQuality(showId: String, quality: Int?, recordingId: String? = null, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE show_reviews SET playingQuality = :quality, updatedAt = :updatedAt WHERE showId = :showId")
    suspend fun updatePlayingQuality(showId: String, quality: Int?, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM show_reviews WHERE showId = :showId")
    suspend fun deleteByShowId(showId: String)

    @Query("DELETE FROM show_reviews")
    suspend fun deleteAll()
}
