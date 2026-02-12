package com.deadly.v2.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.deadly.v2.core.database.entities.RecentShowEntity
import kotlinx.coroutines.flow.Flow

/**
 * V2 Recent Show database access object
 * 
 * Provides database operations for recent show tracking using UPSERT pattern.
 * Since each show has only one record (updated on each play), queries are
 * simple and efficient without need for GROUP BY operations.
 */
@Dao
interface RecentShowDao {
    
    /**
     * Get recently played shows ordered by most recent play.
     * Simple ORDER BY query since UPSERT ensures one record per show.
     * 
     * @param limit Maximum number of recent shows to return (default 8 for UI)
     * @return List of recent show entities ordered by lastPlayedTimestamp DESC
     */
    @Query("""
        SELECT * FROM recent_shows 
        ORDER BY lastPlayedTimestamp DESC 
        LIMIT :limit
    """)
    suspend fun getRecentShows(limit: Int = 8): List<RecentShowEntity>
    
    /**
     * Get recently played shows as Flow for reactive UI updates.
     * 
     * @param limit Maximum number of recent shows to return
     * @return Flow of recent show entities that updates automatically
     */
    @Query("""
        SELECT * FROM recent_shows 
        ORDER BY lastPlayedTimestamp DESC 
        LIMIT :limit
    """)
    fun getRecentShowsFlow(limit: Int = 8): Flow<List<RecentShowEntity>>
    
    /**
     * Get a specific show's recent play record.
     * Used for UPSERT logic to determine if show exists.
     * 
     * @param showId The show ID to lookup
     * @return RecentShowEntity if exists, null otherwise
     */
    @Query("SELECT * FROM recent_shows WHERE showId = :showId")
    suspend fun getShowById(showId: String): RecentShowEntity?
    
    /**
     * Insert a new recent show record.
     * Used when recording first play of a show.
     * 
     * @param entity The recent show entity to insert
     */
    @Insert
    suspend fun insert(entity: RecentShowEntity)
    
    /**
     * Update an existing show's play information.
     * Used when recording subsequent plays of same show.
     * 
     * @param showId The show ID to update
     * @param timestamp New lastPlayedTimestamp 
     * @param playCount New totalPlayCount (should be incremented)
     */
    @Query("""
        UPDATE recent_shows 
        SET lastPlayedTimestamp = :timestamp, totalPlayCount = :playCount 
        WHERE showId = :showId
    """)
    suspend fun updateShow(showId: String, timestamp: Long, playCount: Int)
    
    /**
     * Remove a specific show from recent shows.
     * Useful for user privacy or "hide from recent" functionality.
     * 
     * @param showId The show ID to remove
     */
    @Query("DELETE FROM recent_shows WHERE showId = :showId")
    suspend fun removeShow(showId: String)
    
    /**
     * Clear all recent shows history.
     * For user privacy or reset functionality.
     */
    @Query("DELETE FROM recent_shows")
    suspend fun clearAll()
    
    /**
     * Get count of shows in recent history.
     * Useful for analytics and debugging.
     * 
     * @return Total number of shows in recent_shows table
     */
    @Query("SELECT COUNT(*) FROM recent_shows")
    suspend fun getRecentShowCount(): Int
    
    /**
     * Get shows played within a specific time range.
     * Useful for analytics like "shows played this week".
     * 
     * @param startTimestamp Start of time range (inclusive)
     * @param endTimestamp End of time range (inclusive)
     * @return List of shows played within the time range
     */
    @Query("""
        SELECT * FROM recent_shows 
        WHERE lastPlayedTimestamp BETWEEN :startTimestamp AND :endTimestamp
        ORDER BY lastPlayedTimestamp DESC
    """)
    suspend fun getShowsPlayedInRange(startTimestamp: Long, endTimestamp: Long): List<RecentShowEntity>
    
    /**
     * Get most frequently played shows from recent history.
     * Useful for "most played" analytics.
     * 
     * @param limit Maximum number of shows to return
     * @return List of shows ordered by play count descending
     */
    @Query("""
        SELECT * FROM recent_shows 
        ORDER BY totalPlayCount DESC, lastPlayedTimestamp DESC
        LIMIT :limit
    """)
    suspend fun getMostPlayedRecentShows(limit: Int = 10): List<RecentShowEntity>
    
    /**
     * Remove old shows beyond retention period.
     * Useful for privacy/storage management.
     * 
     * @param cutoffTimestamp Shows older than this will be deleted
     * @return Number of shows deleted
     */
    @Query("DELETE FROM recent_shows WHERE lastPlayedTimestamp < :cutoffTimestamp")
    suspend fun deleteOldShows(cutoffTimestamp: Long): Int
}