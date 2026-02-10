# Search

The Search feature enables users to find Grateful Dead shows using a powerful local FTS5 (Full-Text Search) implementation. Users can search by songs, venues, dates, cities, band members, or any text found in show data.

## Overview

**Purpose**: Provide fast, relevant search results across the entire Grateful Dead show catalog (~2,500 shows) stored locally in the database.

**Why It Exists**: With thousands of shows available, browsing alone isn't practical. Search is the primary discovery mechanism for users looking for specific songs, venues, dates, or tour periods.

**Key Characteristics**:
- **Local-only search** - No Archive.org API calls, all data is in local database
- **FTS5 powered** - SQLite Full-Text Search with BM25 ranking
- **Debounced input** - 800ms delay to avoid excessive queries while typing
- **Reactive state** - Real-time updates via Kotlin Flows
- **Multi-field matching** - Searches across songs, venues, dates, members, locations

## Capabilities

### Core Search
- **Free-text query** - Type anything, get ranked results
- **Multi-field matching** - Automatically searches songs, venues, dates, cities, states, band members
- **Ranked results** - BM25 relevance scoring via FTS5
- **Instant results** - Typical search completes in < 100ms

### Query Processing
- **Debounced input** - 800ms delay before executing search (configurable)
- **Distinct queries** - Only triggers search when query actually changes
- **Empty query handling** - Clears results immediately when query is empty
- **Min query length** - Queries under 3 characters don't get saved to history

### Search Results
- **Relevance score** - Each result has calculated relevance (FTS5 rank + position)
- **Match type** - Indicates what matched (venue, year, location, song, general)
- **Full show data** - Each result includes complete show information
- **Preserved ranking** - FTS5 BM25 ranking maintained through result pipeline

### Search History
- **Recent searches** - Track previously executed queries
- **Quick re-execution** - Tap recent search to run again (no debounce)
- **Clear history** - Remove all recent searches

### Search Suggestions (Planned)
- **Dynamic suggestions** - Based on partial query
- **Quick selection** - Tap suggestion to execute immediately
- **No debounce** - Deliberate selections execute instantly

### Search Filters (Planned)
- Venue, Year, Location
- Has Downloads, Recent, Popular
- Soundboard vs Audience recordings

## Implementation

### Modules

**API Module**: `v2:core:api:search`
- **SearchService.kt** - Service interface defining search contract
- Defines all data models (SearchResultShow, SearchStatus, SearchMatchType, etc.)
- Exposes reactive Flows for UI observation

**Core Module**: `v2:core:search`
- **SearchServiceImpl.kt** - Real FTS5 implementation
- **SearchServiceStub.kt** - Test data stub for UI development
- **SearchModule.kt** - Hilt dependency injection configuration

**Feature Module**: `v2:feature:search`
- **SearchViewModel.kt** - Presentation logic and state coordination
- **SearchScreen.kt** - Main search UI
- **SearchResultsScreen.kt** - Results display
- **SearchNavigation.kt** - Navigation integration

### State Management

#### Service Layer (SearchServiceImpl)

Exposes 6 reactive Flows:

```kotlin
val currentQuery: Flow<String>               // Currently active query
val searchResults: Flow<List<SearchResultShow>>  // Ranked results
val searchStatus: Flow<SearchStatus>         // IDLE, SEARCHING, SUCCESS, NO_RESULTS, ERROR
val recentSearches: Flow<List<RecentSearch>> // Recent queries
val suggestedSearches: Flow<List<SuggestedSearch>>  // Query suggestions
val searchStats: Flow<SearchStats>           // Result count, search duration
```

#### ViewModel Layer (SearchViewModel)

Coordinates between UI and service:

```kotlin
data class SearchUiState(
    val searchQuery: String = "",
    val searchResults: List<SearchResultShow> = emptyList(),
    val searchStatus: SearchStatus = SearchStatus.IDLE,
    val isLoading: Boolean = false,
    val error: String? = null,
    val recentSearches: List<RecentSearch> = emptyList(),
    val suggestedSearches: List<SuggestedSearch> = emptyList(),
    val searchStats: SearchStats = SearchStats(0, 0)
)
```

