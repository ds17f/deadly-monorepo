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
    /** "Fan Favorites" — per-decade pools; the section picks its 4-show display set. */
    val popularContent: PopularContent,
    /** When true, render the Fan Favorites carousel on the home screen. */
    val popularEnabled: Boolean,
    /** Card size for the Fan Favorites carousel: "small" or "large". */
    val popularCardSize: String,
    /** Currently-selected decade filter for the Fan Favorites rail (from Settings). */
    val popularDecade: PopularDecade,
    /** How many rows of Recently Played to render (1..4). Each row = 2 shows. */
    val recentRows: Int,
    /** Card size for the Trending carousel: "small" or "large". */
    val trendingCardSize: String,
    /** Card size for the Today In History carousel: "small" or "large". */
    val todayCardSize: String,
    /** Card size for the Featured Collections carousel: "small" or "large". */
    val collectionsCardSize: String,
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
            popularContent = PopularContent.initial(),
            popularEnabled = true,
            popularCardSize = "small",
            popularDecade = PopularDecade.ALL,
            recentRows = 2,
            trendingCardSize = "small",
            todayCardSize = "large",
            collectionsCardSize = "large",
            lastRefresh = 0L
        )
    }
}
