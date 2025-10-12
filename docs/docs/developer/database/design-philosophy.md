# Design Philosophy

This document explains the key design decisions that shape the database architecture. Understanding the "why" behind these choices is essential for maintaining consistency when extending the system or implementing the iOS version.

---

## Core Principles

### 1. Read-Heavy Optimization

**Decision**: Optimize for fast reads at the expense of write complexity.

**Why**: The app's primary use case is browsing and searching shows. Users spend 99% of their time reading data:

- Browsing shows by date/venue/location
- Searching for specific shows
- Loading show details with recordings
- Navigating chronologically through shows

Data writes are rare and confined to:

- Initial import (one-time setup)
- User library changes (occasional)
- Play history updates (frequent but simple)

**Implications**:

- Strategic denormalization to avoid JOINs
- Extensive indexing on frequently-queried columns
- Precomputed aggregates (e.g., `recordingCount`, `averageRating`)
- Acceptable data duplication for performance

---

## Key Design Decisions

### 1. Denormalization (Venues in Shows)

**Decision**: Store venue information directly in the `shows` table instead of a normalized `venues` table.

#### The Normalized Approach (Rejected)

```sql
-- Separate venues table
CREATE TABLE venues (
    venue_id INTEGER PRIMARY KEY,
    name TEXT,
    city TEXT,
    state TEXT,
    country TEXT
);

CREATE TABLE shows (
    show_id TEXT PRIMARY KEY,
    venue_id INTEGER,
    date TEXT,
    FOREIGN KEY (venue_id) REFERENCES venues(id)
);

-- Query requires JOIN
SELECT * FROM shows
JOIN venues ON shows.venue_id = venues.id
WHERE venues.city = 'Ithaca';
```

**Problems**:

- Every show query requires a JOIN (slow)
- More complex queries for simple browsing
- Additional table to manage

#### The Denormalized Approach (Chosen)

```sql
-- Venue data inline
CREATE TABLE shows (
    show_id TEXT PRIMARY KEY,
    date TEXT,
    venue_name TEXT,
    city TEXT,
    state TEXT,
    country TEXT
    -- ... other fields
);

-- Simple, fast query
SELECT * FROM shows WHERE city = 'Ithaca';
```

**Benefits**:

- ✅ No JOINs for 90% of queries
- ✅ Single-table queries are simpler and faster
- ✅ All show data in one row (better cache locality)
- ✅ Indexes on venue columns work efficiently

**Trade-offs**:

- ❌ Data duplication: "Barton Hall" repeated for every show there
- ❌ Update complexity: Changing venue name requires updating multiple rows
- ❌ Storage overhead: ~20-50 bytes per show for venue strings

**Why Acceptable**:

1. **Data is immutable**: Show/venue data doesn't change after import
2. **Small storage cost**: Extra ~50KB for 2,500 shows (negligible)
3. **Huge performance gain**: Eliminates thousands of JOINs per session
4. **Simpler queries**: Easier to write, read, and maintain

**When to Update a Venue**: Never in practice. If a venue name correction is needed, it happens during metadata package update, not at runtime.

---

### 2. JSON Storage (Setlists, Lineups, Tags)

**Decision**: Store complex nested structures as JSON strings in TEXT columns instead of normalized relational tables.

#### Examples

**Setlist JSON**:

```json
[
  {
    "set": "Set 1",
    "songs": [
      {"name": "Scarlet Begonias", "segue": true},
      {"name": "Fire on the Mountain", "segue": false}
    ]
  },
  {
    "set": "Set 2",
    "songs": [
      {"name": "Estimated Prophet", "segue": true},
      {"name": "Eyes of the World", "segue": false}
    ]
  }
]
```

Stored as: `setlistRaw TEXT` column containing the JSON string above.

**Lineup JSON**:

