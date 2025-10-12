# Data Flow

This document explains how data flows through the system from initial import through runtime usage. Every flow described here is extracted from the actual Android implementation.

---

## Overview

Data flows through the app in three primary scenarios:

1. **Initial Setup** - One-time metadata import from dead-metadata package
2. **Runtime Access** - Show browsing, playback, search
3. **User Actions** - Library management, play tracking

---

## 1. Initial Setup Flow

### 1.1 High-Level Import Flow

```
User triggers import
    ↓
Download metadata ZIP from GitHub
    ↓
Extract ZIP to temporary directory
    ↓
Parse JSON files (shows, recordings)
    ↓
Clear existing database
    ↓
Import shows → database
    ↓
Build FTS4 search index
    ↓
Import recordings → database
    ↓
Import collections
    ↓
Save data version metadata
    ↓
Complete ✅
```

**Total Time**: 5-15 seconds (2,400 shows + 16,000 recordings)

**Code Location**: `DataImportService.kt:114-357`

---

### 1.2 Detailed Import Steps

#### Step 1: Download Metadata Package

**Source**: GitHub releases - `deadly-metadata` repository

**Service**: `DownloadService.kt`

**URL Format**: `https://github.com/{org}/deadly-metadata/releases/download/{version}/data.zip`

**Size**: ~10-15 MB compressed

**Output**: Temporary ZIP file in cache directory

---

#### Step 2: Extract ZIP

**Service**: `ZipExtractionService.kt`

**Structure**:
```
extracted/
  shows/
    1965-11-03-longshoremens-hall-san-francisco-ca-usa.json
    1965-12-10-fillmore-auditorium-san-francisco-ca-usa.json
    ...
    1995-07-09-soldier-field-chicago-il-usa.json
  recordings/
    gd1965-11-03.aud.pcrp.107177.flac16.json
    ...
  collections.json
  manifest.json
```

**File Count**: 2,400 show files + 16,000 recording files + 2 metadata files

---

#### Step 3: Clear Existing Data

**Code**: `DataImportService.kt:133-137`

```kotlin
showSearchDao.clearAllSearchData()    // Clear FTS4 index first
recordingDao.deleteAllRecordings()    // Clear child table
showDao.deleteAll()                   // Clear parent table
dataVersionDao.deleteAll()            // Clear version tracker
```

**Why This Order**: Child tables (recordings, search) before parent (shows) to avoid FK violations

**Transaction**: All-or-nothing (rollback on failure)

---

#### Step 4: Parse Show Files

**Code**: `DataImportService.kt:142-172`

**Process**:
```kotlin
// Read all show JSON files
showFiles.forEach { file ->
    val showJson = file.readText()
    val showData = Json.decodeFromString<ShowImportData>(showJson)
    showsMap[showData.showId] = showData
}
```

**Example JSON** (`1977-05-08-barton-hall...json`):
```json
{
  "show_id": "1977-05-08-barton-hall-cornell-u-ithaca-ny-usa",
  "band": "Grateful Dead",
  "venue": "Barton Hall",
  "location_raw": "Ithaca, NY",
  "city": "Ithaca",
  "state": "NY",
  "country": "USA",
  "date": "1977-05-08",
  "setlist": [
    {
      "set": "Set 1",
      "songs": [
        {"name": "New Minglewood Blues"},
        {"name": "Loser"}
      ]
    }
  ],
  "lineup": [
    {"name": "Jerry Garcia", "instruments": "guitar, vocals"},
    {"name": "Bob Weir", "instruments": "guitar, vocals"}
  ],
  "recordings": [
    "gd1977-05-08.sbd.miller.97065.flac16",
    "gd1977-05-08.aud.vernon.82548.sbeok.flac16"
  ],
  "best_recording": "gd1977-05-08.sbd.miller.97065.flac16",
  "avg_rating": 4.9,
  "recording_count": 8
}
```

**Progress**: `ImportProgress("READING_SHOWS", index, total, "Processing show data...")`

---

