# Data Sources

The application uses two distinct data sources with different lifecycles, access patterns, and storage mechanisms. Understanding where data comes from and how it flows into the system is essential for implementing the database layer correctly.

---

## Overview

| Source | What | Storage | Frequency | Size |
|--------|------|---------|-----------|------|
| **Dead Metadata Package** | Show catalog, recordings metadata, collections | SQLite Database | One-time (initial setup) | ~500KB compressed, ~10-15MB in DB |
| **Archive.org APIs** | Track lists, reviews, detailed metadata | Filesystem Cache (JSON) | On-demand per show (24h TTL) | Variable (~5-20KB per recording) |

---

## Source 1: Dead Metadata Package

### What It Is

A curated dataset of Grateful Dead shows and recordings maintained in the Deadly monorepo ([deadly-monorepo](https://github.com/ds17f/deadly-monorepo)). This is the **queryable catalog** that powers browsing, searching, and show discovery.

**Compilation of this dataset is out of scope** for this application - it's maintained separately and released as a downloadable package.

### Data Format

A ZIP archive with the following structure:

```
deadly-metadata/
├── manifest.json              # Version metadata
├── shows/                     # ~2,500 show JSON files
│   ├── 1965-11-03-longshoremens-hall-san-francisco-ca-usa.json
│   ├── 1977-05-08-barton-hall-cornell-u-ithaca-ny-usa.json
│   └── ...
├── recordings/                # ~15,000-20,000 recording JSON files
│   ├── gd1965-11-03.aud.pcrp.107177.flac16.json
│   ├── gd1977-05-08.sbd.miller.97065.flac16.json
│   └── ...
└── collections.json           # Curated collections
```

### Show JSON Schema

Each show file contains comprehensive metadata:

```json
{
  "show_id": "1977-05-08-barton-hall-cornell-u-ithaca-ny-usa",
  "band": "Grateful Dead",
  "date": "1977-05-08",
  "venue": "Barton Hall, Cornell University",
  "location_raw": "Ithaca, NY",
  "city": "Ithaca",
  "state": "NY",
  "country": "USA",
  "url": "https://jerrygarcia.com/show/...",

  "setlist_status": "found",
  "setlist": [
    {
      "set": "Set 1",
      "songs": [
        {"name": "Scarlet Begonias", "segue": true},
        {"name": "Fire on the Mountain", "segue": false}
      ]
    }
  ],

  "lineup_status": "found",
  "lineup": [
    {"name": "Jerry Garcia", "instruments": "guitar, vocals"},
    {"name": "Bob Weir", "instruments": "guitar, vocals"}
  ],

  "recordings": ["gd1977-05-08.sbd.miller.97065.flac16"],
  "best_recording": "gd1977-05-08.sbd.miller.97065.flac16",
  "recording_count": 8,
  "avg_rating": 4.8,

  "collections": ["dicks-picks", "cornell-77"]
}
```

### Recording JSON Schema

Each recording file contains quality metrics and provenance:

```json
{
  "identifier": "gd1977-05-08.sbd.miller.97065.flac16",
  "date": "1977-05-08",
  "venue": "Barton Hall, Cornell University",
  "location": "Ithaca, NY",

  "source_type": "SBD",
  "rating": 4.8,
  "raw_rating": 4.9,
  "review_count": 156,
  "confidence": 0.95,
  "high_ratings": 142,
  "low_ratings": 3,

  "taper": "Betty Cantor-Jackson",
  "source": "SBD > Reel > DAT > ...",
  "lineage": "Digital transfer by Miller...",

  "tracks": [
    {
      "track": "01",
      "title": "New Minglewood Blues",
      "duration": 321.5
    }
  ]
}
```

### Collections JSON Schema

Curated collections of shows:

```json
{
  "collections": [
    {
      "id": "dicks-picks",
      "name": "Dick's Picks",
      "description": "Official Dick's Picks series...",
      "tags": ["official-release", "soundboard"],
      "shows": [
        "1973-12-19-curtis-hixon-convention-center-tampa-fl-usa",
        "1977-05-08-barton-hall-cornell-u-ithaca-ny-usa"
      ]
    }
  ]
}
```

### Import Process Flow

```
User initiates first-time setup
  ↓
1. Download ZIP from dead-metadata GitHub release
   - URL: https://github.com/ds17f/dead-metadata/releases/latest
   - File: deadly-metadata-v2.0.0.zip (~500KB)
  ↓
2. Save to app cache directory
   - Android: context.cacheDir
   - iOS: FileManager.default.temporaryDirectory
  ↓
3. Extract ZIP to cache/deadly-metadata/
   - Using ZipExtractionService (Android)
   - Using ZipArchive or similar (iOS)
  ↓
4. Parse shows/ directory (JSON files)
   - Read each file as text
   - Parse JSON with kotlinx.serialization (Android) or Codable (iOS)
   - Handle parse errors gracefully (log, skip file, continue)
  ↓
5. Parse recordings/ directory (JSON files)
   - Same parse logic as shows
   - Link recordings to shows via show_id
  ↓
6. Parse collections.json
   - Single JSON file with array of collections
  ↓
7. Clear existing database
   - DELETE FROM all tables (clean slate)
   - Ensures idempotent import (can re-run if failed)
  ↓
8. Insert shows (batched)
   - Create ShowEntity from parsed data
   - Map JSON fields to entity columns
   - Extract songList/memberList for FTS
   - Batch insert (500-1000 at a time for performance)
  ↓
9. Build FTS4 index
   - For each show, create ShowSearchEntity
   - Generate searchText with date variations
   - Insert into show_search FTS table
  ↓
10. Insert recordings (batched)
    - Create RecordingEntity from parsed data
    - Only insert recordings referenced by shows
    - Link via show_id foreign key
  ↓
11. Insert collections
    - Create DeadCollectionEntity from parsed data
    - Store showIds as JSON array string
  ↓
12. Insert data version
    - Single row in data_version_v2 table
    - Records version, import time, statistics
  ↓
13. Cleanup
    - Delete extracted files (cache/deadly-metadata/)
    - Keep original ZIP if desired
  ↓
Import complete (~5-10 seconds on modern device)
```

### Progress Reporting

The import emits progress events for UI feedback:

```kotlin
data class ImportProgress(
    val phase: String,           // "READING_SHOWS", "INSERTING_SHOWS", etc.
    val processedItems: Int,     // Current progress
    val totalItems: Int,         // Total items in phase
    val currentItem: String      // Current item description
)
```

UI can display:
- Progress bar (processedItems / totalItems)
- Current phase description
- Current item being processed

### Error Handling

**Parse Errors**:
- Log error with filename
- Skip malformed file
- Continue with remaining files
- Report count of successful vs failed parses

**Database Errors**:
- Roll back transaction if using
- Clear database on next import attempt
- Show error to user with retry option

**Network Errors** (download):
- Retry with exponential backoff
- Show error if persistent failure
- Allow user to retry manually

### Statistics

After import, the `data_version_v2` table contains:

```sql
INSERT INTO data_version_v2 VALUES (
    id = 1,
    data_version = "2.0.0",
    package_name = "Deadly Metadata",
    version_type = "release",
    imported_at = 1234567890,
    total_shows = 2487,
    total_files = 17234,  -- recordings count
    ...
);
```

### Future: Metadata Updates

**Current State**: No update mechanism (one-time import on setup)

**Future Consideration**:

When metadata package updates are implemented:

1. **Check for updates**: Query GitHub releases API for new version
2. **Compare versions**: Check against `data_version_v2.data_version`
3. **Download delta or full**: Decide on update strategy
4. **Preserve user data**: Don't delete `library_shows` or `recent_shows`
5. **Apply changes**:
    - INSERT new shows
    - UPDATE changed shows (setlist corrections, etc.)
    - DELETE removed shows (rare)
6. **Update data version**: Update `data_version_v2` record

**Challenges**:
- Preserve user library if show IDs change
- Handle show deletions gracefully
- Delta updates vs full re-import trade-offs

**Implementation Locations**:

- **Android**: `DataImportService.kt:114`, `DownloadService.kt`, `ZipExtractionService.kt`
- **iOS**: TBD (similar service layer pattern)

---

## Source 2: Archive.org APIs

### What It Is

Live data fetched on-demand from Archive.org's public metadata API. This provides **detailed recording information** not stored in the local database: track lists, user reviews, and detailed metadata.

**Why Not in Database**: See [Design Philosophy - Two-Tier Architecture](design-philosophy.md#5-two-tier-data-architecture)

### API Endpoint

```
GET https://archive.org/metadata/{recordingId}
```

Example:
```
https://archive.org/metadata/gd1977-05-08.sbd.miller.97065.flac16
```

Returns comprehensive JSON with:
- Full track list (all formats: MP3, FLAC, Ogg Vorbis, etc.)
- User reviews with ratings and text
- Detailed metadata (uploader, collection, dates, etc.)
- File information (sizes, checksums, formats)

### Data Extracted

From the API response, we extract:

**Tracks**:
```kotlin
data class Track(
    val name: String,        // "gd77-05-08d1t01.mp3"
    val title: String?,      // "New Minglewood Blues"
    val duration: String?,   // "05:21"
    val format: String       // "VBR MP3"
)
```

**Reviews**:
```kotlin
data class Review(
    val reviewer: String?,   // "deadhead42"
    val rating: Int?,        // 5 (stars)
    val body: String?,       // Review text
    val reviewDate: String?  // "2015-05-08"
)
```

**Recording Metadata**:
```kotlin
data class RecordingMetadata(
    val identifier: String,
    val title: String?,
    val description: String?,
    val uploader: String?,
    val publicDate: String?,
    val addedDate: String?
)
```

### Filesystem Cache

**Why Filesystem**:
- Large payloads (~5-20KB per recording)
- Short TTL (24 hours)
- Ephemeral nature (can be deleted anytime)
- OS handles cleanup automatically

**Cache Structure**:

```
<app_cache_dir>/
  archive/
    {recordingId}.metadata.json
    {recordingId}.tracks.json
    {recordingId}.reviews.json
```

Example:
```
/cache/
  archive/
    gd1977-05-08.sbd.miller.97065.flac16.metadata.json
    gd1977-05-08.sbd.miller.97065.flac16.tracks.json
    gd1977-05-08.sbd.miller.97065.flac16.reviews.json
```

**File Format**: Plain JSON (kotlinx.serialization format)

```json
// tracks.json
[
  {
    "name": "gd77-05-08d1t01.mp3",
    "title": "New Minglewood Blues",
    "duration": "05:21",
    "format": "VBR MP3"
  }
]
```

### Cache Flow

```
User opens show detail page
  ↓
User selects a recording
  ↓
App requests tracks for recording
  ↓
ArchiveService.getRecordingTracks(recordingId)
  ↓
1. Check cache file exists
   - Path: cache/archive/{recordingId}.tracks.json
  ↓
2. Check cache not expired
   - Read file.lastModified()
   - Expired if: now - lastModified > 24 hours
  ↓
3a. CACHE HIT (file exists, not expired)
    - Read JSON from file (~10ms)
    - Deserialize to List<Track>
    - Return to UI
  ↓
3b. CACHE MISS (no file or expired)
    - Fetch from Archive.org API (~500-2000ms)
    - Parse API response
    - Extract tracks
    - Serialize to JSON
    - Write to cache file
    - Return to UI
  ↓
UI displays track list
```

### Cache Expiry Logic

```kotlin
private fun isCacheExpired(timestamp: Long): Boolean {
    val expiryTime = timestamp + (24 * 60 * 60 * 1000L)  // 24 hours
    return System.currentTimeMillis() > expiryTime
}
```

**Why 24 Hours**:
- Balances freshness with API load
- Archive.org data rarely changes for old shows
- User typically views a show once per session
- Cached data likely still valid on next session

### Error Handling

**API Failure** (network error, timeout, 500 error):
```kotlin
return Result.failure(Exception("API error: ${response.code()}"))
```
- UI shows error state
- User can retry
- No cache fallback (stale data worse than error)

**Cache Read Error** (corrupted file):
```kotlin
catch (e: Exception) {
    Log.e(TAG, "Cache read failed, fetching from API", e)
    // Delete corrupted cache file
    cacheFile.delete()
    // Fetch from API as fallback
    return fetchFromApi(recordingId)
}
```

**JSON Parse Error** (invalid JSON):
- Same as cache read error
- Delete corrupted file
- Re-fetch from API

### Cache Management

**Automatic Cleanup**:
- OS automatically cleans cache directory when storage is low
- No manual cleanup needed in most cases

**Manual Cleanup** (if implemented):

```kotlin
// Clear specific recording
suspend fun clearCache(recordingId: String) {
    File(cacheDir, "$recordingId.metadata.json").delete()
    File(cacheDir, "$recordingId.tracks.json").delete()
    File(cacheDir, "$recordingId.reviews.json").delete()
}

// Clear all cache
suspend fun clearAllCache() {
    cacheDir.listFiles()?.forEach { file ->
        if (file.isFile && file.name.endsWith(".json")) {
            file.delete()
        }
    }
}
```

**User-Initiated Cleanup**:
- Settings screen could offer "Clear Cache" button
- Useful if storage is critically low
- Cache rebuilds automatically on next use

### Prefetch Optimization

**Problem**: First load of a show is slow (API fetch required)

**Solution**: Prefetch tracks for adjacent shows in background

```kotlin
// After loading tracks for current show
startAdjacentPrefetch()

fun startAdjacentPrefetch() {
    coroutineScope.launch {
        // Prefetch next 2 shows
        val nextShows = getNextShows(currentShow, count = 2)
        nextShows.forEach { show ->
            if (!trackCache.containsKey(show.bestRecordingId)) {
                prefetchTracks(show.bestRecordingId)
            }
        }

        // Prefetch previous 2 shows
        val prevShows = getPreviousShows(currentShow, count = 2)
        prevShows.forEach { show ->
            if (!trackCache.containsKey(show.bestRecordingId)) {
                prefetchTracks(show.bestRecordingId)
            }
        }
    }
}
```

**Benefits**:
- Next/previous navigation feels instant
- Background work doesn't block UI
- Only prefetch if not already cached

**Implementation Location**: `PlaylistServiceImpl.kt:627`

### API Rate Limiting

**Archive.org Policy**: No published rate limits, but be respectful

**Our Approach**:
- Cache aggressively (24h TTL)
- Don't prefetch excessively (only adjacent shows)
- No bulk API calls (one recording at a time)
- Use cache-first strategy (never fetch if cached)

**If Rate Limited** (429 response):
- Retry with exponential backoff
- Fall back to cached data if available (even if expired)
- Show error to user if unavoidable

### Platform Implementation

**Android** (Current):
- `ArchiveServiceImpl.kt:25`
- Uses Retrofit for API calls
- Uses kotlinx.serialization for JSON
- Uses Android cache directory

**iOS** (To Be Implemented):
- Use URLSession for API calls
- Use Codable for JSON parsing
- Use FileManager.default.cachesDirectory
- Implement same cache structure and TTL

**Critical**: Both platforms must use same cache structure (filename patterns, JSON format) for consistency.

---

## Comparison: Database vs Cache

| Aspect | Database (Catalog) | Filesystem Cache (Runtime) |
|--------|-------------------|---------------------------|
| **What** | Shows, recordings metadata, collections | Tracks, reviews, detailed metadata |
| **When** | One-time import on setup | On-demand per show viewed |
| **Size** | ~10-15 MB | Variable (~5-20KB per recording) |
| **Queryable** | Yes (SQL, FTS4) | No (flat JSON files) |
| **Lifecycle** | Persistent (until app uninstall) | Ephemeral (24h TTL, OS cleanup) |
| **Update** | Rare (metadata package updates) | Frequent (re-fetch after 24h) |
| **Offline** | Always available | Only if cached |
| **Access Pattern** | Browse, search, filter | Load once per show |

---

## Data Flow Summary

```
┌─────────────────────────────────────────────────────────────┐
│ INITIAL SETUP (One-Time)                                    │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  GitHub Release                                              │
│  (dead-metadata)                                             │
│         │                                                    │
│         │ Download ZIP                                       │
│         ↓                                                    │
│  App Cache Directory                                         │
│         │                                                    │
│         │ Extract & Parse JSON                               │
│         ↓                                                    │
│  SQLite Database                                             │
│  (Shows, Recordings, Collections)                            │
│                                                              │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ RUNTIME (Per Show)                                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  User opens show detail                                      │
│         │                                                    │
│         │ Query show + recordings                            │
│         ↓                                                    │
│  SQLite Database ────────────────→ UI (show details)         │
│                                                              │
│  User selects recording                                      │
│         │                                                    │
│         │ Request tracks                                     │
│         ↓                                                    │
│  Check Filesystem Cache                                      │
│         │                                                    │
│    ┌────┴────┐                                              │
│    │         │                                               │
│  HIT       MISS                                              │
│    │         │                                               │
│    │         └──→ Archive.org API                            │
│    │                    │                                    │
│    │                    │ Fetch & Cache                      │
│    │                    ↓                                    │
│    └────────────────→ JSON File                              │
│                         │                                    │
│                         └──────────→ UI (track list)         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## References

- [Design Philosophy](design-philosophy.md) - Why two-tier architecture
- [Data Flow](data-flow.md) - Detailed flow diagrams
- [Platform Implementation](platform-implementation.md) - iOS implementation guidance
