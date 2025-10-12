# ShowSearchEntity (show_search)

FTS4 (Full-Text Search) entity for fast text search on show data. Provides inverted index for searching venues, locations, dates, and years with relevance ranking.

---

## Purpose

`ShowSearchEntity` enables efficient full-text search:

- **Fast text search** - Search by venue name, city, date formats
- **Relevance ranking** - BM25 algorithm ranks results by relevance
- **Tokenization** - Unicode61 with special handling for dates (5-8-77, 5.8.77)
- **Inverted index** - Fast lookups without table scans

**Why FTS4**: Alternative would be `LIKE '%query%'` queries on shows table (slow, no ranking). FTS4 provides orders of magnitude faster search with relevance scoring.

---

## Schema

### Table Definition

```sql
-- FTS4 virtual table
CREATE VIRTUAL TABLE show_search USING fts4(
    rowid INTEGER PRIMARY KEY,
    show_id TEXT,
    search_text TEXT,
    tokenize=unicode61 tokenchars=-\.
);
```

**Virtual Table**: FTS4 creates multiple shadow tables for inverted index

**No Indexes**: FTS4 manages its own inverted index structures

---

## Fields

### Primary Key

#### `rowid` (INTEGER, PRIMARY KEY, AUTO-GENERATED)

FTS4 internal row identifier.

**Auto-generated**: Assigned by FTS4 automatically

**Not Used Directly**: Application uses `show_id` for references

**Usage**: FTS4 internal indexing

---

### Foreign Key (Logical, Not Enforced)

#### `show_id` (TEXT, NOT NULL)

References parent show.

**Example**: `"1977-05-08-barton-hall-cornell-u-ithaca-ny-usa"`

**Logical Foreign Key**: References `shows.show_id` (not enforced by database)

**Why Not Enforced**: FTS4 virtual tables don't support foreign key constraints

**Manual Sync Required**: Must keep in sync with shows table

**Usage**: Join search results with shows table for full data

---

### Searchable Content

#### `search_text` (TEXT, NOT NULL, INDEXED BY FTS4)

Rich searchable content composed from show fields.

**Format**: Space-separated tokens

**Example**: `"1977-05-08 5-8-77 5/8/77 Barton Hall Cornell University Ithaca NY USA New York"`

**Components**:
- **ISO Date**: `1977-05-08`
- **Abbreviated Date**: `5-8-77`
- **Slash Date**: `5/8/77`
- **Venue Name**: `Barton Hall`
- **Venue Full Name**: `Barton Hall, Cornell University`
- **City**: `Ithaca`
- **State/Region**: `NY`, `New York`
- **Country**: `USA`
- **Year**: `1977`

**Why Multiple Formats**: Users search in different ways ("5-8-77" vs "Barton Hall" vs "Ithaca 1977")

**Indexed**: FTS4 automatically creates inverted index on this field

**Usage**: FTS4 MATCH queries search this field

---

## FTS4 Configuration

### Tokenizer: unicode61

**What**: Unicode-aware tokenizer with normalization

**Features**:
- Case-insensitive (converts to lowercase)
- Diacritic folding (é → e)
- Unicode normalization (NFD)
- Word boundary detection

**Why unicode61**: Better than default `simple` tokenizer for international text

---

### Token Characters: `-` and `.`

**Configuration**: `tokenchars=-.`

**Effect**: Treats dashes and periods as part of tokens (not word separators)

**Examples**:
- `5-8-77` → single token (not split into `5`, `8`, `77`)
- `5.8.77` → single token
- `Barton-Hall` → single token

**Why Important**: Users search for dates like "5-8-77" expecting exact match

**Trade-off**: Hyphenated words like "Barton-Hall" become single token (both advantage and limitation)

---

### Ranking: BM25

**Algorithm**: BM25 (Best Matching 25)

**Features**:
- Term frequency scoring
- Inverse document frequency
- Document length normalization
- Relevance-ranked results

**Usage**: Results ordered by relevance (most relevant first)

**Example**:
- Query: "Ithaca 1977"
- Result 1: "Barton Hall, Ithaca, NY 1977-05-08" (higher rank - matches both terms)
- Result 2: "Ithaca, NY 1977-11-02" (lower rank - matches both but less prominent venue)
- Result 3: "Ithaca, NY 1976-05-03" (even lower - only matches one term)

---

## Relationships

### To ShowEntity (1:1, Logical)

**Cardinality**: Each search entry references one show, each show has one search entry

**Not Enforced**: FTS4 virtual tables don't support foreign key constraints

**Manual Sync**: Application must maintain consistency

**Sync Pattern**:
```kotlin
// When inserting show
showDao.insert(showEntity)
showSearchDao.insertOrUpdate(ShowSearchEntity(
    showId = showEntity.showId,
    searchText = buildSearchText(showEntity)
))

// When updating show
showDao.update(showEntity)
showSearchDao.insertOrUpdate(ShowSearchEntity(
    showId = showEntity.showId,
    searchText = buildSearchText(showEntity)
))

// When deleting show
showDao.delete(showId)
showSearchDao.removeShowFromIndex(showId)
```

