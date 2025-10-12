# Query Patterns

This document catalogs the actual queries the Android app uses and explains how the database schema is designed to support them efficiently. Every query shown here is extracted from the live DAO files.

---

## Overview

The database schema is optimized for these primary access patterns:

1. **Chronological Browse** - Show all shows by date (newest first)
2. **Date Filtering** - Shows by year, month, or date range
3. **Location Browse** - Shows by venue, city, or state
4. **Full-Text Search** - Search any show attribute via FTS4
5. **Navigation** - Next/previous show for playback continuity
6. **Recording Queries** - Get best recording, filter by quality
7. **Library Management** - User's saved shows with pins
8. **Recent History** - Recently played shows

---

## 1. Browse Patterns

### 1.1 Chronological Browse (All Shows)

**Use Case**: Home screen default view, browse all shows

**Query**:
```sql
SELECT * FROM shows ORDER BY date DESC
```

**Code Location**: `ShowDao.kt:20-21`

**Indexes Used**:
- `idx_shows_date` - Fast sorting by date

**Performance**: O(log N) with index

**Variations**:
```sql
-- With limit for pagination
SELECT * FROM shows ORDER BY date DESC LIMIT 20
```

**Code Location**: `ShowDao.kt:71-72`

**UI Context**: Main show list, "Latest Shows" section

---

### 1.2 Shows by Year

**Use Case**: "1977 Shows" - user browsing by year

**Query**:
```sql
SELECT * FROM shows WHERE year = :year ORDER BY date
```

**Code Location**: `ShowDao.kt:36-37`

**Indexes Used**:
- `idx_shows_year` - Fast filter by year
- `idx_shows_date` - Sort filtered results chronologically

**Performance**: O(log N + k) where k = shows in year

**Example**: `getShowsByYear(1977)` returns all ~100 shows from 1977

**UI Context**: Year filter button, decade browser

---

### 1.3 Shows by Year-Month

**Use Case**: "May 1977" - more granular browsing

**Query**:
```sql
SELECT * FROM shows WHERE yearMonth = :yearMonth ORDER BY date
```

**Code Location**: `ShowDao.kt:39-40`

**Indexes Used**:
- `idx_shows_yearMonth` - Composite index for month-level filtering

**Performance**: O(log N + k) where k = shows in month

**Example**: `getShowsByYearMonth("1977-05")` returns ~8 shows from May 1977

**UI Context**: Month drill-down, calendar view

---

### 1.4 Shows by Exact Date

**Use Case**: Multiple shows on same day (common for festivals/runs)

**Query**:
```sql
SELECT * FROM shows WHERE date = :date ORDER BY showSequence
```

**Code Location**: `ShowDao.kt:42-43`

**Indexes Used**:
- `idx_shows_date` - Exact date lookup

**Performance**: O(log N + k) where k = shows on date (typically 1-3)

**Example**: `getShowsByDate("1977-05-08")` returns Cornell '77

**Note**: `showSequence` handles multiple shows per day (first show = 1, second = 2)

**UI Context**: Date picker, "on this day" feature

---

### 1.5 Date Range Browse

**Use Case**: "Spring 1977 Tour" - tour date ranges

**Query**:
```sql
SELECT * FROM shows
WHERE date >= :startDate AND date <= :endDate
ORDER BY date
```

**Code Location**: `ShowDao.kt:45-46`

**Indexes Used**:
- `idx_shows_date` - Range scan

**Performance**: O(log N + k) where k = shows in range

**Example**: `getShowsInDateRange("1977-04-22", "1977-06-09")` returns entire Spring '77 tour

**UI Context**: Tour collections, date range picker

---

### 1.6 "On This Day" Browse

**Use Case**: "All shows played on May 8th" (across all years)

**Query**:
```sql
SELECT * FROM shows
WHERE month = :month
  AND SUBSTR(date, 9, 2) = PRINTF('%02d', :day)
  AND recordingCount > 0
ORDER BY year
```

**Code Location**: `ShowDao.kt:74-75`

**Indexes Used**:
- `idx_shows_month` - Filter by month first
- Manual SUBSTR for day matching (no index, but small result set)

**Performance**: O(N) for SUBSTR, but acceptable (only 30-40 days per month to scan)

