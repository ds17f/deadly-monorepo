package com.deadly.v2.core.search

import android.util.Log
import com.deadly.v2.core.api.search.SearchService
import com.deadly.v2.core.api.search.SearchFilter
import com.deadly.v2.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprehensive stub implementation of SearchService with realistic Dead show data.
 * 
 * This stub provides:
 * - Rich mock data spanning decades of Grateful Dead concerts
 * - Realistic search filtering and relevance scoring
 * - Search suggestions based on popular venues, years, and songs
 * - Recent search history management
 * - Proper search status management with loading states
 * 
 * Enables immediate UI development with comprehensive test data while validating
 * the V2 architecture patterns and service integration.
 */
@Singleton
class SearchServiceStub @Inject constructor() : SearchService {
    
    companion object {
        private const val TAG = "SearchServiceStub"
    }
    
    // Reactive state management
    private val _currentQuery = MutableStateFlow("")
    private val _searchResults = MutableStateFlow<List<SearchResultShow>>(emptyList())
    private val _searchStatus = MutableStateFlow(SearchStatus.IDLE)
    private val _recentSearches = MutableStateFlow<List<RecentSearch>>(emptyList())
    private val _suggestedSearches = MutableStateFlow<List<SuggestedSearch>>(emptyList())
    private val _searchStats = MutableStateFlow(SearchStats(0, 0))
    private val _appliedFilters = MutableStateFlow<Set<SearchFilter>>(emptySet())
    
    // Public reactive flows
    override val currentQuery: Flow<String> = _currentQuery.asStateFlow()
    override val searchResults: Flow<List<SearchResultShow>> = _searchResults.asStateFlow()
    override val searchStatus: Flow<SearchStatus> = _searchStatus.asStateFlow()
    override val recentSearches: Flow<List<RecentSearch>> = _recentSearches.asStateFlow()
    override val suggestedSearches: Flow<List<SuggestedSearch>> = _suggestedSearches.asStateFlow()
    override val searchStats: Flow<SearchStats> = _searchStats.asStateFlow()
    