#### Step 5: Parse Recording Files

**Code**: `DataImportService.kt:174-205`

**Process**:
```kotlin
recordingFiles.forEach { file ->
    val recordingJson = file.readText()
    val recordingData = Json.decodeFromString<RecordingImportData>(recordingJson)
    val recordingId = file.nameWithoutExtension
    recordingsMap[recordingId] = recordingData
}
```

**Example JSON** (`gd1977-05-08.sbd.miller.97065.flac16.json`):
```json
{
  "rating": 4.9,
  "review_count": 156,
  "source_type": "SBD",
  "confidence": 0.95,
  "raw_rating": 4.9,
  "high_ratings": 142,
  "low_ratings": 3,
  "taper": "Miller",
  "source": "SBD > Reel > DAT > CD > EAC > FLAC",
  "lineage": "Digital transfer by Miller using Sonic Solutions"
}
```

**Note**: Track lists NOT in import JSON (fetched on-demand from Archive.org)

---

#### Step 6: Import Shows to Database

**Code**: `DataImportService.kt:213-242`

```kotlin
showsMap.values.forEach { showData ->
    // Create ShowEntity
    val showEntity = createShowEntity(showData, recordingsMap)
    showDao.insert(showEntity)

    // Create FTS4 search entry
    val searchEntity = createSearchEntity(showData)
    showSearchDao.insertOrUpdate(searchEntity)
}
```

**Key Transformation** (`createShowEntity` - line 360):
```kotlin
ShowEntity(
    showId = showData.showId,
    date = showData.date,
    year = extractYear(showData.date),    // "1977"
    month = extractMonth(showData.date),  // 5
    yearMonth = extractYearMonth(showData.date),  // "1977-05"
    venueName = showData.venue,
    city = showData.city,
    state = showData.state,
    setlistRaw = Json.encodeToString(showData.setlist),  // JSON blob
    songList = extractSongList(showData.setlist),  // "Scarlet,Fire,..."
    lineupRaw = Json.encodeToString(showData.lineup),  // JSON blob
    memberList = extractMemberList(showData.lineup),  // "Jerry,Bob,Phil"
    recordingCount = showData.recordingCount,
    averageRating = showData.avgRating,
    bestRecordingId = showData.bestRecording,
    // ... denormalized fields for performance
)
```

**Progress**: `ImportProgress("IMPORTING_SHOWS", index, total, "Creating show entries...")`

---

#### Step 7: Build Search Index

**Code**: `DataImportService.kt:233-234` (inline with show import)

**Process** (`createSearchEntity` - line 460):
```kotlin
fun createSearchEntity(showData: ShowImportData): ShowSearchEntity {
    val searchText = buildList {
        // Date variations
        add(showData.date)                // "1977-05-08"
        add("5-8-77")                     // Short date
        add("5/8/77")                     // Slash date
        add("5.8.77")                     // Dot date
        add("1977")                       // Year
        add("77")                         // Short year
        add("197")                        // Decade

        // Location
        add(showData.venue)               // "Barton Hall"
        add(showData.city)                // "Ithaca"
        add(showData.state)               // "NY"
        add(showData.locationRaw)         // "Ithaca, NY"

        // Members
        add(extractMemberList(showData.lineup))  // "Jerry Garcia Bob Weir..."

        // Songs
        add(extractSongList(showData.setlist))  // "Scarlet Begonias Fire..."
    }.joinToString(" ")

    return ShowSearchEntity(showId = showData.showId, searchText = searchText)
}
```

**FTS4 Config**: `unicode61` tokenizer with `tokenchars=-.` (preserves dashes/dots in dates)

**Why So Many Date Formats**: Users search many ways - "5-8-77", "May 8 1977", "Cornell 77"

---

#### Step 8: Import Recordings to Database

**Code**: `DataImportService.kt:246-289`