---

## Building Search Text

### Construction Logic

```kotlin
fun buildSearchText(show: ShowEntity): String {
    val parts = mutableListOf<String>()

    // Date formats
    parts.add(show.date)  // "1977-05-08"
    parts.add(show.displayDate)  // "5-8-77"
    parts.add(show.date.replace("-", "/"))  // "1977/05/08"

    // Venue
    parts.add(show.venueName)  // "Barton Hall"
    if (show.venueFullName != show.venueName) {
        parts.add(show.venueFullName)  // "Barton Hall, Cornell University"
    }

    // Location
    parts.add(show.venueCity)  // "Ithaca"
    parts.add(show.venueStateCode)  // "NY"
    parts.add(show.venueStateName)  // "New York"
    parts.add(show.venueCountry)  // "USA"

    // Year (for "1977" searches)
    parts.add(show.year.toString())  // "1977"

    // Tour name (if exists)
    show.tourName?.let { parts.add(it) }

    return parts.joinToString(" ")
}
```

---

## Common Queries

### Search Shows by Text

```sql
SELECT show_id FROM show_search
WHERE show_search MATCH 'Ithaca 1977'
LIMIT 50;
```

**Performance**: Fast via FTS4 inverted index

**Returns**: Show IDs ordered by relevance (BM25)

**Then Fetch Full Data**:
```kotlin
val showIds = showSearchDao.searchShows(query)
val shows = showDao.getShowsByIds(showIds)
```

---

### Search with Special Characters

FTS4 supports special query operators:

**AND (implicit)**:
```sql
MATCH 'Ithaca 1977'  -- Both terms required (implicit AND)
```

**OR**:
```sql
MATCH 'Ithaca OR Buffalo'  -- Either term matches
```

**NOT**:
```sql
MATCH 'Ithaca NOT 1977'  -- Has "Ithaca" but not "1977"
```

**Phrase**:
```sql
MATCH '"Barton Hall"'  -- Exact phrase (both words adjacent)
```

**Prefix**:
```sql
MATCH 'Barton*'  -- Matches "Barton", "Barton-Hall", etc.
```

---

### Count Indexed Shows

```sql
SELECT COUNT(*) FROM show_search;
```

**Performance**: Fast (FTS4 maintains count)

**Usage**: Verify import completeness, debugging

---

### Check if Show is Indexed

```sql
SELECT COUNT(*) FROM show_search
WHERE show_id = '1977-05-08-barton-hall-cornell-u-ithaca-ny-usa';
```

**Performance**: Fast via FTS4 index

**Returns**: 1 if indexed, 0 if not

---

### Remove Show from Index

```sql
DELETE FROM show_search
WHERE show_id = '1977-05-08-barton-hall-cornell-u-ithaca-ny-usa';
```

**Performance**: Fast

**Usage**: Sync after deleting show from main table

---

### Rebuild Entire Index

```kotlin
suspend fun rebuildSearchIndex() {
    // Clear existing index
    showSearchDao.clearAllSearchData()

    // Rebuild from shows table
    val allShows = showDao.getAllShows()
    val searchEntries = allShows.map { show ->
        ShowSearchEntity(
            showId = show.showId,
            searchText = buildSearchText(show)
        )
    }
    showSearchDao.insertAll(searchEntries)
}
```

**Usage**: After major data updates, corruption recovery

---

## Common Operations

### Search and Fetch Full Results

```kotlin
suspend fun searchShows(query: String): List<Show> {
    // FTS4 search returns show IDs ordered by relevance
    val showIds = showSearchDao.searchShows(query)

    // Fetch full show data
    val shows = showDao.getShowsByIds(showIds)

    // Preserve relevance order from FTS4
    return showIds.mapNotNull { id ->
        shows.find { it.showId == id }
    }
}
```

---

### Reactive Search

```kotlin
// ViewModel
fun searchShows(query: String): StateFlow<List<Show>> {
    return showSearchDao.searchShowsFlow(query)
        .map { showIds ->
            showDao.getShowsByIds(showIds)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
```

---

### Sync Search Index on Show Update

```kotlin
suspend fun updateShow(show: ShowEntity) {
    database.withTransaction {
        // Update main show record
        showDao.update(show)

        // Update search index
        showSearchDao.insertOrUpdate(
            ShowSearchEntity(
                showId = show.showId,
                searchText = buildSearchText(show)
            )
        )
    }
}
```

---

## Query Sanitization

FTS4 has special characters that need escaping:

**Special Characters**: `"` (quotes), `*` (prefix), `-` (NOT operator), `(` `)` (grouping)