    // Comprehensive mock show data spanning decades
    private val mockShows = listOf(
        // Cornell 5/8/77 - The legendary show
        Show(
            id = "gd1977-05-08",
            date = "1977-05-08",
            year = 1977,
            band = "Grateful Dead",
            venue = Venue("Barton Hall", "Ithaca", "NY", "USA"),
            location = Location("Ithaca, NY", "Ithaca", "NY"),
            setlist = null,
            lineup = null,
            recordingIds = listOf("gd1977-05-08.sbd.hicks.4982.sbeok.shnf"),
            bestRecordingId = "gd1977-05-08.sbd.hicks.4982.sbeok.shnf",
            recordingCount = 1,
            averageRating = 4.8f,
            totalReviews = 245,
            isInLibrary = false,
            libraryAddedAt = null
        ),
        
        // Europe '72 Classic
        Show(
            id = "gd1972-05-03",
            date = "1972-05-03",
            year = 1972,
            band = "Grateful Dead",
            venue = Venue("Olympia Theatre", "Paris", null, "France"),
            location = Location("Paris, France", "Paris", null),
            setlist = null,
            lineup = null,
            recordingIds = listOf("gd72-05-03.sbd.unknown.30057.sbeok.shnf"),
            bestRecordingId = "gd72-05-03.sbd.unknown.30057.sbeok.shnf",
            recordingCount = 1,
            averageRating = 4.6f,
            totalReviews = 189,
            isInLibrary = false,
            libraryAddedAt = null
        ),
        
        // Woodstock 1969
        Show(
            id = "gd1969-08-16",
            date = "1969-08-16",
            year = 1969,
            band = "Grateful Dead",
            venue = Venue("Woodstock Music & Art Fair", "Bethel", "NY", "USA"),
            location = Location("Bethel, NY", "Bethel", "NY"),
            setlist = null,
            lineup = null,
            recordingIds = listOf("gd69-08-16.aud.vernon.16793.sbeok.shnf"),
            bestRecordingId = "gd69-08-16.aud.vernon.16793.sbeok.shnf",
            recordingCount = 1,
            averageRating = 4.2f,
            totalReviews = 156,
            isInLibrary = false,
            libraryAddedAt = null
        ),
        
        // Dick's Picks era
        Show(
            id = "gd1973-06-10",
            date = "1973-06-10",
            year = 1973,
            band = "Grateful Dead",
            venue = Venue("RFK Stadium", "Washington", "DC", "USA"),
            location = Location("Washington, DC", "Washington", "DC"),
            setlist = null,
            lineup = null,
            recordingIds = listOf("dp12"),
            bestRecordingId = "dp12",
            recordingCount = 1,
            averageRating = 4.7f,
            totalReviews = 203,
            isInLibrary = false,
            libraryAddedAt = null
        ),
        
        // 1990s era
        Show(
            id = "gd1995-07-09",
            date = "1995-07-09",
            year = 1995,
            band = "Grateful Dead",
            venue = Venue("Soldier Field", "Chicago", "IL", "USA"),
            location = Location("Chicago, IL", "Chicago", "IL"),
            setlist = null,
            lineup = null,
            recordingIds = listOf("gd95-07-09.sbd.miller.97483.flac1644"),
            bestRecordingId = "gd95-07-09.sbd.miller.97483.flac1644",
            recordingCount = 1,
            averageRating = 4.1f,
            totalReviews = 298,
            isInLibrary = false,
            libraryAddedAt = null
        ),
        
        // Fillmore East classics
        Show(
            id = "gd1970-02-13",
            date = "1970-02-13",
            year = 1970,
            band = "Grateful Dead",
            venue = Venue("Fillmore East", "New York", "NY", "USA"),
            location = Location("New York, NY", "New York", "NY"),
            setlist = null,
            lineup = null,
            recordingIds = listOf("gd70-02-13.sbd.16332.sbeok.shnf"),
            bestRecordingId = "gd70-02-13.sbd.16332.sbeok.shnf",
            recordingCount = 1,
            averageRating = 4.5f,
            totalReviews = 167,
            isInLibrary = false,
            libraryAddedAt = null
        ),
        
        // Fillmore West
        Show(
            id = "gd1969-02-27",
            date = "1969-02-27",
            year = 1969,
            band = "Grateful Dead",
            venue = Venue("Fillmore West", "San Francisco", "CA", "USA"),
            location = Location("San Francisco, CA", "San Francisco", "CA"),
            setlist = null,
            lineup = null,
            recordingIds = listOf("gd69-02-27.sbd.vernon.87915.flac1644"),
            bestRecordingId = "gd69-02-27.sbd.vernon.87915.flac1644",
            recordingCount = 1,
            averageRating = 4.3f,
            totalReviews = 134,
            isInLibrary = false,
            libraryAddedAt = null
        ),
        
        // More 1977 shows
        Show(
            id = "gd1977-05-22",
            date = "1977-05-22",
            year = 1977,
            band = "Grateful Dead",
            venue = Venue("The Sportatorium", "Pembroke Pines", "FL", "USA"),
            location = Location("Pembroke Pines, FL", "Pembroke Pines", "FL"),
            setlist = null,
            lineup = null,
            recordingIds = listOf("gd77-05-22.sbd.hicks.32928.sbeok.shnf"),
            bestRecordingId = "gd77-05-22.sbd.hicks.32928.sbeok.shnf",
            recordingCount = 1,
            averageRating = 4.4f,
            totalReviews = 178,
            isInLibrary = false,
            libraryAddedAt = null
        )
    )
    
    override suspend fun updateSearchQuery(query: String): Result<Unit> {
        Log.d(TAG, "STUB: updateSearchQuery(query='$query') called")
        
        _currentQuery.value = query
        
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _searchStatus.value = SearchStatus.IDLE
            _suggestedSearches.value = emptyList()
            _searchStats.value = SearchStats(0, 0)
            return Result.success(Unit)
        }
        
        // Simulate search loading
        _searchStatus.value = SearchStatus.SEARCHING
        delay(300) // Realistic search delay
        
        try {
            // Perform smart search with relevance scoring
            val startTime = System.currentTimeMillis()
            val results = performSearch(query)
            val searchDuration = System.currentTimeMillis() - startTime
            
            _searchResults.value = results
            _searchStats.value = SearchStats(
                totalResults = results.size,
                searchDuration = searchDuration,
                appliedFilters = _appliedFilters.value.map { it.displayName }
            )
            
            _searchStatus.value = if (results.isEmpty()) SearchStatus.NO_RESULTS else SearchStatus.SUCCESS
            
            // Generate suggestions based on query
            _suggestedSearches.value = generateSuggestions(query)
            
            Log.d(TAG, "STUB: Search completed - found ${results.size} results in ${searchDuration}ms")
            
        } catch (e: Exception) {
            Log.e(TAG, "STUB: Search failed", e)
            _searchStatus.value = SearchStatus.ERROR
            return Result.failure(e)
        }
        