```kotlin
recordingsMap.forEach { (recordingId, recordingData) ->
    // Find which show(s) reference this recording
    val referencingShows = showsMap.values.filter { show ->
        show.recordings.contains(recordingId)
    }

    if (referencingShows.isNotEmpty()) {
        // Recording referenced by show - import it
        referencingShows.forEach { show ->
            val recordingEntity = createRecordingEntity(
                recordingId,
                recordingData,
                show.showId
            )
            recordingDao.insertRecording(recordingEntity)
        }
    } else {
        // Recording not referenced - skip (orphaned data)
        Log.w(TAG, "Recording $recordingId not referenced by any show")
    }
}
```

**Key Point**: Only import recordings referenced by shows (data validation)

**Progress**: `ImportProgress("IMPORTING_RECORDINGS", index, total, "Creating recording entries...")`

---

#### Step 9: Import Collections

**Code**: `DataImportService.kt:296-313`

**Service**: `CollectionsImportService.kt`

**Source File**: `collections.json`

**Example**:
```json
{
  "collections": [
    {
      "id": "dicks-picks",
      "name": "Dick's Picks",
      "description": "Official release series...",
      "tags": ["official-release", "soundboard"],
      "shows": [
        {"identifier": "gd1977-05-08...", "volume": 15}
      ]
    }
  ]
}
```

**Transform**:
```kotlin
DeadCollectionEntity(
    id = "dicks-picks",
    name = "Dick's Picks",
    description = "Official release series...",
    tagsJson = Json.encodeToString(["official-release", "soundboard"]),
    showIdsJson = Json.encodeToString(["gd1977-05-08-..."]),
    totalShows = 36,
    primaryTag = "official-release"
)
```

---

#### Step 10: Save Data Version

**Code**: `DataImportService.kt:318-335`

```kotlin
dataVersionDao.insertOrUpdate(
    DataVersionEntity(
        id = 1,  // Singleton
        dataVersion = "2.0.0",
        packageName = "Deadly Metadata",
        versionType = "release",
        description = "V2 database import from extracted files",
        importedAt = System.currentTimeMillis(),
        totalShows = importedShows,
        totalFiles = importedRecordings
    )
)
```

**Purpose**: Track which version is installed, detect updates

---

### 1.3 Import Error Handling

**Parse Failures**: Logged but don't stop import
```kotlin
try {
    val showData = Json.decodeFromString<ShowImportData>(showJson)
    showsMap[showData.showId] = showData
} catch (e: Exception) {
    Log.e(TAG, "Failed to parse show file: ${file.name}", e)
    // Continue with next file
}
```

**Database Failures**: Stop import, rollback transaction
```kotlin
try {
    // All database operations in transaction
    database.withTransaction {
        // ... import operations
    }
} catch (e: Exception) {
    Log.e(TAG, "Data import failed", e)
    return ImportResult(success = false, message = e.message)
}
```

**Orphaned Data**: Recordings not referenced by shows are skipped (data integrity)

---

## 2. Runtime Access Flow

### 2.1 Show Detail Page Flow

**User Action**: User taps show in browse list

**Sequence**:

```
User taps show card
    ↓
[1] Load show from database (fast - single query)
    ↓
[2] Display show metadata (venue, date, setlist, lineup)
    ↓
[3] Load recordings from database (fast - indexed query)
    ↓
[4] Display recordings list
    ↓
[5] User selects recording (or auto-select best)
    ↓
[6] Check filesystem cache for tracks
    ↓
[7a] Cache HIT: Read tracks.json (10ms)
      OR
[7b] Cache MISS: Fetch from Archive.org API (500-2000ms)
    ↓
[8] Cache response to filesystem
    ↓
[9] Display track list
    ↓
User starts playback
```

---

#### Step 1: Load Show from Database

**Code**: ViewModel calls ShowRepository

```kotlin
val show = showDao.getShowById(showId)
```

**Query**:
```sql
SELECT * FROM shows WHERE showId = :showId
```

**Performance**: O(1) via PRIMARY KEY

**Data Returned**: Full show with denormalized venue, JSON setlist/lineup, precomputed stats

---

#### Step 2: Display Show Metadata