```json
[
  {"name": "Jerry Garcia", "instruments": "guitar, vocals"},
  {"name": "Bob Weir", "instruments": "guitar, vocals"},
  {"name": "Phil Lesh", "instruments": "bass, vocals"}
]
```

Stored as: `lineupRaw TEXT` column.

#### The Normalized Approach (Rejected)

```sql
-- Separate tables for setlists
CREATE TABLE setlist_sets (
    set_id INTEGER PRIMARY KEY,
    show_id TEXT,
    set_name TEXT,
    sequence INTEGER,
    FOREIGN KEY (show_id) REFERENCES shows(id)
);

CREATE TABLE setlist_songs (
    song_id INTEGER PRIMARY KEY,
    set_id INTEGER,
    song_name TEXT,
    position INTEGER,
    has_segue BOOLEAN,
    FOREIGN KEY (set_id) REFERENCES setlist_sets(id)
);

-- Complex query with multiple JOINs
SELECT * FROM shows
JOIN setlist_sets ON shows.id = setlist_sets.show_id
JOIN setlist_songs ON setlist_sets.id = setlist_songs.set_id
WHERE shows.show_id = '1977-05-08-...';
```

**Problems**:

- ❌ Requires 2+ JOINs to display setlist
- ❌ Multiple tables to manage
- ❌ Complex UPDATE logic (must update 3 tables atomically)
- ❌ Schema inflexibility (what about "Encore 2"?)

#### The JSON Approach (Chosen)

```sql
CREATE TABLE shows (
    show_id TEXT PRIMARY KEY,
    setlist_raw TEXT,  -- JSON string
    -- ...
);

-- Simple query
SELECT setlist_raw FROM shows WHERE show_id = '1977-05-08-...';

-- Parse JSON in application code
val setlist = Json.decodeFromString<Setlist>(show.setlistRaw)
```

**Benefits**:

- ✅ Flexible schema: Handles variable setlist structures (2 sets, 3 sets, encores)
- ✅ Atomic updates: Change entire setlist in one UPDATE
- ✅ Simpler queries: Single column read, parse in app
- ✅ Display-optimized: JSON maps directly to UI models

**Trade-offs**:

- ❌ Can't query inside JSON efficiently (e.g., "find shows with song X")
- ❌ No database schema validation (relies on app-level parsing)
- ❌ Must deserialize JSON on every read (but fast with kotlinx.serialization)

**Why Acceptable**:

