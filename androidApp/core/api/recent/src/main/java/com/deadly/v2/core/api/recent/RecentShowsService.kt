package com.deadly.v2.core.api.recent

import com.deadly.v2.core.model.Show
import kotlinx.coroutines.flow.StateFlow

/**
 * V2 RecentShowsService API contract
 * 
 * Provides recently played shows based on user listening behavior.
 * Tracks show-level plays and maintains chronological history for
 * quick access to recently enjoyed content.
 * 
 * Key responsibilities:
 * - Track when shows are played (any meaningful track play from show counts)
 * - Maintain recency-ordered list of played shows with deduplication  
 * - Provide reactive updates when new shows are played
 * - Filter for meaningful listens using hybrid threshold (30sec OR 25% of track)
 * - Use UPSERT pattern to maintain single record per show
 * 
 * Architecture:
 * - Observes MediaControllerRepository StateFlows for track changes
 * - Persists to database using RecentShowEntity/RecentShowDao
 * - Exposes reactive StateFlow for UI consumption
 * - Clean separation from business logic services
 */
interface RecentShowsService {
    
    /**
     * StateFlow of recently played shows, ordered by most recent first.
     * Updates automatically when new shows are played and pass meaningful play threshold.
     * 
     * Uses UPSERT pattern so each show appears only once, with most recent play time
     * determining order. Typically limited to 8-10 shows for optimal UI performance.
     * 
     * @return StateFlow<List<Show>> that emits updates when recent shows change
     */
    val recentShows: StateFlow<List<Show>>
    
    /**
     * Manually record that a show was played.
     * 
     * Typically called internally when track-level observation detects meaningful play,
     * but can be used for explicit show-level tracking (e.g., "Play Random Track" button).
     * 
     * Uses UPSERT logic:
     * - If show exists: update lastPlayedTimestamp, increment playCount
     * - If new show: insert with current timestamp and playCount = 1
     * 
     * @param showId The ID of the show that was played
     * @param playTimestamp When the play occurred (defaults to current time)
     */
    suspend fun recordShowPlay(showId: String, playTimestamp: Long = System.currentTimeMillis())
    
    /**
     * Get recent shows with custom limit.
     * 
     * Useful for different UI contexts that need different amounts:
     * - HomeScreen: 8 shows for quick access
     * - RecentShowsScreen: 20+ shows for full history
     * - Recommendations: 5 shows for "more like recent" suggestions
     * 
     * @param limit Maximum number of recent shows to return
     * @return List of shows ordered by most recent play first
     */
    suspend fun getRecentShows(limit: Int = 8): List<Show>
    
    /**
     * Check if a specific show is in the recent shows list.
     * 
     * Useful for:
     * - UI highlighting ("Recently Played" badge)
     * - Conditional behavior (skip recent shows in discovery)
     * - Analytics (track re-plays of recent content)
     * 
     * @param showId The show ID to check
     * @return True if show is in recent shows list
     */
    suspend fun isShowInRecent(showId: String): Boolean
    
    /**
     * Remove a specific show from recent shows history.
     * 
     * User privacy feature - allows hiding specific shows from recent list.
     * Useful for:
     * - "Hide from Recent" user action
     * - Content filtering preferences
     * - Privacy after sharing device
     * 
     * @param showId The show ID to remove from recent history
     */
    suspend fun removeShow(showId: String)
    
    /**
     * Clear all recent shows history.
     * 
     * Complete privacy reset - removes all recent show tracking.
     * Useful for:
     * - User privacy controls
     * - Account switching
     * - Fresh start after profile changes
     */
    suspend fun clearRecentShows()
    
    /**
     * Get statistics about recent shows tracking.
     * 
     * Provides analytics data for:
     * - Total shows in recent history
     * - Most played recent show
     * - Listening patterns analysis
     * - Debug information for development
     * 
     * @return Map with statistics keys and values
     */
    suspend fun getRecentShowsStats(): Map<String, Any>
}