**Setlist Deserialization**:
```kotlin
val setlist = Json.decodeFromString<List<Set>>(show.setlistRaw)

// UI renders:
Set 1
├─ New Minglewood Blues
├─ Loser
├─ El Paso
└─ ...
```

**Lineup Deserialization**:
```kotlin
val lineup = Json.decodeFromString<List<LineupMember>>(show.lineupRaw)

// UI renders:
Jerry Garcia - guitar, vocals
Bob Weir - guitar, vocals
Phil Lesh - bass, vocals
```

**Why JSON**: Flexible structure (encores, guests), atomic display, no JOINs

---

#### Step 3: Load Recordings from Database

```kotlin
val recordings = recordingDao.getRecordingsForShow(showId)
```

**Query**:
```sql
SELECT * FROM recordings
WHERE show_id = :showId
ORDER BY rating DESC
```

**Performance**: O(log N + k) via `idx_recordings_show_id_rating` where k = 6-8 recordings

**Data Returned**: List of RecordingEntity sorted by quality

---

#### Step 4: Display Recordings List

**UI Rendering**:
```
Recordings (8 available)

★★★★★ (4.9) gd1977-05-08.sbd.miller.97065 [SBD]
             Miller > Soundboard > 156 reviews

★★★★☆ (4.2) gd1977-05-08.aud.vernon.82548 [AUD]
             Vernon > Audience > 42 reviews

...
```

**Auto-Select**: Best recording (first in list) selected by default

---

#### Step 5: Load Tracks (Cache Flow)

**Service**: `ArchiveServiceImpl.kt:83-117`

**Cache Location**: `<cacheDir>/archive/{recordingId}.tracks.json`

**Example**: `gd1977-05-08.sbd.miller.97065.flac16.tracks.json`

**Flow**:
```kotlin
suspend fun getRecordingTracks(recordingId: String): Result<List<Track>> {
    val cacheFile = File(cacheDir, "$recordingId.tracks.json")

    // Check cache first
    if (cacheFile.exists() && !isCacheExpired(cacheFile.lastModified())) {
        // CACHE HIT (common case)
        Log.d(TAG, "Cache hit for tracks: $recordingId")
        val cached = json.decodeFromString<List<Track>>(cacheFile.readText())
        return Result.success(cached)
    }

    // CACHE MISS - fetch from API
    Log.d(TAG, "Cache miss, fetching from API: $recordingId")
    val response = archiveApiService.getRecordingMetadata(recordingId)

    if (response.isSuccessful) {
        val tracks = archiveMapper.mapToTracks(response.body()!!)

        // Cache for future
        cacheFile.writeText(json.encodeToString(tracks))
        Log.d(TAG, "Cached ${tracks.size} tracks")

        return Result.success(tracks)
    }

    return Result.failure(Exception("API error: ${response.code()}"))
}
```

**Cache Expiry**: 24 hours (86,400,000 milliseconds)

```kotlin
private fun isCacheExpired(timestamp: Long): Boolean {
    val expiryTime = timestamp + (24 * 60 * 60 * 1000L)
    return System.currentTimeMillis() > expiryTime
}
```

---

#### Cache Performance Comparison

| Scenario | Time | Source |
|----------|------|--------|
| **Cache hit** | 10-20ms | Read local JSON file |
| **Cache miss (first load)** | 500-2000ms | Archive.org API + parse |
| **Cache hit (24h later)** | 10-20ms | Still cached |
| **Cache expired (24h+)** | 500-2000ms | Re-fetch from API |

**Why 24h TTL**: Balance freshness (reviews/ratings update) vs performance

---

#### Archive.org API Call

**Endpoint**: `https://archive.org/metadata/{recordingId}`