**Sanitization**:
```kotlin
fun sanitizeFtsQuery(query: String): String {
    // Remove special FTS operators if user input should be literal
    return query
        .replace("\"", "")  // Remove quotes
        .replace("*", "")   // Remove wildcards
        .replace("(", "")   // Remove grouping
        .replace(")", "")
        .trim()
}

// Usage
val sanitized = sanitizeFtsQuery(userInput)
val results = showSearchDao.searchShows(sanitized)
```

**When to Sanitize**: User-facing search boxes (prevent injection, syntax errors)

**When NOT to Sanitize**: Power user features with explicit FTS syntax support

---

## Performance Characteristics

### Query Speed

**FTS4 Search**: O(log n) via inverted index - extremely fast even for thousands of shows

**LIKE Alternative**: O(n) table scan - slow for large datasets

**Comparison**:
- FTS4: ~1-5ms for typical queries
- LIKE: ~50-200ms for full table scan

### Storage Overhead

**FTS4 Shadow Tables**: ~3-4x storage of original text data

**Example**:
- Shows table: 2,400 shows × ~200 bytes = ~480 KB
- FTS4 index: ~480 KB × 3.5 = ~1.7 MB

**Trade-off**: Extra storage for dramatically faster search

### Index Build Time

**Initial Build**: ~100-500ms for 2,400 shows (one-time on import)

**Incremental Updates**: ~1-2ms per show (on show insert/update)

---

## FTS4 vs FTS5

**Current**: Using FTS4 with unicode61 tokenizer

**FTS5 Alternative**: Newer, faster, more features

**Why FTS4**:
- Room has better FTS4 support (as of Room 2.6)
- FTS4 is mature and stable
- FTS5 migration path exists if needed

**FTS5 Advantages** (for future):
- Better performance (especially for large indexes)
- More flexible ranking
- Column filters
- Tokenizer improvements

**Migration Path**:
```sql
-- Create FTS5 table
CREATE VIRTUAL TABLE show_search_fts5 USING fts5(
    show_id UNINDEXED,
    search_text,
    tokenize='unicode61 tokenchars "-.\_"'
);

-- Copy data
INSERT INTO show_search_fts5 SELECT show_id, search_text FROM show_search;

-- Drop old table
DROP TABLE show_search;
```

---

## Debugging FTS4

### View FTS4 Shadow Tables

```sql
-- FTS4 creates these shadow tables:
-- show_search_content (document storage)
-- show_search_segments (b-tree segments)
-- show_search_segdir (segment directory)

SELECT * FROM show_search_content LIMIT 10;
```

### FTS4 Integrity Check

```sql
INSERT INTO show_search(show_search) VALUES('integrity-check');
```

**Returns**: Empty result if index is valid, error if corrupted

### FTS4 Optimize

```sql
INSERT INTO show_search(show_search) VALUES('optimize');
```

**Effect**: Merges FTS4 segments for better performance

**Usage**: After bulk imports or many incremental updates

---

## Code Locations

### Android

- **Entity**: `androidApp/v2/core/database/src/main/java/com/deadly/v2/core/database/entities/ShowSearchEntity.kt:28`
- **DAO**: `androidApp/v2/core/database/src/main/java/com/deadly/v2/core/database/dao/ShowSearchDao.kt:18`

### iOS

TBD (to be implemented with GRDB FTS5)

**iOS Note**: GRDB supports FTS5 (not FTS4). iOS implementation should use FTS5 with similar tokenizer configuration.

---

## Implementation Notes

### Import Process

During metadata import:

1. Import shows to `shows` table
2. Build search text for each show
3. Bulk insert to `show_search` table
4. Run FTS4 optimize

```kotlin
suspend fun importShows(shows: List<ShowEntity>) {
    database.withTransaction {
        // Insert shows
        showDao.insertAll(shows)

        // Build search index
        val searchEntries = shows.map { show ->
            ShowSearchEntity(
                showId = show.showId,
                searchText = buildSearchText(show)
            )
        }
        showSearchDao.insertAll(searchEntries)

        // Optimize FTS4 index
        database.execSQL("INSERT INTO show_search(show_search) VALUES('optimize')")
    }
}
```

---

### Search UX Patterns

**Debouncing**: Delay search execution until user stops typing
```kotlin
val searchQuery = MutableStateFlow("")
val searchResults = searchQuery
    .debounce(300)  // Wait 300ms after last keystroke
    .flatMapLatest { query ->
        if (query.length < 2) {
            flowOf(emptyList())
        } else {
            searchShowsFlow(query)
        }
    }
    .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
```

**Minimum Length**: Don't search for 1-character queries (too many results)

**Empty Query**: Return recent/popular shows instead of all shows

---

## See Also

- [ShowEntity](shows.md) - Parent show entity
- [Design Philosophy](../design-philosophy.md#3-fts4-for-search) - Why FTS4
- [Data Sources](../data-sources.md) - Where show data comes from