**Example**: `getShowsForDate(5, 8)` returns all May 8th shows (Cornell '77, others)

**UI Context**: "Today in Dead history" feature

**Why No Day Index**: Date part extraction doesn't benefit from index; month filter reduces scan significantly.

---

## 2. Location Browse Patterns

### 2.1 Shows by Venue Name

**Use Case**: "All Fillmore East shows"

**Query**:
```sql
SELECT * FROM shows
WHERE venueName LIKE '%' || :venueName || '%'
ORDER BY date
```

**Code Location**: `ShowDao.kt:50-51`

**Indexes Used**:
- None (LIKE with wildcard prefix can't use index)

**Performance**: O(N) - full table scan

**Why No Index**: `%pattern%` requires scanning all rows; prefix-only `LIKE 'pattern%'` could use index but defeats flexible search

**Example**: `getShowsByVenue("Fillmore")` matches "Fillmore East", "Fillmore West"

**UI Context**: Venue browser, venue detail page

**Alternative**: Use FTS4 search for better performance on venue queries

---

### 2.2 Shows by City

**Use Case**: "All San Francisco shows"

**Query**:
```sql
SELECT * FROM shows WHERE city = :city ORDER BY date DESC
```

**Code Location**: `ShowDao.kt:53-54`

**Indexes Used**:
- `idx_shows_city` - Exact city match

**Performance**: O(log N + k) where k = shows in city

**Example**: `getShowsByCity("San Francisco")` returns ~200 shows

**UI Context**: Location browser, map view

---

### 2.3 Shows by State

**Use Case**: "All California shows"

**Query**:
```sql
SELECT * FROM shows WHERE state = :state ORDER BY date DESC
```

**Code Location**: `ShowDao.kt:56-57`

**Indexes Used**:
- `idx_shows_state` - Exact state match

**Performance**: O(log N + k) where k = shows in state

**Example**: `getShowsByState("CA")` returns ~500 shows

**UI Context**: State filter, regional browser

---

## 3. Full-Text Search Pattern

### 3.1 FTS4 Text Search

**Use Case**: User types "Cornell 1977" or "5-8-77" in search box

**Query**:
```sql
SELECT showId FROM show_search
WHERE show_search MATCH :query
```

**Code Location**: `ShowSearchDao.kt:36-37`

**Indexes Used**:
- FTS4 inverted index (automatic)

**Performance**: O(log T) where T = token count, not row count

**BM25 Ranking**: Results ordered by relevance automatically

**Example Queries**:
- `searchShows("Cornell")` → `["1977-05-08-barton-hall-cornell-u-ithaca-ny-usa", ...]`
- `searchShows("5-8-77")` → Same result (date variation indexed)
- `searchShows("Ithaca 1977")` → Cornell '77 + other Ithaca shows

**Then Fetch Full Data**:
```kotlin
val showIds = showSearchDao.searchShows(query)
val shows = showDao.getShowsByIds(showIds)
```

**Code Location**: `ShowDao.kt:29-30`

**UI Context**: Search bar, global show search

**Why Two Queries**: FTS4 returns IDs with ranking; must fetch full show data separately

---

### 3.2 Reactive Search (Live Updates)

**Query**:
```sql
SELECT showId FROM show_search
WHERE show_search MATCH :query
```

**Code Location**: `ShowSearchDao.kt:43-44` (Flow variant)

**Returns**: `Flow<List<String>>` that updates automatically

**Usage**: ViewModel observes Flow, UI updates reactively as user types

---

### 3.3 Song Search (Fallback)

**Use Case**: Find shows by song name (when FTS4 not sufficient)

**Query**:
```sql
SELECT * FROM shows
WHERE songList LIKE '%' || :songName || '%'
ORDER BY date DESC
```

**Code Location**: `ShowDao.kt:60-64`

**Indexes Used**:
- None (wildcard LIKE requires scan)

**Performance**: O(N) - full table scan

**Why Exists**: Fallback for detailed song queries; FTS4 handles most cases

**Example**: `getShowsBySong("Scarlet Begonias")` returns all shows with that song

**Note**: `songList` is comma-separated: `"Scarlet Begonias,Fire on the Mountain,..."`

---

## 4. Navigation Patterns

### 4.1 Next Show (Chronological)

**Use Case**: Playback ends, user taps "Next Show" button

**Query**:
```sql
SELECT * FROM shows
WHERE date > :currentDate
ORDER BY date ASC
LIMIT 1
```

**Code Location**: `ShowDao.kt:78-79`

**Indexes Used**:
- `idx_shows_date` - Range scan + sort

**Performance**: O(log N) - single row lookup

**Example**: After playing `1977-05-08`, `getNextShowByDate("1977-05-08")` returns `1977-05-09`

**UI Context**: "Next Show" button, autoplay

---

### 4.2 Previous Show (Chronological)

**Use Case**: User taps "Previous Show" button

**Query**:
```sql
SELECT * FROM shows
WHERE date < :currentDate
ORDER BY date DESC
LIMIT 1
```

**Code Location**: `ShowDao.kt:81-82`

**Indexes Used**:
- `idx_shows_date` - Range scan + reverse sort

**Performance**: O(log N) - single row lookup

**Example**: From `1977-05-08`, `getPreviousShowByDate("1977-05-08")` returns `1977-05-07`

**UI Context**: "Previous Show" button

---

## 5. Recording Queries

### 5.1 All Recordings for Show

**Use Case**: Show detail page - list all available recordings

**Query**:
```sql
SELECT * FROM recordings
WHERE show_id = :showId
ORDER BY rating DESC
```

**Code Location**: `RecordingDao.kt:22-23`

**Indexes Used**:
- `idx_recordings_show_id_rating` - Composite index (perfect for this query)

**Performance**: O(log N + k) where k = recordings for show (typically 6-8)

**Example**: `getRecordingsForShow("1977-05-08-...")` returns ~8 recordings sorted by quality

**UI Context**: Recording selector dropdown

---

### 5.2 Best Recording for Show

**Use Case**: Auto-select best quality recording on show load

**Query**:
```sql
SELECT * FROM recordings
WHERE show_id = :showId
ORDER BY rating DESC
LIMIT 1
```

**Code Location**: `RecordingDao.kt:25-26`

**Indexes Used**:
- `idx_recordings_show_id_rating` - Composite index (optimized)

**Performance**: O(log N) - single best record

**Example**: `getBestRecordingForShow("1977-05-08-...")` returns Miller SBD (highest rated)

**UI Context**: Default recording selection

---

### 5.3 Filter by Source Type

**Use Case**: "Show me only soundboards"

**Query**:
```sql
SELECT * FROM recordings
WHERE source_type = :sourceType
ORDER BY rating DESC
```

**Code Location**: `RecordingDao.kt:28-29`

**Indexes Used**:
- `idx_recordings_source_type` - Filter by type
- Sort by rating (may require temp table)

**Performance**: O(log N + k) where k = recordings of type

**Example**: `getRecordingsBySourceType("SBD")` returns all soundboards

**UI Context**: Source type filter button

---

### 5.4 Top Rated Recordings (Global)

**Use Case**: "Best recordings ever" discovery feature

**Query**:
```sql
SELECT * FROM recordings
WHERE rating > :minRating AND review_count >= :minReviews
ORDER BY rating DESC, review_count DESC
LIMIT :limit
```

**Code Location**: `RecordingDao.kt:47-57`

**Indexes Used**:
- `idx_recordings_rating` - Primary sort
- Secondary sort by review_count (in-memory)

**Performance**: O(N) for filters + O(k log k) for sort where k = qualifying recordings

**Example**: `getTopRatedRecordings(minRating=4.0, minReviews=10, limit=50)` returns top 50 legendary recordings

**UI Context**: "Top Recordings" discovery page

---

### 5.5 Recording Statistics by Source Type

**Use Case**: Analytics, debugging

**Query**:
```sql
SELECT
    source_type,
    COUNT(*) as count,
    AVG(rating) as avg_rating,
    AVG(review_count) as avg_reviews
FROM recordings
WHERE source_type IS NOT NULL
GROUP BY source_type
ORDER BY count DESC
```

**Code Location**: `RecordingDao.kt:62-73`

**Indexes Used**:
- `idx_recordings_source_type` - GROUP BY optimization

**Performance**: O(N) - full table scan for aggregation

**Example Output**:
```
AUD: 12000 recordings, avg_rating=3.5, avg_reviews=15
SBD: 5000 recordings, avg_rating=4.2, avg_reviews=25
MATRIX: 2000 recordings, avg_rating=4.0, avg_reviews=20
```

**UI Context**: Admin/debug screen

---

## 6. Library Management Patterns

### 6.1 Get All Library Shows

**Use Case**: User's library page

**Query**:
```sql
SELECT * FROM library_shows
ORDER BY isPinned DESC, addedToLibraryAt DESC
```

**Code Location**: `LibraryDao.kt:38-39`

**Indexes Used**:
- `idx_library_shows_is_pinned` - Sort pinned first
- `idx_library_shows_added_at` - Sort by added date

**Performance**: O(log N + k) where k = library size (typically 10-100)

**Sort Order**: Pinned shows first, then chronological by add date

**Example Output**:
```
📌 Cornell '77 (pinned, added 2 days ago)
📌 Veneta '72 (pinned, added 1 week ago)
   Red Rocks '78 (added yesterday)
   Nassau '79 (added 3 days ago)
```

**UI Context**: Library tab

---

### 6.2 Get Pinned Shows Only

**Query**:
```sql
SELECT * FROM library_shows
WHERE isPinned = 1
ORDER BY addedToLibraryAt DESC
```

**Code Location**: `LibraryDao.kt:41-42`

**Indexes Used**:
- `idx_library_shows_is_pinned` - Filter pinned

**Performance**: O(log N + k) where k = pinned count (typically 0-10)

**UI Context**: Quick access widget, favorites section

---

### 6.3 Check if Show in Library

**Use Case**: Display heart icon (filled/unfilled)

**Query**:
```sql
SELECT EXISTS(SELECT 1 FROM library_shows WHERE showId = :showId)
```

**Code Location**: `LibraryDao.kt:55-56`

**Indexes Used**:
- PRIMARY KEY on `showId` - O(1) lookup

**Performance**: O(1)

**Returns**: Boolean (true if in library)

**UI Context**: Heart icon on every show card

---

### 6.4 Check if Show is Pinned

**Query**:
```sql
SELECT EXISTS(SELECT 1 FROM library_shows WHERE showId = :showId AND isPinned = 1)
```

**Code Location**: `LibraryDao.kt:61-62`

**Indexes Used**:
- PRIMARY KEY + isPinned filter

**Performance**: O(1)

**UI Context**: Pin icon display

---

### 6.5 Toggle Pin Status

**Use Case**: User taps pin icon

**Query**:
```sql
UPDATE library_shows SET isPinned = :isPinned WHERE showId = :showId
```

**Code Location**: `LibraryDao.kt:68-69`

**Performance**: O(1) via PRIMARY KEY

**Note**: Must sync with `shows.is_in_library` flag in transaction

---

### 6.6 Update Library Notes

**Use Case**: User adds personal note to show

**Query**:
```sql
UPDATE library_shows SET libraryNotes = :notes WHERE showId = :showId
```

**Code Location**: `LibraryDao.kt:71-72`

**Performance**: O(1) via PRIMARY KEY

**Example**: `updateLibraryNotes(showId, "First show I attended! Amazing Scarlet > Fire.")`

---

### 6.7 Library Statistics

**Query**:
```sql
SELECT COUNT(*) FROM library_shows
```

**Code Location**: `LibraryDao.kt:75-76`

**Performance**: Fast (simple count)

**UI Context**: Display "42 shows in library"

---

## 7. Recent History Patterns

### 7.1 Get Recently Played Shows

**Use Case**: "Recently played" section on home screen

**Query**:
```sql
SELECT * FROM recent_shows
ORDER BY last_played_timestamp DESC
LIMIT 8
```

**Code Location**: `RecentShowDao.kt:26-30`

**Indexes Used**:
- `idx_recent_shows_last_played DESC` - Perfect index for this query

**Performance**: O(log N + k) where k = limit (typically 8-10)

**Example Output** (show IDs):
```
1977-05-08-... (played 2 hours ago)
1972-08-27-... (played yesterday)
1979-11-05-... (played 3 days ago)
```

**Then**: Fetch full show data via `ShowDao.getShowsByIds()`

**UI Context**: Home screen "Recently played" carousel

---

### 7.2 Check if Show Played Recently

**Query**:
```sql
SELECT * FROM recent_shows WHERE showId = :showId
```

**Code Location**: `RecentShowDao.kt:53-54`

**Indexes Used**:
- PRIMARY KEY on `showId` - O(1)

**Performance**: O(1)

**Usage**: UPSERT logic (check exists before INSERT vs UPDATE)

---

### 7.3 Get Most Played Shows

**Use Case**: "Most played" analytics

**Query**:
```sql
SELECT * FROM recent_shows
ORDER BY totalPlayCount DESC, last_played_timestamp DESC
LIMIT 10
```

**Code Location**: `RecentShowDao.kt:128-132`

**Indexes Used**:
- No index on `totalPlayCount` (table is small, acceptable)

**Performance**: O(N log N) sort, but N is small (<100 typically)

**Example Output**:
```
Cornell '77: 12 plays
Veneta '72: 10 plays
Nassau '79: 8 plays
```

**UI Context**: User stats page

---

### 7.4 Shows Played in Time Range

**Query**:
```sql
SELECT * FROM recent_shows
WHERE last_played_timestamp BETWEEN :startTimestamp AND :endTimestamp
ORDER BY last_played_timestamp DESC
```

**Code Location**: `RecentShowDao.kt:113-118`

**Indexes Used**:
- `idx_recent_shows_last_played` - Range scan

**Performance**: O(log N + k) where k = shows in range

**Example**: `getShowsPlayedInRange(lastWeekStart, today)` returns this week's plays

**UI Context**: "This week's plays" analytics

---

### 7.5 Remove Show from Recent (Privacy)

**Query**:
```sql
DELETE FROM recent_shows WHERE showId = :showId
```

**Code Location**: `RecentShowDao.kt:86-87`

**Performance**: O(1) via PRIMARY KEY

**UI Context**: "Remove from recent" menu option

---

### 7.6 Clear Old Shows (Privacy/Storage)

**Query**:
```sql
DELETE FROM recent_shows WHERE last_played_timestamp < :cutoffTimestamp
```

**Code Location**: `RecentShowDao.kt:141-142`

**Performance**: O(N) scan (but table is small)

**Usage**: Privacy settings - "Clear plays older than 90 days"

---

## 8. Popular/Featured Patterns

### 8.1 Top Rated Shows

**Use Case**: "Best shows ever" discovery feature

**Query**:
```sql
SELECT * FROM shows
WHERE averageRating IS NOT NULL
ORDER BY averageRating DESC
LIMIT 20
```

**Code Location**: `ShowDao.kt:68-69`

**Indexes Used**:
- No dedicated index on `averageRating` (rare query)

**Performance**: O(N log N) sort (acceptable for discovery feature)

**Example Output**:
```
1977-05-08 Cornell (4.9★)
1972-08-27 Veneta (4.8★)
1977-05-09 Buffalo (4.7★)
```

**UI Context**: "Top Shows" discovery page

---

### 8.2 Most Recent Shows

**Query**:
```sql
SELECT * FROM shows ORDER BY date DESC LIMIT 20
```

**Code Location**: `ShowDao.kt:71-72`

**Indexes Used**:
- `idx_shows_date` - Fast reverse chronological

**Performance**: O(log N + k) where k = 20

**UI Context**: Home screen "Latest shows"

---

## 9. Collections Patterns

### 9.1 Get All Collections

**Query**:
```sql
SELECT * FROM dead_collections ORDER BY name ASC
```

**Code Location**: `CollectionsDao.kt:24-25`

**Performance**: O(N log N) where N = collections (20-50)

**UI Context**: Collections browser

---

### 9.2 Get Featured Collections

**Query**:
```sql
SELECT * FROM dead_collections ORDER BY totalShows DESC LIMIT 6
```

**Code Location**: `CollectionsDao.kt:42-43`

**Indexes Used**:
- `idx_dead_collections_total_shows` - Sort by size

**Performance**: O(log N + k) where k = 6

**Example**: Dick's Picks, Dave's Picks, Road Trips (largest collections)

**UI Context**: Home screen "Featured Collections"

---

### 9.3 Search Collections

**Query**:
```sql
SELECT * FROM dead_collections
WHERE name LIKE '%' || :query || '%'
   OR description LIKE '%' || :query || '%'
ORDER BY name ASC
```

**Code Location**: `CollectionsDao.kt:54-60`

**Performance**: O(N) scan (acceptable for small table)

**UI Context**: Collection search bar

---

### 9.4 Get Collections by Tag

**Query**:
```sql
SELECT * FROM dead_collections WHERE primaryTag = :tag ORDER BY name ASC
```

**Code Location**: `CollectionsDao.kt:65-66`

**Indexes Used**:
- `idx_dead_collections_primary_tag` - Filter by tag

**Performance**: O(log N + k) where k = collections with tag

**Example**: `getCollectionsByTag("era")` returns era-based collections

**UI Context**: Tag filter tabs

---

### 9.5 Get Collections Containing Show

**Query**:
```sql
SELECT * FROM dead_collections
WHERE showIdsJson LIKE '%' || :showId || '%'
ORDER BY name ASC
```

**Code Location**: `CollectionsDao.kt:113-118`

**Performance**: O(N) scan with string matching (acceptable for small table)

**Example**: `getCollectionsContainingShow("1977-05-08-...")` returns "Dick's Picks Vol. 15"

**UI Context**: Show detail page - "Part of collections: ..."

**Note**: LIKE on JSON is inefficient but acceptable for read-only small dataset

---

## 10. Data Version Queries

### 10.1 Get Current Version

**Query**:
```sql
SELECT * FROM data_version_v2 WHERE id = 1
```

**Code Location**: `DataVersionDao.kt:16-17`

**Performance**: O(1) via PRIMARY KEY (singleton table)

**Returns**: Version info or NULL if no data imported

**UI Context**: About screen, update checker

---

### 10.2 Check if Data Exists

**Query**:
```sql
SELECT COUNT(*) > 0 FROM data_version_v2
```

**Code Location**: `DataVersionDao.kt:26-27`

**Performance**: Fast

**Usage**: First-run detection

---

## Index Summary

This table shows which indexes support which query patterns:

| Index | Query Patterns Supported | Critical? |
|-------|-------------------------|-----------|
| `idx_shows_date` | Chronological browse, navigation, date ranges | ✅ YES |
| `idx_shows_year` | Year filtering | ✅ YES |
| `idx_shows_yearMonth` | Month filtering | ✅ YES |
| `idx_shows_city` | Location browse | ✅ YES |
| `idx_shows_state` | Location browse | ✅ YES |
| `idx_recordings_show_id_rating` | Best recording, all recordings | ✅ YES |
| `idx_recordings_source_type` | Filter by source type | Moderate |
| `idx_recordings_rating` | Top rated recordings | Moderate |
| `idx_library_shows_is_pinned` | Library pins first | ✅ YES |
| `idx_library_shows_added_at` | Library chronological | ✅ YES |
| `idx_recent_shows_last_played DESC` | Recent history | ✅ YES |
| FTS4 `show_search` | Full-text search | ✅ YES |

---

## Performance Notes

### Fast Queries (< 10ms)

- Single show lookup by ID (PRIMARY KEY)
- Library membership check (PRIMARY KEY)
- Get best recording (composite index)
- Recent shows (indexed timestamp)

### Moderate Queries (10-50ms)

- Year/month filtering (indexed, moderate result sets)
- Location filtering (indexed, variable result sets)
- FTS4 search (depends on query complexity)

### Slow Queries (> 50ms)

- Venue LIKE search (full table scan)
- Song LIKE search (full table scan)
- Top rated shows (sort entire table)
- Collection searches (small table, acceptable)

---

## Query Optimization Patterns

### 1. Composite Indexes

`idx_recordings_show_id_rating` covers both filter AND sort:
```sql
WHERE show_id = ? ORDER BY rating DESC
```

Single index handles entire query (no temp table needed).

### 2. Covering Indexes

FTS4 returns only `showId`, then fetch full data:
```sql
-- Step 1: FTS4 (fast)
SELECT showId FROM show_search WHERE ...

-- Step 2: Fetch full data
SELECT * FROM shows WHERE showId IN (...)
```

Two queries more efficient than JOINing FTS4 virtual table.

### 3. EXISTS for Boolean Checks

```sql
SELECT EXISTS(SELECT 1 FROM library_shows WHERE showId = ?)
```

Faster than `SELECT COUNT(*)` - stops at first match.

### 4. DESC Index for Recent Queries

```sql
INDEX idx_recent_shows_last_played DESC
```

Avoids reverse sort for `ORDER BY timestamp DESC` queries.

---

## See Also

- [Design Philosophy](design-philosophy.md) - Why these patterns
- [Index Documentation](entities/shows.md#indexes) - Index details
- [FTS4 Search](entities/show-search.md) - Search implementation
- [Data Flow](data-flow.md) - How queries fit into app flow