**Response** (simplified):
```json
{
  "metadata": {
    "identifier": "gd1977-05-08.sbd.miller.97065.flac16",
    "title": "Grateful Dead Live at Barton Hall...",
    "date": "1977-05-08"
  },
  "files": [
    {
      "name": "gd77-05-08d1t01.flac",
      "title": "New Minglewood Blues",
      "length": "354.23",
      "format": "Flac"
    },
    {
      "name": "gd77-05-08d1t02.flac",
      "title": "Loser",
      "length": "412.87",
      "format": "Flac"
    }
  ],
  "reviews": [
    {
      "reviewer": "deadhead77",
      "stars": "5",
      "reviewtitle": "Holy Grail",
      "reviewbody": "This is THE show..."
    }
  ]
}
```

**Mapping**: `ArchiveMapper.kt` converts API response → app models

---

### 2.2 Search Flow

**User Action**: User types "Cornell 1977" in search box

**Sequence**:

```
User types query
    ↓
Debounce 300ms (wait for user to finish typing)
    ↓
FTS4 search: MATCH query
    ↓
Returns show IDs ordered by relevance (BM25)
    ↓
Fetch full show data for IDs
    ↓
Display results
```

**Code**:
```kotlin
val searchQuery = MutableStateFlow("")

val searchResults = searchQuery
    .debounce(300)  // Wait for typing to stop
    .flatMapLatest { query ->
        if (query.length < 2) {
            flowOf(emptyList())
        } else {
            // FTS4 search
            val showIds = showSearchDao.searchShows(query)

            // Fetch full data
            val shows = showDao.getShowsByIds(showIds)

            flowOf(shows)
        }
    }
    .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
```

**FTS4 Query**:
```sql
SELECT showId FROM show_search WHERE show_search MATCH 'Cornell 1977'
```

**Returns**: `["1977-05-08-barton-hall-cornell-u-ithaca-ny-usa"]`

**BM25 Ranking**: "Cornell 1977" matches venue + year → high relevance score

---

### 2.3 Browse Flow

**User Action**: User filters by year "1977"

**Sequence**:

```
User taps "1977" filter
    ↓
Query: WHERE year = 1977 ORDER BY date
    ↓
Returns ~100 shows from 1977
    ↓
Display in RecyclerView with pagination
```

**Query**:
```sql
SELECT * FROM shows WHERE year = 1977 ORDER BY date
```

**Performance**: O(log N + k) via `idx_shows_year`

**UI**: LazyColumn/RecyclerView with virtual scrolling (only render visible items)

---

### 2.4 Navigation Flow (Next/Previous Show)

**User Action**: User taps "Next Show" button during playback

**Sequence**:

```
Current show: 1977-05-08
    ↓
Query: Next show after 1977-05-08
    ↓
Returns: 1977-05-09 (Buffalo)
    ↓
Load show detail for 1977-05-09
    ↓
Auto-select best recording
    ↓
Start playback
```

**Query**:
```sql
SELECT * FROM shows
WHERE date > '1977-05-08'
ORDER BY date ASC
LIMIT 1
```

**Performance**: O(log N) via `idx_shows_date`

**Result**: Next chronological show

---

## 3. User Action Flows

### 3.1 Add to Library Flow

**User Action**: User taps heart icon on show card

**Sequence**:

```
User taps heart icon (empty)
    ↓
[Transaction Start]
    ↓
Insert into library_shows table
    ↓
Update shows.is_in_library = true
    ↓
[Transaction Commit]
    ↓
UI updates (heart fills)
```

**Code**:
```kotlin
suspend fun addToLibrary(showId: String) {
    database.withTransaction {
        // Insert library entry
        libraryDao.addToLibrary(
            LibraryShowEntity(
                showId = showId,
                addedToLibraryAt = System.currentTimeMillis(),
                isPinned = false,
                libraryNotes = null
            )
        )

        // Sync denormalized flag in shows table
        showDao.updateLibraryStatus(
            showId = showId,
            isInLibrary = true,
            addedAt = System.currentTimeMillis()
        )
    }
}
```

**Why Transaction**: Ensures both tables updated atomically (no inconsistent state)

**Denormalization**: `shows.is_in_library` mirrored for fast membership checks without JOIN

---

### 3.2 Remove from Library Flow