**Responsibilities**:
- Debounce user input (800ms configurable delay)
- Collect service Flows and transform to UI state
- Handle user actions (query changes, clear, recent selection)
- Manage lifecycle (cancel jobs on clear)

### Data Sources

#### Primary: show_search_v2 FTS5 Table

Search uses the FTS5 virtual table which indexes searchable text:

```sql
CREATE VIRTUAL TABLE show_search_v2 USING fts5(
    show_id UNINDEXED,
    search_text,          -- Concatenated searchable fields
    tokenize='unicode61'
)
```

**What's indexed** (from shows_v2 table):
- Song names (from setlist)
- Venue name
- City, state, country
- Band member names (from lineup)
- Date variations (YYYY-MM-DD, YYYY-MM, YYYY)

**Search flow**:
1. `ShowSearchDao.searchShows(query)` queries FTS5 table
2. Returns list of `show_id` strings in BM25 rank order
3. `ShowRepository.getShowById()` fetches full show entities
4. Mapper converts entities → domain models
5. Results returned to UI with ranking preserved

#### Secondary: Database Cache

Recent searches planned to be persisted to database (not yet implemented).

## Integration Points

### Navigation
- **Entry Points**: Bottom nav bar, home screen quick search
- **Exit Points**: Tap search result → Show Detail screen
- **Deep Links**: Can navigate directly to search with query parameter

### Show Detail
Search results link to Show Detail screen:
- Passes `show_id` via navigation arguments
- Show Detail loads full show data + recordings

### Player
From search results, users can:
- Tap show → view details → select recording → start playback
- Quick actions planned: Play best recording directly from search result

### Home
Home screen can display:
- Recent searches (quick access)
- Search suggestions (if user has search history)

## Code References

### API Contract
- **Interface**: `androidApp/v2/core/api/search/src/main/java/com/deadly/v2/core/api/search/SearchService.kt`
- **Models**: Lines 113-124 (SearchFilter enum)

### Core Implementation
- **Service**: `androidApp/v2/core/search/src/main/java/com/deadly/v2/core/search/SearchServiceImpl.kt`
  - FTS5 search: Lines 66-68
  - Domain conversion: Lines 71-73
  - Result mapping: Lines 78-88
  - Match type detection: Lines 143-151

### UI Layer
- **ViewModel**: `androidApp/v2/feature/search/src/main/java/com/deadly/v2/feature/search/screens/main/models/SearchViewModel.kt`
  - Debounced search: Lines 89-109
  - Query change handling: Lines 79-84
  - Service flow observation: Lines 212-252

### Database
- **FTS5 DAO**: `androidApp/v2/core/database/src/main/java/com/deadly/v2/core/database/dao/ShowSearchDao.kt`
- **Entity**: `androidApp/v2/core/database/src/main/java/com/deadly/v2/core/database/entity/ShowSearchEntity.kt`

## Search Algorithm

### Query Processing

1. **User types** in search field
2. **Debounce** waits 800ms for typing to stop
3. **ViewModel** calls `searchService.updateSearchQuery(query)`
4. **Service** updates `currentQuery` Flow (UI shows query immediately)
5. **Service** sets status to `SEARCHING` (UI shows loading)

### FTS5 Execution

6. **FTS5 query** executes: `SELECT show_id FROM show_search_v2 WHERE search_text MATCH ?`
7. **BM25 ranking** automatically applied by FTS5
8. **Show IDs** returned in relevance order

### Result Enrichment

