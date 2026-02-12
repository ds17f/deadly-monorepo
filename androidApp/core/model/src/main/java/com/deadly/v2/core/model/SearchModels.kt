package com.deadly.v2.core.model

import kotlinx.serialization.Serializable

/**
 * Search specific data models for the V2 search interface
 */

@Serializable
data class SearchResultShow(
    val show: Show,
    val relevanceScore: Float = 1.0f,
    val matchType: SearchMatchType = SearchMatchType.TITLE,
    val hasDownloads: Boolean = false,
    val highlightedFields: List<String> = emptyList()
)

@Serializable
data class RecentSearch(
    val query: String,
    val timestamp: Long
)

@Serializable
data class SuggestedSearch(
    val query: String,
    val resultCount: Int = 0,
    val type: SuggestionType = SuggestionType.GENERAL
)

enum class SearchMatchType(val displayName: String) {
    TITLE("Title"),
    VENUE("Venue"), 
    YEAR("Year"),
    SETLIST("Setlist"),
    LOCATION("Location"),
    GENERAL("General")
}

enum class SuggestionType {
    GENERAL,
    VENUE,
    YEAR,
    SONG,
    LOCATION
}

enum class SearchStatus {
    IDLE,
    SEARCHING,
    SUCCESS,
    ERROR,
    NO_RESULTS
}

@Serializable
data class SearchStats(
    val totalResults: Int,
    val searchDuration: Long,
    val appliedFilters: List<String> = emptyList()
)

/**
 * UI State for SearchScreen
 * 
 * Comprehensive state model discovered through UI-first development
 * and coordinated with SearchService reactive flows.
 */
data class SearchUiState(
    val searchQuery: String = "",
    val searchResults: List<SearchResultShow> = emptyList(),
    val searchStatus: SearchStatus = SearchStatus.IDLE,
    val recentSearches: List<RecentSearch> = emptyList(),
    val suggestedSearches: List<SuggestedSearch> = emptyList(),
    val searchStats: SearchStats = SearchStats(0, 0),
    val isLoading: Boolean = false,
    val error: String? = null
)