**User Action**: User taps filled heart icon

**Sequence**:

```
User taps heart icon (filled)
    ↓
[Transaction Start]
    ↓
Delete from library_shows table
    ↓
Update shows.is_in_library = false
    ↓
[Transaction Commit]
    ↓
UI updates (heart empties)
```

**Code**:
```kotlin
suspend fun removeFromLibrary(showId: String) {
    database.withTransaction {
        // Remove library entry
        libraryDao.removeFromLibraryById(showId)

        // Sync denormalized flag
        showDao.updateLibraryStatus(
            showId = showId,
            isInLibrary = false,
            addedAt = null
        )
    }
}
```

---

### 3.3 Toggle Pin Flow

**User Action**: User taps pin icon in library

**Sequence**:

```
User taps pin icon
    ↓
Read current pin status
    ↓
UPDATE library_shows SET isPinned = !current
    ↓
Library re-sorts (pinned first)
```

**Code**:
```kotlin
suspend fun togglePin(showId: String) {
    val entity = libraryDao.getLibraryShowById(showId) ?: return
    libraryDao.updatePinStatus(showId, !entity.isPinned)
}
```

**Query**:
```sql
UPDATE library_shows SET isPinned = :isPinned WHERE showId = :showId
```

**UI Update**: Reactive Flow observes `library_shows`, UI re-renders with new sort order

---

### 3.4 Record Play Flow (UPSERT Pattern)

**User Action**: User plays a track, passes "meaningful play" threshold (30+ seconds)

**Sequence**:

```
Track plays for 30+ seconds
    ↓
Check: Does recent_shows entry exist for this show?
    ↓
YES: UPDATE timestamp and increment play count
NO:  INSERT new entry with count = 1
    ↓
"Recently played" section updates
```

**Code** (UPSERT pattern):
```kotlin
suspend fun recordPlay(showId: String) {
    val now = System.currentTimeMillis()
    val existing = recentShowDao.getShowById(showId)

    if (existing != null) {
        // UPDATE existing entry
        recentShowDao.updateShow(
            showId = showId,
            timestamp = now,
            playCount = existing.totalPlayCount + 1
        )
    } else {
        // INSERT new entry
        recentShowDao.insert(
            RecentShowEntity(
                showId = showId,
                lastPlayedTimestamp = now,
                firstPlayedTimestamp = now,
                totalPlayCount = 1
            )
        )
    }
}
```

**Why UPSERT**: One row per show, simple queries, no GROUP BY needed

**Meaningful Play Threshold**:
```kotlin
fun onTrackEnded(playedDuration: Long, trackDuration: Long) {
    val isMeaningful = playedDuration >= 30_000 ||  // 30+ seconds
                       (playedDuration.toDouble() / trackDuration >= 0.5)  // 50%+

    if (isMeaningful) {
        recordPlay(currentShowId)
    }
}
```

---

## 4. Cache Management Flow

### 4.1 Cache Structure

```
<app_cache_dir>/
  archive/
    gd1977-05-08.sbd.miller.97065.flac16.metadata.json
    gd1977-05-08.sbd.miller.97065.flac16.tracks.json
    gd1977-05-08.sbd.miller.97065.flac16.reviews.json
    gd1977-05-08.aud.vernon.82548.sbeok.flac16.tracks.json
    ...
```

**File Naming**: `{recordingId}.{type}.json`

**Types**: `metadata`, `tracks`, `reviews`

---

### 4.2 Cache Cleanup

**Automatic**: OS clears cache when storage low (standard Android behavior)

**Manual**:
```kotlin
suspend fun clearCache(recordingId: String) {
    File(cacheDir, "$recordingId.metadata.json").delete()
    File(cacheDir, "$recordingId.tracks.json").delete()
    File(cacheDir, "$recordingId.reviews.json").delete()
}

suspend fun clearAllCache() {
    cacheDir.listFiles()?.forEach { it.delete() }
}
```

**Code Location**: `ArchiveServiceImpl.kt:155-197`

---

