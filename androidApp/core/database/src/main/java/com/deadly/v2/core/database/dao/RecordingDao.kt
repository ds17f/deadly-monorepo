package com.deadly.v2.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.deadly.v2.core.database.entities.RecordingEntity

@Dao
interface RecordingDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: RecordingEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecordings(recordings: List<RecordingEntity>): List<Long>
    
    @Query("SELECT * FROM recordings WHERE identifier = :identifier")
    suspend fun getRecordingById(identifier: String): RecordingEntity?
    
    @Query("SELECT * FROM recordings WHERE show_id = :showId ORDER BY rating DESC")
    suspend fun getRecordingsForShow(showId: String): List<RecordingEntity>
    
    @Query("SELECT * FROM recordings WHERE show_id = :showId ORDER BY rating DESC LIMIT 1")
    suspend fun getBestRecordingForShow(showId: String): RecordingEntity?
    
    @Query("SELECT * FROM recordings WHERE source_type = :sourceType ORDER BY rating DESC")
    suspend fun getRecordingsBySourceType(sourceType: String): List<RecordingEntity>
    
    @Query("SELECT COUNT(*) FROM recordings")
    suspend fun getRecordingCount(): Int
    
    @Query("SELECT COUNT(*) FROM recordings WHERE show_id = :showId")
    suspend fun getRecordingCountForShow(showId: String): Int
    
    @Query("DELETE FROM recordings WHERE show_id = :showId")
    suspend fun deleteRecordingsForShow(showId: String)
    
    @Query("DELETE FROM recordings")
    suspend fun deleteAllRecordings()
    
    /**
     * Get top-rated recordings across all shows.
     * Simplified without venue JOIN since venue data is in shows table.
     */
    @Query("""
        SELECT * FROM recordings
        WHERE rating > :minRating AND review_count >= :minReviews
        ORDER BY rating DESC, review_count DESC
        LIMIT :limit
    """)
    suspend fun getTopRatedRecordings(
        minRating: Double = 2.0,
        minReviews: Int = 5,
        limit: Int = 50
    ): List<RecordingEntity>
    
    /**
     * Get recording statistics for analysis and debugging.
     */
    @Query("""
        SELECT 
            source_type,
            COUNT(*) as count,
            AVG(rating) as avg_rating,
            AVG(review_count) as avg_reviews
        FROM recordings 
        WHERE source_type IS NOT NULL
        GROUP BY source_type
        ORDER BY count DESC
    """)
    suspend fun getRecordingStatisticsBySourceType(): List<RecordingStatistics>
}

// Simplified data class for statistics
data class RecordingStatistics(
    @ColumnInfo(name = "source_type") val sourceType: String,
    val count: Int,
    @ColumnInfo(name = "avg_rating") val avgRating: Double,
    @ColumnInfo(name = "avg_reviews") val avgReviews: Double
)