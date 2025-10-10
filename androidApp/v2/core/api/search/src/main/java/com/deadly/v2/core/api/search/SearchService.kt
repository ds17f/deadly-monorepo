package com.deadly.v2.core.api.search

import com.deadly.v2.core.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Clean API interface for Search operations.
 * Defines the contract that both stub and real implementations must follow.
 * 
 * This interface follows the V2 architecture pattern with reactive flows
 * and Result types for proper error handling. Designed through UI-first
 * development where service requirements are discovered by building UI.
 * 
 * Following established patterns from LibraryV2Service and DownloadV2Service.
 */
interface SearchService {
    
    /**
     * Current search query being processed
     */
    val currentQuery: Flow<String>
    
    /**
     * Search results for the current query as a reactive Flow
     */
    val searchResults: Flow<List<SearchResultShow>>
    
    /**
     * Current search status (idle, searching, success, error, no results)
     */
    val searchStatus: Flow<SearchStatus>
    
    /**
     * Recent search history for quick access
     */
    val recentSearches: Flow<List<RecentSearch>>
    
    /**
     * Dynamic search suggestions based on current query
     */
    val suggestedSearches: Flow<List<SuggestedSearch>>
    
    /**
     * Search statistics for the current results
     */
    val searchStats: Flow<SearchStats>
    
    /**
     * Update the search query and trigger search
     * @param query The search query string
     * @return Result indicating success or failure with error details
     */
    suspend fun updateSearchQuery(query: String): Result<Unit>
    
    /**
     * Clear the current search query and results
     * @return Result indicating success or failure with error details
     */
    suspend fun clearSearch(): Result<Unit>
    
    /**
     * Add a search query to recent history
     * @param query The query to add to history
     * @return Result indicating success or failure with error details
     */
    suspend fun addRecentSearch(query: String): Result<Unit>
    
    /**
     * Clear all recent search history
     * @return Result indicating success or failure with error details
     */
    suspend fun clearRecentSearches(): Result<Unit>
    
    /**
     * Select a suggested search query
     * @param suggestion The suggested search to execute
     * @return Result indicating success or failure with error details
     */
    suspend fun selectSuggestion(suggestion: SuggestedSearch): Result<Unit>
    
    /**
     * Apply search filters to refine results
     * @param filters List of search filters to apply
     * @return Result indicating success or failure with error details
     */
    suspend fun applyFilters(filters: List<SearchFilter>): Result<Unit>
    
    /**
     * Clear all applied search filters
     * @return Result indicating success or failure with error details
     */
    suspend fun clearFilters(): Result<Unit>
    
    /**
     * Get search suggestions for a partial query
     * @param partialQuery The partial query to get suggestions for
     * @return Result with list of suggested searches
     */
    suspend fun getSuggestions(partialQuery: String): Result<List<SuggestedSearch>>
    
    /**
     * Populate search service with test data for UI development.
     * Only implemented in stub - no-op in real implementations.
     * @return Result indicating success or failure
     */
    suspend fun populateTestData(): Result<Unit> {
        // Default no-op implementation for real services
        return Result.success(Unit)
    }
}

/**
 * Search filter types for refining results
 */
enum class SearchFilter(val displayName: String) {
    VENUE("Venue"),
    YEAR("Year"),
    LOCATION("Location"),
    HAS_DOWNLOADS("Has Downloads"),
    RECENT("Recent"),
    POPULAR("Popular"),
    SOUNDBOARD("Soundboard"),
    AUDIENCE("Audience")
}