### 4.3 Cache Invalidation

**Time-Based** (24h TTL):
```kotlin
if (cacheFile.exists() && !isCacheExpired(cacheFile.lastModified())) {
    // Use cache
} else {
    // Re-fetch
}
```

**Manual** (user action):
- "Refresh" button on show detail
- "Clear cache" in settings

---

## 5. Data Consistency Patterns

### 5.1 Transaction Safety

**Library Operations** (two-table update):
```kotlin
database.withTransaction {
    libraryDao.addToLibrary(entity)
    showDao.updateLibraryStatus(showId, true, timestamp)
}
```

**Guarantees**: Both updates succeed or both rollback (no inconsistent state)

---

### 5.2 Denormalization Sync

**Pattern**: Update denormalized field atomically with source table

**Example**: Library membership
```kotlin
// Source of truth: library_shows table (presence/absence)
// Denormalized: shows.is_in_library flag (for fast checks)

// Always update both in transaction
database.withTransaction {
    libraryDao.addToLibrary(entity)  // Source
    showDao.updateLibraryStatus(showId, true, timestamp)  // Mirror
}
```

**Why**: Fast membership checks without JOIN (`SELECT is_in_library FROM shows WHERE ...`)

---

### 5.3 Foreign Key Cascade

**Recordings → Shows**:
```sql
FOREIGN KEY (show_id) REFERENCES shows(show_id) ON DELETE CASCADE
```

**Effect**: Deleting show auto-deletes all recordings (orphan prevention)

**Library → Shows**:
```sql
FOREIGN KEY (show_id) REFERENCES shows(show_id) ON DELETE CASCADE
```

**Effect**: Deleting show auto-deletes library entry (orphan prevention)

---

## 6. Reactive Data Flow

### 6.1 Flow-Based Reactivity

**DAO Returns Flow**:
```kotlin
@Query("SELECT * FROM library_shows ORDER BY isPinned DESC, addedToLibraryAt DESC")
fun getAllLibraryShowsFlow(): Flow<List<LibraryShowEntity>>
```

**ViewModel Observes**:
```kotlin
val libraryShows: StateFlow<List<Show>> =
    libraryDao.getAllLibraryShowsFlow()
        .map { libraryEntities ->
            val showIds = libraryEntities.map { it.showId }
            showDao.getShowsByIds(showIds)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
```

**UI Observes StateFlow**:
```kotlin
val libraryShows by viewModel.libraryShows.collectAsState()

LazyColumn {
    items(libraryShows) { show ->
        ShowCard(show)
    }
}
```

**Auto-Update**: Any change to `library_shows` table triggers Flow emission → UI re-renders

---

### 6.2 Reactive Update Example

**Sequence**:
```
User adds show to library (Cornell '77)
    ↓
Transaction: INSERT into library_shows + UPDATE shows
    ↓
Room detects library_shows table change
    ↓
Flow emits new list
    ↓
ViewModel maps show IDs → full Show entities
    ↓
StateFlow updates
    ↓
UI re-composes with new list
    ↓
Cornell '77 appears in library UI
```

**No Manual Refresh**: Flow-based architecture handles updates automatically

---

## 7. Performance Optimizations

### 7.1 Background Processing

**Import**:
```kotlin
withContext(Dispatchers.IO) {
    // All file I/O and database operations on background thread
    showDao.insertAll(shows)
}
```

**Cache Fetching**:
```kotlin
suspend fun getRecordingTracks(recordingId: String): Result<List<Track>> {
    // Suspend function - automatically runs on background thread
    return withContext(Dispatchers.IO) {
        // File I/O + network call
    }
}
```

---

### 7.2 Batch Operations

**Bulk Insert**:
```kotlin
@Insert
suspend fun insertAll(shows: List<ShowEntity>)

// Usage:
showDao.insertAll(shows)  // One transaction for all 2,400 shows
```

**Why**: Single transaction is ~100x faster than 2,400 individual INSERTs

---

### 7.3 Pagination

