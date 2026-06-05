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

    // UI reads exclude tombstones (deletedAt set). Sync deletes soft-delete
    // so the deletion can propagate; the push path uses the *IncludingTombstones
    // reader to still find those rows.
    @Query("SELECT * FROM show_reviews WHERE showId = :showId AND deletedAt IS NULL")
    suspend fun getByShowId(showId: String): ShowReviewEntity?

    /** Includes tombstoned rows — used by the sync push to honor deletes. */
    @Query("SELECT * FROM show_reviews WHERE showId = :showId")
    suspend fun getByShowIdIncludingTombstones(showId: String): ShowReviewEntity?

    @Query("SELECT * FROM show_reviews WHERE showId = :showId AND deletedAt IS NULL")
    fun getByShowIdFlow(showId: String): Flow<ShowReviewEntity?>

    @Query("SELECT * FROM show_reviews WHERE deletedAt IS NULL")
    fun getAllFlow(): Flow<List<ShowReviewEntity>>

    @Query("SELECT * FROM show_reviews WHERE deletedAt IS NULL")
    suspend fun getAll(): List<ShowReviewEntity>

    @Query("SELECT * FROM show_reviews WHERE showId IN (:showIds) AND deletedAt IS NULL")
    suspend fun getByShowIds(showIds: List<String>): List<ShowReviewEntity>

    /** Tombstone a review so its deletion syncs (LWW by updatedAt). */
    @Query("UPDATE show_reviews SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE showId = :showId")
    suspend fun softDelete(showId: String, deletedAt: Long = System.currentTimeMillis(), updatedAt: Long = System.currentTimeMillis())

    /** Bump updatedAt so a player-tag change (tags travel with the review) carries an LWW timestamp. */
    @Query("UPDATE show_reviews SET updatedAt = :updatedAt WHERE showId = :showId")
    suspend fun touchUpdatedAt(showId: String, updatedAt: Long = System.currentTimeMillis())

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