1. **Display-only**: We never need to query "shows with song X" using the setlist JSON
2. **Search handled separately**: `songList` column (comma-separated string) is indexed by FTS4 for search
3. **Variable structure**: Setlists vary wildly (some shows have encores, some don't; some have 3 sets, some 2)
4. **Atomic display**: UI displays entire setlist at once, so loading full JSON is natural

#### When to Use JSON vs Normalized Tables

**Use JSON when**:

- ✅ Data is displayed as-is (no need to query inside structure)
- ✅ Schema varies between records (flexible structure needed)
- ✅ Data is updated atomically (all or nothing)
- ✅ No referential integrity needed

**Use normalized tables when**:

- ✅ Need to query inside structure (e.g., "find all songs in key of G")
- ✅ Need referential integrity (e.g., FK to songs table)
- ✅ Data is updated incrementally (e.g., add one song to setlist)
- ✅ Need aggregations across records (e.g., "most played songs")

#### Extracted Fields for Search

While JSON is great for display, we still need to search song names. Solution: **extract flat strings during import**:

- `songList`: `"Scarlet Begonias,Fire on the Mountain,Estimated Prophet"` (comma-separated)
- `memberList`: `"Jerry Garcia,Bob Weir,Phil Lesh"` (comma-separated)

These are indexed by FTS4 for search, while JSON is used for rich UI display.

---

### 3. FTS4 for Search

**Decision**: Use SQLite FTS4 (Full-Text Search) with custom tokenizer for show searching.

#### The Problem

Users search for shows in many ways:

- By date: `"5-8-77"`, `"5/8/77"`, `"May 8 1977"`, `"1977-05-08"`
- By venue: `"Cornell"`, `"Barton Hall"`
- By location: `"Ithaca"`, `"NY"`, `"New York"`
- By band member: `"Jerry Garcia"`
- By song: `"Scarlet Begonias"`

Search is a **critical feature** - users expect to find shows regardless of how they type the query.

#### Alternative Approaches (Rejected)

**A. LIKE Queries**:

```sql
SELECT * FROM shows
WHERE venue_name LIKE '%Cornell%'
   OR city LIKE '%Cornell%'
   OR date LIKE '%Cornell%';
```

**Problems**:

- ❌ Full table scan (slow on 2,500+ rows)
- ❌ No relevance ranking
- ❌ Must know which column to search
- ❌ Can't handle token variations (`5-8-77` vs `5/8/77`)
- ❌ Indexes don't help `LIKE '%pattern%'` (only prefix)

**B. Multiple Indexed Columns**:

```sql
CREATE INDEX idx_venue ON shows(venue_name);
CREATE INDEX idx_city ON shows(city);
-- Query with LIKE still requires scan
```

**Problems**:

- ❌ Indexes useless for `%pattern%` matches
- ❌ No unified search across columns
- ❌ No relevance ranking

#### The FTS4 Approach (Chosen)

```sql
CREATE VIRTUAL TABLE show_search USING fts4(
    showId,
    searchText,
    tokenize=unicode61 "tokenchars=-."
);

-- Fast, ranked search
SELECT showId FROM show_search
WHERE show_search MATCH 'Cornell'
ORDER BY rank;
```

**How It Works**:

1. **Inverted Index**: FTS4 builds an inverted index (token → document mapping)
2. **Fast Lookup**: Search is O(log N) in token count, not row count
3. **BM25 Ranking**: Ranks results by relevance (better matches first)
4. **Unified Search**: One index covers all searchable fields

**Benefits**:

- ✅ Fast: O(log N) search via inverted index
- ✅ Ranked: BM25 algorithm ranks by relevance
- ✅ Unified: Search all fields in one query
- ✅ Token control: Preserve punctuation in dates
- ✅ Advanced queries: Prefix (`Corn*`), phrase (`"Barton Hall"`), boolean (`Cornell AND 1977`)

**Trade-offs**:

- ❌ Index size: ~500KB extra (acceptable)
- ❌ Import time: Building index takes ~1-2 seconds (one-time cost)
- ❌ Update complexity: Must rebuild FTS entry when show changes

**Why Worth It**: Search is a primary interaction pattern. Fast, robust search is worth the overhead.

#### Custom Tokenizer Configuration

```kotlin
@Fts4(
    tokenizer = "unicode61",
    tokenizerArgs = ["tokenchars=-."]
)
```

**What This Does**:

- **unicode61**: Unicode-aware tokenizer (handles international characters)
- **tokenchars=-.**: Treat dashes and dots as part of tokens, not separators

**Example**:

Without `tokenchars`:

- `"5-8-77"` → `["5", "8", "77"]` (3 separate tokens)
- Searching `"5-8-77"` won't match (looking for exact phrase of 3 tokens)

With `tokenchars=-.`:

- `"5-8-77"` → `["5-8-77"]` (1 token)
- Searching `"5-8-77"` matches exactly
- Also enables `"5.8.77"`, `"1977-05-08"` as single tokens

#### Why So Many Date Variations?

The `searchText` field includes extensive date format variations:

```
"1977-05-08 5-8-77 5/8/77 5.8.77 77 197 1977-5 1977-05 ..."
```

**Why**: Users search in many ways based on their background:

- **Deadheads**: Short dates like `"5-8-77"` (common notation)
- **Calendar apps**: Slashes like `"5/8/77"` (US format)
- **International**: Dots like `"5.8.77"` (European format)
- **Database format**: ISO like `"1977-05-08"`
- **Partial**: Month/year like `"May 1977"`, decade like `"77"` or `"1970s"`

Without extensive date variations, users would frequently fail to find shows they know exist. The extra index space (~100 bytes per show) is worth the UX improvement.

**Code Location**: `DataImportService.kt:460` (searchText generation)

---

### 4. UPSERT Pattern (Recent Shows)

**Decision**: Recent shows table uses one record per show, updated on each play (UPSERT pattern).

#### The Problem

Track which shows the user has recently played for quick access.

#### Alternative Approach (Rejected): Event Log

```sql
-- One row per play event
CREATE TABLE recent_plays (
    id INTEGER PRIMARY KEY,
    show_id TEXT,
    played_at INTEGER
);

-- Query requires GROUP BY
SELECT show_id, MAX(played_at) as last_played, COUNT(*) as play_count
FROM recent_plays
GROUP BY show_id
ORDER BY last_played DESC
LIMIT 8;
```

**Problems**:

- ❌ `GROUP BY` is expensive (aggregates all plays for all shows)
- ❌ Table grows unbounded (one row per play forever)
- ❌ Need pruning logic (complex retention policy)
- ❌ Indexes don't help GROUP BY much
- ❌ Slower as table grows (10, 100, 1000+ plays)

#### The UPSERT Approach (Chosen)

```sql
-- One record per show
CREATE TABLE recent_shows (
    show_id TEXT PRIMARY KEY,
    last_played_timestamp INTEGER,
    first_played_timestamp INTEGER,
    total_play_count INTEGER
);

-- Simple, fast query
SELECT * FROM recent_shows
ORDER BY last_played_timestamp DESC
LIMIT 8;
```

**UPSERT Logic** (in app code):

```kotlin
val existing = recentShowDao.getShowById(showId)
if (existing != null) {
    // Update: increment counter, update timestamp
    recentShowDao.updateShow(
        showId = showId,
        timestamp = System.currentTimeMillis(),
        playCount = existing.totalPlayCount + 1
    )
} else {
    // Insert: new show with count=1
    recentShowDao.insert(RecentShowEntity.createNew(showId))
}
```

**Benefits**:

- ✅ Simple queries: `ORDER BY` with index is O(log N)
- ✅ Bounded table: Max one row per show (~2,500 max, typically <100)
- ✅ No aggregation: Pre-computed play counts
- ✅ Easy pruning: `DELETE WHERE last_played < cutoff`
- ✅ Fast regardless of play count: Always 1 row per show

**Trade-offs**:

- ❌ Lose granular play history (can't reconstruct "played 3 times on Tuesday")
- ❌ Two operations per play (SELECT then UPDATE/INSERT)

**Why Acceptable**:

1. **UI needs**: Recent shows UI only needs "last played" and "total plays", not full history
2. **Performance**: Much simpler and faster than GROUP BY approach
3. **Storage**: Bounded table size, no unbounded growth
4. **Analytics**: Can add separate analytics table later if needed

**Code Location**: `RecentShowEntity.kt:29`, `RecentShowDao.kt:17`

---

### 5. Two-Tier Data Architecture

**Decision**: Show/recording catalog in database, Archive.org API responses in filesystem cache.

#### The Problem

The app needs two types of data:

1. **Catalog data**: Shows, recordings (metadata), collections - queryable, persistent
2. **Runtime data**: Track lists, reviews, detailed metadata - large, ephemeral, changes on Archive.org

#### Alternative Approach (Rejected): Everything in Database

```sql
-- Store tracks in database
CREATE TABLE tracks (
    track_id INTEGER PRIMARY KEY,
    recording_id TEXT,
    title TEXT,
    duration TEXT,
    format TEXT,
    filename TEXT
);

-- Store reviews in database
CREATE TABLE reviews (
    review_id INTEGER PRIMARY KEY,
    recording_id TEXT,
    reviewer TEXT,
    rating INTEGER,
    text TEXT,
    date TEXT
);
```

**Problems**:

- ❌ **Massive initial import**: Must fetch tracks/reviews for 20,000 recordings (would take hours)
- ❌ **Large database**: ~100 tracks × 20,000 recordings = 2 million rows (100+ MB)
- ❌ **Stale data**: Tracks/reviews change on Archive.org, need refresh mechanism
- ❌ **Complex cache invalidation**: Which tracks are stale? How often to refresh?
- ❌ **Wasted storage**: User views ~10-20 shows per session, but storage for all 20,000
- ❌ **No offline benefit**: Tracks are just metadata; actual audio still requires streaming

#### The Two-Tier Approach (Chosen)

**Tier 1: Database (Persistent Catalog)**

- **What**: Shows, recordings (metadata only), collections, library, recent shows
- **Lifecycle**: Imported once on setup, rarely updated
- **Optimization**: Indexed, FTS4, denormalized for fast queries
- **Size**: ~10-15 MB

**Tier 2: Filesystem Cache (Ephemeral Runtime Data)**

- **What**: Track lists, reviews, detailed recording metadata
- **Lifecycle**: Fetched on-demand from Archive.org API, cached 24 hours
- **Location**: `<cache_dir>/archive/{recordingId}.{type}.json`
- **Size**: Variable, cleaned up automatically

**Cache Structure**:

```
<app_cache_dir>/
  archive/
    gd1977-05-08.sbd.miller.97065.flac16.metadata.json
    gd1977-05-08.sbd.miller.97065.flac16.tracks.json
    gd1977-05-08.sbd.miller.97065.flac16.reviews.json
```

**Cache Flow**:

```
User opens show detail page
  ↓
Check: file exists AND not expired (< 24h old)?
  ↓
YES: Read JSON from file (fast)
NO:  Fetch from Archive.org API → Write to file → Read JSON
```

**Benefits**:

- ✅ **Fast initial setup**: No need to fetch all tracks (5-10 seconds vs hours)
- ✅ **Small database**: Only catalog data (~15 MB vs 100+ MB)
- ✅ **Fresh data**: Re-fetch after 24h, gets updates from Archive.org
- ✅ **Simple invalidation**: Delete file or check timestamp
- ✅ **On-demand loading**: Only fetch data for shows user views
- ✅ **Separation of concerns**: Database is queryable catalog, cache is large payloads

**Trade-offs**:

- ❌ **First load slower**: Must fetch from API (500-2000ms vs 10ms)
- ❌ **No offline tracks**: Can't search track names offline (but catalog search works)
- ❌ **Cache management**: Need cleanup for old files (OS handles this automatically)

**Why Worth It**:

1. **Different lifecycles**: Catalog is static, tracks/reviews change
2. **Different access patterns**: Catalog queried constantly, tracks loaded once per show
3. **Storage efficiency**: Only cache what user views
4. **Fresh data**: Always get latest reviews/tracks after 24h

**iOS Implementation Note**: Use the same pattern. Filesystem cache with `FileManager`, not Core Data for tracks/reviews.

**Code Location**: `ArchiveServiceImpl.kt:25`

---

## Summary

The database design prioritizes:

1. **Read performance** over write simplicity (denormalization, indexes, precomputed fields)
2. **Flexibility** over normalization (JSON for variable structures)
3. **Robust search** over simple queries (FTS4 with custom tokenization)
4. **Simplicity** over granular history (UPSERT pattern for recent shows)
5. **Efficiency** over completeness (two-tier architecture)

These decisions make sense for a read-heavy mobile app with a static catalog and dynamic runtime needs. When implementing the iOS version or extending the Android version, maintain these principles for consistency.

---

## References

- [Data Sources](data-sources.md) - Where data comes from
- [Query Patterns](query-patterns.md) - How these decisions enable fast queries
- [Platform Implementation](platform-implementation.md) - How to replicate on iOS
