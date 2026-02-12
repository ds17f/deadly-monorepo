package com.deadly.v2.core.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * V2 Recent Show database entity
 * 
 * Tracks recently played shows using UPSERT pattern - single record per show
 * that gets updated each time the show is played. This eliminates the need
 * for complex GROUP BY queries while maintaining deduplication.
 * 
 * Key design decisions:
 * - showId as primary key ensures one record per show
 * - lastPlayedTimestamp updated on each play for ordering
 * - firstPlayedTimestamp preserved for analytics
 * - totalPlayCount incremented on each play
 * - Indexed on lastPlayedTimestamp for efficient ORDER BY queries
 */
@Entity(
    tableName = "recent_shows",
    indices = [
        Index(value = ["lastPlayedTimestamp"], orders = [Index.Order.DESC])
    ]
)
@Serializable
data class RecentShowEntity(
    @PrimaryKey
    val showId: String,
    
    /** 
     * Timestamp of the most recent play of this show.
     * Updated each time any track from this show is played.
     * Used for ordering recent shows by recency.
     */
    val lastPlayedTimestamp: Long,
    
    /**
     * Timestamp when this show was first played by the user.
     * Set once and never changed, useful for analytics.
     */
    val firstPlayedTimestamp: Long,
    
    /**
     * Total number of times this show has been played.
     * Incremented each time any track from this show is played
     * (after passing the meaningful play threshold).
     */
    val totalPlayCount: Int
) {
    companion object {
        /**
         * Create a new recent show entry for first-time play
         */
        fun createNew(showId: String, timestamp: Long = System.currentTimeMillis()): RecentShowEntity {
            return RecentShowEntity(
                showId = showId,
                lastPlayedTimestamp = timestamp,
                firstPlayedTimestamp = timestamp,
                totalPlayCount = 1
            )
        }
        
        /**
         * Update an existing entry with new play
         */
        fun RecentShowEntity.withNewPlay(timestamp: Long = System.currentTimeMillis()): RecentShowEntity {
            return copy(
                lastPlayedTimestamp = timestamp,
                totalPlayCount = totalPlayCount + 1
            )
        }
        
        /**
         * Check if this show was played recently (within last 7 days)
         */
        fun RecentShowEntity.isPlayedRecently(daysThreshold: Int = 7): Boolean {
            val cutoff = System.currentTimeMillis() - (daysThreshold * 24 * 60 * 60 * 1000L)
            return lastPlayedTimestamp > cutoff
        }
    }
}