9. **Batch lookup**: For each show_id, fetch full `ShowEntity` from database
10. **Domain conversion**: Entity → Domain `Show` model via repository
11. **Match type detection**: Determine what matched (venue/year/location/general)
12. **Relevance scoring**: Calculate score from FTS5 position (1.0 for #1, decreasing)

### State Update

13. **Results Flow** updated with `List<SearchResultShow>`
14. **Status Flow** updated to `SUCCESS` or `NO_RESULTS`
15. **Stats Flow** updated with count and duration
16. **UI** automatically updates via Flow collection

### Empty Query

Special case: If query is blank:
- Clear results immediately (no FTS5 query)
- Set status to `IDLE`
- Clear stats

## Performance

### Search Speed
- **Typical**: 50-100ms for queries returning 10-50 results
- **Fast path**: FTS5 index scan is O(log n)
- **Slow path**: Batch entity lookup is O(k) where k = result count

### Optimizations
- **Debouncing**: Reduces queries while user types (800ms delay)
- **Distinct queries**: Skips duplicate searches
- **FTS5 indexing**: Pre-computed index for instant matching
- **Preserved ranking**: No re-sorting after FTS5 (ranking is correct)
- **Lazy loading**: Only fetches show entities for matched IDs

### Known Limitations
- **No pagination**: All results returned at once (typically < 100 results)
- **No result limit**: FTS5 can return thousands of matches (rare)
- **No caching**: Each query executes fresh FTS5 search

## Testing

### Unit Tests

**ViewModel Tests**:
```kotlin
@Test
fun `debounced search executes after delay`() {
    // Given: ViewModel with mock service
    // When: User types query
    viewModel.onSearchQueryChanged("Cornell")
    // Then: Service not called immediately
    verify(searchService, never()).updateSearchQuery(any())
    // When: 800ms elapses
    advanceTimeBy(800)
    // Then: Service called with query
    verify(searchService).updateSearchQuery("Cornell")
}
```

**Service Tests**:
```kotlin
@Test
fun `search returns results ranked by relevance`() {
    // Given: Database with shows
    // When: Search for "Scarlet Begonias"
    val results = searchService.updateSearchQuery("Scarlet Begonias")
    // Then: Results ordered by FTS5 rank
    assert(results[0].relevanceScore > results[1].relevanceScore)
}
```

### Integration Tests

**FTS5 + Repository**:
```kotlin
@Test
fun `search flow returns complete domain models`() {
    // Given: Database with indexed shows
    // When: Execute search
    val results = searchService.updateSearchQuery("Cornell 1977")
    // Then: Results contain full Show domain models
    assert(results[0].show.venue.name.contains("Cornell"))
    assert(results[0].show.date == "1977-05-08")
}
```

### UI Tests

**Search Screen**:
```kotlin
@Test
fun `typing query shows debounced results`() {
    // Given: Search screen
    // When: Type query
    onNode(hasTestTag("searchField")).performTextInput("Dark Star")
    // Then: Results appear after delay
    advanceTimeBy(800)
    onNode(hasText("Dark Star")).assertExists()
}
```

## Future Enhancements

### Planned Features
1. **Search filters** - Venue, year, location, recording type
2. **Search suggestions** - Autocomplete based on partial query
3. **Recent search persistence** - Save history to database
4. **Search analytics** - Track popular queries
5. **Voice search** - Speech-to-text query input

### Performance Improvements
1. **Result pagination** - Load results in chunks
2. **Query caching** - Cache recent query results
3. **Prefetch** - Load show details for top results

### UX Enhancements
1. **Search tips** - Show example queries on empty state
2. **No results suggestions** - "Did you mean..." for typos
3. **Quick actions** - Play/favorite directly from results
4. **Advanced syntax** - Boolean operators, field-specific queries

## Platform Notes

### Android (Current)
- FTS5 via Room database
- Kotlin Coroutines for async
- Hilt for dependency injection
- Jetpack Compose UI

### iOS (To Be Implemented)
- FTS5 via native SQLite
- Swift Concurrency (async/await)
- Property injection or factory for DI
- SwiftUI

Search functionality should be identical on both platforms. FTS5 is available on iOS via SQLite3 library.
