package com.grateful.deadly.core.search

import android.util.Log
import com.grateful.deadly.core.api.search.SearchService
import com.grateful.deadly.core.api.search.SearchFilter
import com.grateful.deadly.core.database.AppPreferences
import com.grateful.deadly.core.database.dao.ShowSearchDao
import com.grateful.deadly.core.database.mappers.ShowMappers
import com.grateful.deadly.core.model.*
import com.grateful.deadly.core.model.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real FTS5 implementation of SearchService using Room database.
 * 
 * Core functionality only:
 * - FTS5 search for show IDs
 * - Batch lookup for full entities  
 * - Return results with FTS5 ranking preserved
 */
@Singleton
class SearchServiceImpl @Inject constructor(
    @AppDatabase private val showSearchDao: ShowSearchDao,
    private val showMappers: ShowMappers,
    private val appPreferences: AppPreferences
) : SearchService {
    
    companion object {
        private const val TAG = "SearchServiceImpl"
    }
    
    // Reactive state management
    private val _currentQuery = MutableStateFlow("")
    private val _searchResults = MutableStateFlow<List<SearchResultShow>>(emptyList())
    private val _searchStatus = MutableStateFlow(SearchStatus.IDLE)
    private val _recentSearches = MutableStateFlow<List<RecentSearch>>(emptyList())
    private val _searchStats = MutableStateFlow(SearchStats(0, 0))
    
    // Public reactive flows
    override val currentQuery: Flow<String> = _currentQuery.asStateFlow()
    override val searchResults: Flow<List<SearchResultShow>> = _searchResults.asStateFlow()
    override val searchStatus: Flow<SearchStatus> = _searchStatus.asStateFlow()
    override val recentSearches: Flow<List<RecentSearch>> = _recentSearches.asStateFlow()
    override val suggestedSearches: Flow<List<SuggestedSearch>> = MutableStateFlow<List<SuggestedSearch>>(emptyList()).asStateFlow()
    override val searchStats: Flow<SearchStats> = _searchStats.asStateFlow()
    
    override suspend fun updateSearchQuery(query: String): Result<Unit> {
        Log.d(TAG, "updateSearchQuery(query='$query') called")
        
        _currentQuery.value = query
        
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _searchStatus.value = SearchStatus.IDLE
            _searchStats.value = SearchStats(0, 0)
            return Result.success(Unit)
        }
        
        _searchStatus.value = SearchStatus.SEARCHING
        
        try {
            val startTime = System.currentTimeMillis()

            // Single FTS+JOIN query with column projection — skips 3MB of JSON blob I/O
            val summaries = showSearchDao.searchShowsWithSummary(query)
            val allShows = showMappers.summariesToDomain(summaries)

            // Filter out recordingless shows when preference is not enabled
            val shows = if (!appPreferences.includeShowsWithoutRecordings.value) {
                allShows.filter { it.recordingCount > 0 }
            } else {
                allShows
            }

            Log.d(TAG, "FTS+JOIN returned ${allShows.size} shows, ${shows.size} after filter in ${System.currentTimeMillis() - startTime}ms")

            // Convert to SearchResultShow preserving FTS ranking
            val resultCount = shows.size.coerceAtLeast(1)
            val results = shows.mapIndexed { index: Int, show ->
                val ftsRankScore = 1.0f - (index.toFloat() / resultCount)
                val matchType = determineMatchType(show, query)
                
                SearchResultShow(
                    show = show,
                    relevanceScore = ftsRankScore,
                    matchType = matchType,
                    hasDownloads = false
                )
            }
            
            val searchDuration = System.currentTimeMillis() - startTime
            
            _searchResults.value = results
            _searchStats.value = SearchStats(results.size, searchDuration)
            _searchStatus.value = if (results.isEmpty()) SearchStatus.NO_RESULTS else SearchStatus.SUCCESS
            
            Log.d(TAG, "Search completed - ${results.size} results in ${searchDuration}ms")
            
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            _searchStatus.value = SearchStatus.ERROR
            return Result.failure(e)
        }
        
        return Result.success(Unit)
    }
    
    override suspend fun clearSearch(): Result<Unit> {
        _currentQuery.value = ""
        _searchResults.value = emptyList()
        _searchStatus.value = SearchStatus.IDLE
        _searchStats.value = SearchStats(0, 0)
        return Result.success(Unit)
    }
    
    override suspend fun addRecentSearch(query: String): Result<Unit> {
        // TODO: Implement recent search persistence
        return Result.success(Unit)
    }
    
    override suspend fun clearRecentSearches(): Result<Unit> {
        _recentSearches.value = emptyList()
        return Result.success(Unit)
    }
    
    override suspend fun selectSuggestion(suggestion: SuggestedSearch): Result<Unit> {
        return updateSearchQuery(suggestion.query)
    }
    
    override suspend fun applyFilters(filters: List<SearchFilter>): Result<Unit> {
        // TODO: Implement filtering
        return Result.success(Unit)
    }
    
    override suspend fun clearFilters(): Result<Unit> {
        // TODO: Implement filtering
        return Result.success(Unit)
    }
    
    override suspend fun getSuggestions(partialQuery: String): Result<List<SuggestedSearch>> {
        return Result.success(emptyList())
    }
    
    private fun determineMatchType(show: Show, query: String): SearchMatchType {
        val queryLower = query.lowercase()
        return when {
            show.date.contains(queryLower) -> SearchMatchType.YEAR
            show.venue.name.lowercase().contains(queryLower) -> SearchMatchType.VENUE
            show.venue.city?.lowercase()?.contains(queryLower) == true -> SearchMatchType.LOCATION
            else -> SearchMatchType.GENERAL
        }
    }
}