**RecyclerView/LazyColumn**: Only render visible items

**Paging 3 (future)**:
```kotlin
@Query("SELECT * FROM shows ORDER BY date DESC")
fun getAllShowsPaged(): PagingSource<Int, ShowEntity>
```

**Benefit**: Load 20 shows at a time (not all 2,400)

---

## 8. Error Handling

### 8.1 Network Errors (Cache Fetch)

```kotlin
try {
    val response = archiveApiService.getRecordingMetadata(recordingId)
    if (response.isSuccessful) {
        // Cache and return
    } else {
        Result.failure(Exception("API error: ${response.code()}"))
    }
} catch (e: Exception) {
    Log.e(TAG, "Network error", e)
    Result.failure(e)
}
```

**UI Handling**: Show error message, retry button

---

### 8.2 Parse Errors (Import)

```kotlin
try {
    val showData = Json.decodeFromString<ShowImportData>(showJson)
    showsMap[showData.showId] = showData
} catch (e: Exception) {
    Log.e(TAG, "Failed to parse: ${file.name}", e)
    // Continue with next file (don't fail entire import)
}
```

**Graceful Degradation**: Skip malformed files, import others

---

### 8.3 Database Errors (Import)

```kotlin
try {
    database.withTransaction {
        // All import operations
    }
    ImportResult(success = true, ...)
} catch (e: Exception) {
    Log.e(TAG, "Import failed", e)
    ImportResult(success = false, message = e.message)
    // Transaction rolled back automatically
}
```

**Atomic Import**: All or nothing (no partial imports)

---

## 9. Summary Diagrams

### Database → UI Data Flow

```
┌─────────────────────────────────────────────┐
│           DATABASE (SQLite)                  │
│                                             │
│  ┌─────────┐  ┌────────────┐  ┌──────────┐ │
│  │ shows   │  │ recordings │  │ library  │ │
│  └─────────┘  └────────────┘  └──────────┘ │
└──────────────┬──────────────────────────────┘
               │
               │ Flow<List<Entity>>
               ↓
        ┌─────────────┐
        │ ViewModel   │
        │  (map to    │
        │  UI models) │
        └──────┬──────┘
               │
               │ StateFlow<List<UIModel>>
               ↓
         ┌───────────┐
         │ UI Layer  │
         │ (Compose/ │
         │  Views)   │
         └───────────┘
```

---

### Cache → UI Data Flow

```
User requests tracks
         │
         ↓
  ┌──────────────┐
  │ Check cache  │
  └──────┬───────┘
         │
    ┌────┴────┐
    │         │
   HIT       MISS
    │         │
    │         ↓
    │   ┌──────────────┐
    │   │Archive.org   │
    │   │     API      │
    │   └──────┬───────┘
    │          │
    │          ↓
    │   ┌──────────────┐
    │   │ Write cache  │
    │   └──────┬───────┘
    │          │
    └────┬─────┘
         │
         ↓
   ┌──────────────┐
   │  Read cache  │
   └──────┬───────┘
          │
          ↓
     ┌────────┐
     │   UI   │
     └────────┘
```

---

### UPSERT Flow (Recent Plays)

```
Track plays 30+ seconds
         │
         ↓
  ┌──────────────────┐
  │ Query: showId in │
  │  recent_shows?   │
  └────────┬─────────┘
           │
      ┌────┴────┐
      │         │
    EXISTS   NOT EXISTS
      │         │
      ↓         ↓
 ┌─────────┐ ┌────────┐
 │ UPDATE  │ │ INSERT │
 │ +1 play │ │ count=1│
 │ new time│ │        │
 └────┬────┘ └───┬────┘
      │          │
      └────┬─────┘
           │
           ↓
    ┌────────────┐
    │ Flow emits │
    │ UI updates │
    └────────────┘
```

---

## See Also

- [Query Patterns](query-patterns.md) - SQL queries used in these flows
- [Design Philosophy](design-philosophy.md) - Why these patterns
- [Data Sources](data-sources.md) - Metadata package and API details