        return Result.success(Unit)
    }
    
    override suspend fun clearSearch(): Result<Unit> {
        Log.d(TAG, "STUB: clearSearch() called")
        
        _currentQuery.value = ""
        _searchResults.value = emptyList()
        _searchStatus.value = SearchStatus.IDLE
        _suggestedSearches.value = emptyList()
        _searchStats.value = SearchStats(0, 0)
        
        return Result.success(Unit)
    }
    
    override suspend fun addRecentSearch(query: String): Result<Unit> {
        Log.d(TAG, "STUB: addRecentSearch(query='$query') called")
        
        if (query.isBlank()) return Result.success(Unit)
        
        val currentRecents = _recentSearches.value.toMutableList()
        
        // Remove existing entry if present
        currentRecents.removeAll { it.query == query }
        
        // Add to front
        currentRecents.add(0, RecentSearch(query, System.currentTimeMillis()))
        
        // Keep only last 10
        if (currentRecents.size > 10) {
            currentRecents.removeAt(currentRecents.size - 1)
        }
        
        _recentSearches.value = currentRecents
        
        return Result.success(Unit)
    }
    
    override suspend fun clearRecentSearches(): Result<Unit> {
        Log.d(TAG, "STUB: clearRecentSearches() called")
        _recentSearches.value = emptyList()
        return Result.success(Unit)
    }
    
    override suspend fun selectSuggestion(suggestion: SuggestedSearch): Result<Unit> {
        Log.d(TAG, "STUB: selectSuggestion(suggestion='${suggestion.query}') called")
        return updateSearchQuery(suggestion.query)
    }
    
    override suspend fun applyFilters(filters: List<SearchFilter>): Result<Unit> {
        Log.d(TAG, "STUB: applyFilters(filters=$filters) called")
        
        _appliedFilters.value = filters.toSet()
        
        // Re-run search with filters applied
        if (_currentQuery.value.isNotBlank()) {
            return updateSearchQuery(_currentQuery.value)
        }
        
        return Result.success(Unit)
    }
    
    override suspend fun clearFilters(): Result<Unit> {
        Log.d(TAG, "STUB: clearFilters() called")
        
        _appliedFilters.value = emptySet()
        
        // Re-run search without filters
        if (_currentQuery.value.isNotBlank()) {
            return updateSearchQuery(_currentQuery.value)
        }
        
        return Result.success(Unit)
    }
    
    override suspend fun getSuggestions(partialQuery: String): Result<List<SuggestedSearch>> {
        Log.d(TAG, "STUB: getSuggestions(partialQuery='$partialQuery') called")
        
        val suggestions = generateSuggestions(partialQuery)
        return Result.success(suggestions)
    }
    
    override suspend fun populateTestData(): Result<Unit> {
        Log.d(TAG, "STUB: populateTestData() called")
        
        // Populate with sample recent searches
        _recentSearches.value = listOf(
            RecentSearch("Cornell 5/8/77", System.currentTimeMillis() - 3600000),
            RecentSearch("1977", System.currentTimeMillis() - 7200000),
            RecentSearch("Fillmore", System.currentTimeMillis() - 10800000),
            RecentSearch("Dick's Picks", System.currentTimeMillis() - 14400000)
        )
        
        Log.d(TAG, "STUB: Populated ${_recentSearches.value.size} recent searches")
        
        return Result.success(Unit)
    }
    
    /**
     * Perform intelligent search with relevance scoring
     */
    private fun performSearch(query: String): List<SearchResultShow> {
        val queryLower = query.lowercase()
        val results = mutableListOf<SearchResultShow>()
        
        for (show in mockShows) {
            val matchType = determineMatchType(show, queryLower)
            val relevanceScore = calculateRelevanceScore(show, queryLower, matchType)
            
            if (relevanceScore > 0.1f) {
                val hasDownloads = listOf("gd1977-05-08", "gd72-05-03", "dp12").any { 
                    show.recordingIds.any { recordingId -> recordingId.contains(it) }
                }
                
                results.add(SearchResultShow(
                    show = show,
                    relevanceScore = relevanceScore,
                    matchType = matchType,
                    hasDownloads = hasDownloads
                ))
            }
        }
        
        // Apply filters
        val filteredResults = applySearchFilters(results)
        
        // Sort by relevance score descending
        return filteredResults.sortedByDescending { it.relevanceScore }
    }
    
    private fun determineMatchType(show: Show, query: String): SearchMatchType {
        return when {
            show.date.contains(query) || show.year.toString().contains(query) -> SearchMatchType.YEAR
            show.venue.name.lowercase().contains(query) -> SearchMatchType.VENUE
            show.location.displayText.lowercase().contains(query) -> SearchMatchType.LOCATION
            else -> SearchMatchType.GENERAL
        }
    }
    
    private fun calculateRelevanceScore(show: Show, query: String, matchType: SearchMatchType): Float {
        var score = 0f
        
        // Base scoring by match type
        when (matchType) {
            SearchMatchType.TITLE -> score += 1.0f
            SearchMatchType.VENUE -> score += 0.9f
            SearchMatchType.YEAR -> score += 0.8f
            SearchMatchType.LOCATION -> score += 0.7f
            SearchMatchType.SETLIST -> score += 0.6f
            SearchMatchType.GENERAL -> score += 0.5f
        }
        
        // Bonus for exact matches
        if (show.venue.name.lowercase() == query) score += 0.5f
        if (show.date.contains(query)) score += 0.3f
        
        // Popular show bonuses
        when (show.date) {
            "1977-05-08" -> score += 0.3f // Cornell
            "1972-05-03" -> score += 0.2f // Europe '72
            "1969-08-16" -> score += 0.2f // Woodstock
            "1995-07-09" -> score += 0.2f // Jerry's last show
        }
        
        return score.coerceIn(0f, 1f)
    }
    
    private fun applySearchFilters(results: List<SearchResultShow>): List<SearchResultShow> {
        var filtered = results
        
        for (filter in _appliedFilters.value) {
            filtered = when (filter) {
                SearchFilter.HAS_DOWNLOADS -> filtered.filter { it.hasDownloads }
                SearchFilter.SOUNDBOARD -> filtered.filter { show ->
                    // Mock soundboard detection based on recording IDs
                    show.show.recordingIds.any { it.contains("sbd") }
                }
                SearchFilter.AUDIENCE -> filtered.filter { show ->
                    // Mock audience detection based on recording IDs
                    show.show.recordingIds.any { it.contains("aud") }
                }
                SearchFilter.POPULAR -> filtered.filter { it.relevanceScore > 0.8f }
                else -> filtered
            }
        }
        
        return filtered
    }
    
    private fun generateSuggestions(query: String): List<SuggestedSearch> {
        if (query.isBlank()) return emptyList()
        
        val suggestions = mutableListOf<SuggestedSearch>()
        val queryLower = query.lowercase()
        
        // Year suggestions
        if (queryLower.matches(Regex("19\\d{0,2}"))) {
            suggestions.addAll(listOf(
                SuggestedSearch("1977", 25, SuggestionType.YEAR),
                SuggestedSearch("1972", 18, SuggestionType.YEAR),
                SuggestedSearch("1969", 12, SuggestionType.YEAR),
                SuggestedSearch("1995", 8, SuggestionType.YEAR)
            ).filter { it.query.startsWith(queryLower) })
        }
        
        // Venue suggestions
        val venues = listOf("Fillmore", "Cornell", "Woodstock", "Madison Square Garden", "Soldier Field")
        suggestions.addAll(venues
            .filter { it.lowercase().contains(queryLower) }
            .map { SuggestedSearch(it, 15, SuggestionType.VENUE) }
        )
        
        // Location suggestions
        val locations = listOf("New York", "California", "Chicago", "Boston", "Philadelphia")
        suggestions.addAll(locations
            .filter { it.lowercase().contains(queryLower) }
            .map { SuggestedSearch(it, 10, SuggestionType.LOCATION) }
        )
        
        // Popular search terms
        val popularTerms = listOf("Dick's Picks", "Europe '72", "Skull & Roses", "Live/Dead")
        suggestions.addAll(popularTerms
            .filter { it.lowercase().contains(queryLower) }
            .map { SuggestedSearch(it, 20, SuggestionType.GENERAL) }
        )
        
        return suggestions.take(6) // Limit to 6 suggestions
    }
}