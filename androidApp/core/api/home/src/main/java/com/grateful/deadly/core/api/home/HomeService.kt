package com.grateful.deadly.core.api.home

import com.grateful.deadly.core.model.Show
import com.grateful.deadly.core.model.DeadCollection
import kotlinx.coroutines.flow.StateFlow

/**
 * HomeService - Service interface for home screen content
 * 
 * Orchestrates data from multiple sources to provide unified home experience:
 * - Recent shows from user activity
 * - Today in Grateful Dead History from date-based queries  
 * - Featured collections from curated data
 * 
 * Follows architecture patterns with StateFlow for reactive UI updates.
 */
interface HomeService {
    
    /**
     * Reactive home content state for UI consumption
     * Combines data from recent shows, history, and collections services
     */
    val homeContent: StateFlow<HomeContent>
    
    /**
     * Refresh all home content from underlying services
     * @return Result indicating success or failure with error details
     */
    suspend fun refreshAll(): Result<Unit>

    /** Advance the trending window preference one step (Day → Week → Month → All → Day). */
    fun cycleTrendingWindow()
}

/**
 * Comprehensive home content data model
 * 
 * Contains all data needed for home screen UI components
 */
data class HomeContent(
    val recentShows: List<Show>,
    val todayInHistory: List<Show>,
    val featuredCollections: List<DeadCollection>,
    /** Shows for the user's currently-selected trending window. */
    val trendingShows: List<Show>,
    /** Which window [trendingShows] came from — drives section subtitle / docs. */
    val trendingWindow: TrendingWindow,
    /** When true, Trending renders above Today in History; otherwise below. */
    val trendingAboveToday: Boolean,
    val lastRefresh: Long
) {
    companion object {
        fun initial() = HomeContent(
            recentShows = emptyList(),
            todayInHistory = emptyList(),
            featuredCollections = emptyList(),
            trendingShows = emptyList(),
            trendingWindow = TrendingWindow.NOW,
            trendingAboveToday = false,
            lastRefresh = 0L
        )
    }
}
