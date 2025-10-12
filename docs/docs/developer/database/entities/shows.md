# ShowEntity (shows)

The core entity representing a single Grateful Dead concert. This is the atomic unit of the application - everything else relates to or extends shows.

---

## Purpose

`ShowEntity` stores comprehensive metadata about a Grateful Dead concert including:

- **Date and venue** - When and where the show happened
- **Setlist and lineup** - What was played and who played it
- **Recording information** - Available recordings and quality ratings
- **Library status** - Whether user has saved this show

**Why Denormalized**: Venue information is stored directly in show records (not a separate `venues` table) to eliminate JOINs for the most common query patterns. See [Design Philosophy](../design-philosophy.md#1-denormalization-venues-in-shows).

---

## Schema

### Table Definition

```sql
CREATE TABLE shows (
    show_id TEXT PRIMARY KEY,

    -- Date components
    date TEXT NOT NULL,
    year INTEGER NOT NULL,
    month INTEGER NOT NULL,
    year_month TEXT NOT NULL,

    -- Show metadata
    band TEXT NOT NULL,
    url TEXT,

    -- Venue (denormalized)
    venue_name TEXT NOT NULL,
    city TEXT,
    state TEXT,
    country TEXT NOT NULL DEFAULT 'USA',
    location_raw TEXT,

    -- Setlist
    setlist_status TEXT,
    setlist_raw TEXT,
    song_list TEXT,

    -- Lineup
    lineup_status TEXT,
    lineup_raw TEXT,
    member_list TEXT,

    -- Multiple shows same date
    show_sequence INTEGER NOT NULL DEFAULT 1,

    -- Recording data
    recordings_raw TEXT,
    recording_count INTEGER NOT NULL DEFAULT 0,
    best_recording_id TEXT,
    average_rating REAL,
    total_reviews INTEGER NOT NULL DEFAULT 0,

    -- Library status (denormalized)
    is_in_library INTEGER NOT NULL DEFAULT 0,
    library_added_at INTEGER,

    -- Timestamps
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

### Indexes

```sql
CREATE INDEX idx_shows_date ON shows(date);
CREATE INDEX idx_shows_year ON shows(year);
CREATE INDEX idx_shows_year_month ON shows(year_month);
CREATE INDEX idx_shows_venue_name ON shows(venue_name);
CREATE INDEX idx_shows_city ON shows(city);
CREATE INDEX idx_shows_state ON shows(state);
```

---

## Fields

### Primary Key

#### `show_id` (TEXT, PRIMARY KEY)

Unique identifier for the show.

**Format**: `{date}-{venue-slug}-{city-slug}-{state}-{country}`

**Example**: `"1977-05-08-barton-hall-cornell-u-ithaca-ny-usa"`

**Why This Format**:
- Self-describing (contains date and location)
- URL-friendly (no special characters)
- Globally unique (date + venue + location)
- Human-readable for debugging

**Generation**: Created by metadata package during compilation, not by app.

---

### Date Fields

#### `date` (TEXT, NOT NULL, INDEXED)

Full ISO date of the show.

**Format**: `"YYYY-MM-DD"`

**Example**: `"1977-05-08"`

**Indexed**: Yes - chronological browsing and date range queries

**Usage**:
- Primary sorting field (chronological ordering)
- Date range filters
- "On this day in history" features
- Next/previous show navigation

---

#### `year` (INTEGER, NOT NULL, INDEXED)

Year extracted from date.

**Example**: `1977`

**Why Separate Field**: Fast filtering by year without string parsing.

**Indexed**: Yes - year-based browsing (e.g., "all 1977 shows")

**Usage**:
- Browse by decade or specific year
- Year-based statistics
- "Best shows of 1977" queries

---

#### `month` (INTEGER, NOT NULL)

Month extracted from date (1-12).

**Example**: `5` (for May)

**Why Separate Field**: Fast month-based grouping and filtering.

**Not Indexed**: Typically queried with year (`year_month` index)

**Usage**:
- Month-based statistics
- "Shows in May" queries
- Combined with year for "May 1977" queries

---

#### `year_month` (TEXT, NOT NULL, INDEXED)

Year and month combined for efficient monthly grouping.

**Format**: `"YYYY-MM"`

**Example**: `"1977-05"`

**Why Separate Field**: Faster than `WHERE year = 1977 AND month = 5`

**Indexed**: Yes - monthly browsing (e.g., "all May 1977 shows")

**Usage**:
- Browse by month within year
- "All shows in May 1977"
- Monthly statistics and grouping

---

### Show Metadata

#### `band` (TEXT, NOT NULL)

Band name.

**Example**: `"Grateful Dead"`

**Note**: Currently always "Grateful Dead" but field exists for potential future expansion (Jerry Garcia Band, side projects, etc.)

---

#### `url` (TEXT, NULLABLE)

External URL for the show (typically Jerry Garcia website).

**Example**: `"https://jerrygarcia.com/show/1977-05-08/"`

**Nullable**: Some shows don't have URLs

**Usage**: Deep link to external show information

---

### Venue Fields (Denormalized)

These fields are denormalized from what could be a separate `venues` table. See [Design Philosophy](../design-philosophy.md#1-denormalization-venues-in-shows) for rationale.

#### `venue_name` (TEXT, NOT NULL, INDEXED)

Full venue name.

**Example**: `"Barton Hall, Cornell University"`

**Indexed**: Yes - venue-based searches

**Usage**:
- "All shows at Barton Hall"
- Venue-based browsing
- LIKE queries for venue search

---

#### `city` (TEXT, NULLABLE, INDEXED)

City where venue is located.

**Example**: `"Ithaca"`

**Nullable**: Some venues don't have city data

**Indexed**: Yes - city-based browsing

**Usage**:
- "All shows in Ithaca"
- City-based filtering
- Regional browsing

---

#### `state` (TEXT, NULLABLE, INDEXED)

State/province abbreviation.

**Example**: `"NY"`

**Format**: Two-letter US state codes or full names for international

**Nullable**: Some venues outside US don't have state

**Indexed**: Yes - state-based browsing

**Usage**:
- "All shows in New York"
- State-based filtering
- Regional statistics

---

#### `country` (TEXT, NOT NULL, DEFAULT 'USA')

Country code or name.

**Example**: `"USA"`, `"Canada"`, `"England"`

**Default**: `"USA"` (most shows are in US)

**Not Indexed**: Rarely queried (most shows are US)

---

#### `location_raw` (TEXT, NULLABLE)

Original location string from source data.

**Example**: `"Ithaca, NY"`, `"San Francisco, CA"`

**Why Stored**: Preserves original formatting for display

**Usage**:
- Display to user (more natural than "Ithaca NY USA")
- Fallback if city/state parsing failed

---

### Setlist Fields

#### `setlist_status` (TEXT, NULLABLE)

Status of setlist data availability.

**Values**: `"found"`, `"not_found"`, `"partial"`, `null`

**Usage**:
- UI indicator (show icon if setlist available)
- Filter shows with complete setlists

---

#### `setlist_raw` (TEXT, NULLABLE)

Full setlist as JSON string.

**Format**: JSON array of sets with songs

**Example**:
```json
[
  {
    "set": "Set 1",
    "songs": [
      {"name": "Scarlet Begonias", "segue": true},
      {"name": "Fire on the Mountain", "segue": false}
    ]
  }
]
```

**Why JSON**: Variable structure (2 sets, 3 sets, encores). See [Design Philosophy](../design-philosophy.md#2-json-storage).

**Usage**:
- Parsed in app and displayed to user
- Rich setlist UI with segues

---

#### `song_list` (TEXT, NULLABLE)

Comma-separated list of song names extracted from setlist.

**Example**: `"Scarlet Begonias,Fire on the Mountain,Estimated Prophet"`

**Why Separate**: Indexed by FTS4 for song-based searches

**Usage**:
- FTS4 search: "find shows with Scarlet Begonias"
- Quick song presence check

**Not Queried Directly**: Use FTS4 `show_search` table instead

---

### Lineup Fields

#### `lineup_status` (TEXT, NULLABLE)

Status of lineup data availability.

**Values**: `"found"`, `"missing"`, `null`

**Usage**: UI indicator

---

#### `lineup_raw` (TEXT, NULLABLE)

Full lineup as JSON string.

**Format**: JSON array of members with instruments

**Example**:
```json
[
  {"name": "Jerry Garcia", "instruments": "guitar, vocals"},
  {"name": "Bob Weir", "instruments": "guitar, vocals"}
]
```

**Why JSON**: Variable structure (guest musicians, substitutions)

**Usage**: Parsed and displayed to user

---

#### `member_list` (TEXT, NULLABLE)

Comma-separated list of member names extracted from lineup.

**Example**: `"Jerry Garcia,Bob Weir,Phil Lesh"`

**Why Separate**: Indexed by FTS4 for member-based searches

**Usage**: FTS4 search: "find shows with Donna Jean"

---

### Multiple Shows Same Date

#### `show_sequence` (INTEGER, NOT NULL, DEFAULT 1)

Sequence number for multiple shows on same date at same venue.

**Values**: `1`, `2`, `3`, ...

**Example**: If there were two shows at Fillmore on 1969-02-27:
- Morning show: `show_sequence = 1`
- Evening show: `show_sequence = 2`

**Rare**: Most dates have only one show (`show_sequence = 1`)

**Usage**: Distinguish between multiple shows on same date

---

### Recording Fields

#### `recordings_raw` (TEXT, NULLABLE)

JSON array of recording IDs for this show.

**Format**: JSON string array

**Example**: `["gd1977-05-08.sbd.miller.97065.flac16", "gd1977-05-08.aud.unknown.89459.sbeok.flac16"]`

**Why JSON**: Variable number of recordings per show (some have 1, some 20+)

**Usage**:
- Know which recordings exist
- Display count to user
- Not queried (use `recordings` table for detailed queries)

---

#### `recording_count` (INTEGER, NOT NULL, DEFAULT 0)

Total number of recordings available.

**Example**: `8`

**Why Precomputed**: Avoid COUNT query on recordings table

**Usage**:
- Display to user: "8 recordings available"
- Filter shows with recordings vs without

---

#### `best_recording_id` (TEXT, NULLABLE)

Identifier of highest-rated recording for this show.

**Example**: `"gd1977-05-08.sbd.miller.97065.flac16"`

**Why Precomputed**: Default recording to play when user taps show

**Nullable**: Some shows have no recordings

**Usage**:
- Auto-select best recording on show load
- Quick access to recommended recording

---

#### `average_rating` (REAL, NULLABLE)

Average rating across all recordings for this show.

**Example**: `4.8` (out of 5.0)

**Why Precomputed**: Show quality indicator without querying recordings

**Nullable**: Shows with no rated recordings

**Usage**:
- Display star rating to user
- Sort shows by quality
- "Best shows of 1977" queries

---

#### `total_reviews` (INTEGER, NOT NULL, DEFAULT 0)

Total review count across all recordings.

**Example**: `156`

**Why Precomputed**: Avoid SUM query on recordings

**Usage**:
- Display review count to user
- Filter shows with many reviews

---

### Library Status (Denormalized)

These fields are denormalized from `library_shows` table for performance. They **must be kept in sync** with `library_shows` table.

#### `is_in_library` (INTEGER, NOT NULL, DEFAULT 0)

Boolean flag (0/1) indicating if show is in user's library.

**Values**: `0` (false), `1` (true)

**Why Denormalized**: Fast library membership checks without JOIN

**Usage**:
- Display heart icon in show list
- Filter library shows
- Quick membership check

**Sync Required**: When user adds/removes from library, update both:
1. INSERT/DELETE in `library_shows` table
2. UPDATE `is_in_library` flag in `shows` table

---

#### `library_added_at` (INTEGER, NULLABLE)

Timestamp when show was added to library.

**Format**: Unix timestamp (milliseconds)

**Example**: `1678901234000`

**Why Denormalized**: Sort library shows without JOIN

**Nullable**: Null if not in library

**Usage**:
- Sort library by "recently added"
- Display "added on" date

**Sync Required**: Update when user adds to library

---

### Timestamps

#### `created_at` (INTEGER, NOT NULL)

Timestamp when show record was created in database.

**Format**: Unix timestamp (milliseconds)

**Example**: `1678901234000`

**Set Once**: During initial import, never changed

**Usage**: Debugging, data provenance

---

#### `updated_at` (INTEGER, NOT NULL)

Timestamp when show record was last updated.

**Format**: Unix timestamp (milliseconds)

**Example**: `1678901234567`

**Updated**: When any show field changes (rare after initial import)

**Usage**: Debugging, change tracking

---

## Relationships

### Outgoing (Foreign Keys from Other Tables)

#### To RecordingEntity (1:N)

```sql
-- In recordings table
FOREIGN KEY (show_id) REFERENCES shows(show_id) ON DELETE CASCADE
```

**Cardinality**: One show has many recordings (average 6-8, some have 20+)

**Cascade Behavior**: DELETE CASCADE - if show deleted, all recordings deleted

**Why**: Recordings are meaningless without their show

**Query Pattern**:
```sql
SELECT * FROM recordings WHERE show_id = '1977-05-08-...' ORDER BY rating DESC;
```

See [RecordingEntity](recordings.md) for details.

---

#### To LibraryShowEntity (1:0..1)

```sql
-- In library_shows table
FOREIGN KEY (show_id) REFERENCES shows(show_id) ON DELETE CASCADE
```

**Cardinality**: One show has zero or one library entry

**Cascade Behavior**: DELETE CASCADE - if show deleted, library entry deleted

**Why**: Library entry is orphaned if show deleted

**Query Pattern**:
```sql
SELECT EXISTS(SELECT 1 FROM library_shows WHERE show_id = '1977-05-08-...');
```

See [LibraryShowEntity](library-shows.md) for details.

---

#### To ShowSearchEntity (1:1)

**Note**: No formal foreign key (FTS4 tables don't support FKs), but conceptually 1:1 relationship.

**Cardinality**: One show has one FTS4 search entry

**Linked By**: `show_id` field in both tables

**Query Pattern**:
```sql
-- Search returns show IDs
SELECT show_id FROM show_search WHERE show_search MATCH 'Cornell';

-- Then fetch shows
SELECT * FROM shows WHERE show_id IN (...);
```

See [ShowSearchEntity](show-search.md) for details.

---

#### To RecentShowEntity (1:0..1)

**Note**: No foreign key (intentionally, to preserve history if show deleted)

**Cardinality**: One show has zero or one recent history entry

**Linked By**: `show_id` field in both tables

**Query Pattern**:
```sql
SELECT * FROM recent_shows WHERE show_id = '1977-05-08-...';
```

See [RecentShowEntity](recent-shows.md) for details.

---

### Referenced By Collections (N:M via JSON)

Collections reference shows via JSON array in `show_ids_json` field.

**No foreign key**: JSON-based relationship, not enforced by database

**Query Pattern**:
```sql
-- Find collections containing show (inefficient, but rare query)
SELECT * FROM dead_collections WHERE show_ids_json LIKE '%1977-05-08-...%';
```

See [DeadCollectionEntity](collections.md) for details.

---

## Common Queries

### Get Show by ID

```sql
SELECT * FROM shows WHERE show_id = '1977-05-08-barton-hall-cornell-u-ithaca-ny-usa';
```

**Performance**: O(1) via primary key index

---

### Browse by Year

```sql
SELECT * FROM shows WHERE year = 1977 ORDER BY date;
```

**Performance**: Fast via `year` index

---

### Browse by Month

```sql
SELECT * FROM shows WHERE year_month = '1977-05' ORDER BY date;
```

**Performance**: Fast via `year_month` index

---

### Browse by Venue

```sql
SELECT * FROM shows WHERE venue_name LIKE '%Cornell%' ORDER BY date DESC;
```

**Performance**: Decent with `venue_name` index (prefix matches best)

---

### Browse by Location

```sql
-- By city
SELECT * FROM shows WHERE city = 'Ithaca' ORDER BY date DESC;

-- By state
SELECT * FROM shows WHERE state = 'NY' ORDER BY date DESC;
```

**Performance**: Fast via `city`/`state` indexes

---

### Get Top Rated Shows

```sql
SELECT * FROM shows
WHERE average_rating IS NOT NULL
ORDER BY average_rating DESC
LIMIT 20;
```

**Performance**: Table scan (no index on `average_rating`)

**Note**: Could add index if this query is frequent

---

### Chronological Navigation

```sql
-- Next show
SELECT * FROM shows WHERE date > '1977-05-08' ORDER BY date ASC LIMIT 1;

-- Previous show
SELECT * FROM shows WHERE date < '1977-05-08' ORDER BY date DESC LIMIT 1;
```

**Performance**: Fast via `date` index with range scan

---

## Code Locations

### Android

- **Entity**: `androidApp/v2/core/database/src/main/java/com/deadly/v2/core/database/entities/ShowEntity.kt:19`
- **DAO**: `androidApp/v2/core/database/src/main/java/com/deadly/v2/core/database/dao/ShowDao.kt:10`
- **Mappers**: `androidApp/v2/core/database/src/main/java/com/deadly/v2/core/database/mappers/ShowMappers.kt`

### iOS

TBD (to be implemented)

---

## Implementation Notes

### JSON Parsing

**setlist_raw** and **lineup_raw** must be parsed with appropriate error handling:

```kotlin
// Android example
val setlist: Setlist? = try {
    show.setlistRaw?.let { Json.decodeFromString<Setlist>(it) }
} catch (e: Exception) {
    Log.e(TAG, "Failed to parse setlist for ${show.showId}", e)
    null
}
```

**iOS**: Use `JSONDecoder` with similar error handling

---

### Library Sync

When updating library status, update both tables:

```kotlin
// Add to library
transaction {
    libraryDao.addToLibrary(LibraryShowEntity(showId = showId, ...))
    showDao.updateLibraryStatus(showId, isInLibrary = true, addedAt = now)
}

// Remove from library
transaction {
    libraryDao.removeFromLibraryById(showId)
    showDao.updateLibraryStatus(showId, isInLibrary = false, addedAt = null)
}
```

---

## See Also

- [Design Philosophy](../design-philosophy.md) - Why denormalized, why JSON
- [RecordingEntity](recordings.md) - Related recordings
- [ShowSearchEntity](show-search.md) - FTS4 search
- [LibraryShowEntity](library-shows.md) - User library
- [Data Sources](../data-sources.md) - Where show data comes from
