# RecentShowEntity (recent_shows)

Tracks recently played shows using an UPSERT pattern. Each show has a single record that updates when played, eliminating complex GROUP BY queries while maintaining deduplication.

---

## Purpose

`RecentShowEntity` provides efficient recent play tracking:

- **Play history** - What shows user has played
- **Recency ordering** - Most recent plays first
- **Play frequency** - Total play count per show
- **First play tracking** - When user first discovered show
- **UPSERT pattern** - Single record per show, updated on each play

**Why UPSERT Pattern**: Alternative would be inserting new row on each play, requiring `GROUP BY showId` with `MAX(timestamp)` for deduplication. UPSERT keeps one row per show, making queries simple and fast.

---

## Schema

### Table Definition

```sql
CREATE TABLE recent_shows (
    show_id TEXT PRIMARY KEY,

    -- Play tracking
    last_played_timestamp INTEGER NOT NULL,
    first_played_timestamp INTEGER NOT NULL,
    total_play_count INTEGER NOT NULL,

    FOREIGN KEY (show_id) REFERENCES shows(show_id) ON DELETE CASCADE
);
```

### Indexes

```sql
CREATE INDEX idx_recent_shows_last_played ON recent_shows(last_played_timestamp DESC);
```

---

## Fields

### Primary Key

#### `show_id` (TEXT, PRIMARY KEY, FK, UNIQUE)

References parent show.

**Example**: `"1977-05-08-barton-hall-cornell-u-ithaca-ny-usa"`

**Foreign Key**: → `shows.show_id` with `CASCADE DELETE`

**Unique Constraint**: PRIMARY KEY ensures one record per show (UPSERT pattern)

**Usage**: Fast lookups, enforce one-entry-per-show constraint

---

### Play Tracking

#### `last_played_timestamp` (INTEGER, NOT NULL, INDEXED DESC)

Timestamp of the most recent play.

**Format**: Unix timestamp (milliseconds)

**Example**: `1678901234000`

**Updated**: Every time user plays any track from this show

**Indexed (DESC)**: Sort recent shows by recency (newest first)

**Usage**:
- Display "Recently played" section
- Order shows by most recent play
- Analytics: "Last played 2 days ago"

---

#### `first_played_timestamp` (INTEGER, NOT NULL)

Timestamp when user first played this show.

**Format**: Unix timestamp (milliseconds)

**Example**: `1678800000000`

**Set Once**: Never changes after initial insert

**Usage**:
- Analytics: "First played March 14, 2023"
- Track user's discovery timeline
- "New to you" features

---

#### `total_play_count` (INTEGER, NOT NULL)

Total number of times this show has been played.

**Example**: `12`

**Incremented**: Each time user plays any track from this show (after meaningful play threshold)

**Meaningful Play**: Typically 30+ seconds or >50% of track duration (prevents accidental taps from counting)

**Usage**:
- Display "Played 12 times"
- Sort by most played
- Analytics: User's favorite shows
- Recommendations: "Shows you play often"

---

## Relationships

### To ShowEntity (N:1)

```sql
FOREIGN KEY (show_id) REFERENCES shows(show_id) ON DELETE CASCADE
```

**Cardinality**: Many recent play records reference one show (but UPSERT ensures at most one record per show)

**Cascade Behavior**: If show deleted from catalog (rare), recent play record deleted (orphaned entry meaningless)

**Unique Constraint**: `show_id` is PRIMARY KEY, enforcing one-entry-per-show

---

## UPSERT Pattern

The UPSERT (update-or-insert) pattern is the core design of this table.

### How It Works

**First Play**:
```kotlin
val existing = recentShowDao.getShowById(showId)
if (existing == null) {
    // INSERT new record
    recentShowDao.insert(
        RecentShowEntity(
            showId = showId,
            lastPlayedTimestamp = now,
            firstPlayedTimestamp = now,
            totalPlayCount = 1
        )
    )
}
```

**Subsequent Plays**:
```kotlin
val existing = recentShowDao.getShowById(showId)
if (existing != null) {
    // UPDATE existing record
    recentShowDao.updateShow(
        showId = showId,
        timestamp = now,
        playCount = existing.totalPlayCount + 1
    )
}
```

### Why UPSERT?

**Alternative Approach (rejected)**:
```sql
-- Insert new row on each play
INSERT INTO play_history (show_id, timestamp);

-- Query requires GROUP BY for deduplication
SELECT show_id, MAX(timestamp) as last_played
FROM play_history
GROUP BY show_id
ORDER BY last_played DESC;
```

**Problems with Alternative**:
- Unbounded growth (millions of rows over time)
- Complex queries (GROUP BY, aggregations)
- Slower performance (scanning millions of rows)
- Play count requires `COUNT(*)` aggregation

**UPSERT Advantages**:
- Bounded size (one row per unique show, ~200-2000 rows typical)
- Simple queries (no GROUP BY needed)
- Fast performance (direct index lookups)
- Play count readily available (no aggregation)
- Storage efficient

---

## Common Queries

### Get Recently Played Shows

```sql
SELECT * FROM recent_shows
ORDER BY last_played_timestamp DESC
LIMIT 8;
```

**Performance**: Fast via `last_played_timestamp DESC` index

**Usage**: Home screen "Recently Played" section

**Typical Limit**: 8-10 shows for UI display

---

### Check if Show Was Played Recently

```sql
SELECT * FROM recent_shows
WHERE show_id = '1977-05-08-...'
  AND last_played_timestamp > :cutoff;
```

**Performance**: O(1) via PRIMARY KEY index, then simple comparison

**Usage**: "Continue listening" badge, resume playback

**Cutoff Example**: `System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)` (7 days)

---

### Get Most Played Shows

```sql
SELECT * FROM recent_shows
ORDER BY total_play_count DESC, last_played_timestamp DESC
LIMIT 10;
```

**Performance**: Full table scan (no index on play count), but table is small

**Usage**: "Most played" analytics section

**Tie-breaker**: `last_played_timestamp DESC` for consistent ordering

---

### Get Play Count for Show

```sql
SELECT total_play_count FROM recent_shows
WHERE show_id = '1977-05-08-...';
```

**Performance**: O(1) via PRIMARY KEY index

**Usage**: Display "You've played this 12 times"

---

### Count Total Recent Shows

```sql
SELECT COUNT(*) FROM recent_shows;
```

**Performance**: Fast (simple count)

**Usage**: Analytics, debugging

---

### Shows Played in Time Range

```sql
SELECT * FROM recent_shows
WHERE last_played_timestamp BETWEEN :start AND :end
ORDER BY last_played_timestamp DESC;
```

**Performance**: Full table scan, but table is small

**Usage**: "Shows played this week", analytics

**Example Range**: Last 7 days, last month, etc.

---

### Clear Old Shows (Privacy/Storage)

```sql
DELETE FROM recent_shows
WHERE last_played_timestamp < :cutoff;
```

**Performance**: Table scan for matching rows

**Usage**: Privacy settings ("Clear plays older than 90 days")

**Returns**: Number of deleted rows

---

## Common Operations

### Record a Play (UPSERT)

```kotlin
suspend fun recordPlay(showId: String) {
    val now = System.currentTimeMillis()
    val existing = recentShowDao.getShowById(showId)

    if (existing == null) {
        // First play - INSERT
        recentShowDao.insert(
            RecentShowEntity(
                showId = showId,
                lastPlayedTimestamp = now,
                firstPlayedTimestamp = now,
                totalPlayCount = 1
            )
        )
    } else {
        // Subsequent play - UPDATE
        recentShowDao.updateShow(
            showId = showId,
            timestamp = now,
            playCount = existing.totalPlayCount + 1
        )
    }
}
```

---

### Remove Show from Recent

```kotlin
suspend fun removeFromRecent(showId: String) {
    recentShowDao.removeShow(showId)
}
```

**Usage**: User privacy ("Remove from recent"), accidental play cleanup

---

### Clear All Recent Shows

```kotlin
suspend fun clearAllRecent() {
    recentShowDao.clearAll()
}
```

**Usage**: Privacy settings ("Clear all play history")

---

### Get Recent Shows with Full Show Data

```kotlin
suspend fun getRecentShowsWithDetails(): List<Show> {
    val recentEntities = recentShowDao.getRecentShows(limit = 8)
    val showIds = recentEntities.map { it.showId }
    return showDao.getShowsByIds(showIds)
        .sortedByDescending { show ->
            // Preserve recency order from recent_shows
            recentEntities.find { it.showId == show.showId }?.lastPlayedTimestamp ?: 0
        }
}
```

**Why Separate Queries**: Recent shows table only has IDs, must JOIN/fetch full show data for display

---

## Meaningful Play Threshold

Not all playback events count as "plays". A meaningful play typically requires:

**Criteria** (implementation-specific):
- **Duration threshold**: Played for at least 30 seconds
- **OR Percentage threshold**: Played at least 50% of track duration
- **Track skipping**: Rapidly skipping through tracks doesn't count

**Why**: Prevents accidental taps, previewing, and seeking from inflating play counts.

**Implementation Location**: Playback service/ViewModel (not in database layer)

**Example Logic**:
```kotlin
// In playback service
fun onTrackEnded(track: Track, playedDuration: Long) {
    val isMeaningfulPlay = playedDuration >= 30_000 ||
                           (playedDuration.toDouble() / track.duration >= 0.5)

    if (isMeaningfulPlay) {
        recentShowService.recordPlay(track.showId)
    }
}
```

---

## Reactive Queries

Use Flow for automatic UI updates:

```kotlin
// ViewModel
val recentShows: StateFlow<List<Show>> = recentShowDao.getRecentShowsFlow(limit = 8)
    .map { recentEntities ->
        // Fetch full show data
        val showIds = recentEntities.map { it.showId }
        showDao.getShowsByIds(showIds)
    }
    .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

// UI updates automatically when new show is played
```

---

## Privacy Considerations

Recent plays are sensitive user data. Consider implementing:

**Retention Policies**:
```kotlin
// Auto-delete plays older than 90 days
suspend fun pruneOldPlays() {
    val cutoff = System.currentTimeMillis() - (90 * 24 * 60 * 60 * 1000L)
    recentShowDao.deleteOldShows(cutoff)
}
```

**User Controls**:
- "Clear recent plays" button
- "Remove from recent" per-show action
- Auto-delete after X days setting

**Backup Considerations**:
- Some users may NOT want recent plays backed up
- Consider separate backup flag for privacy-sensitive tables

---

## Denormalization in ShowEntity

Unlike `library_shows`, there is **no denormalization** in the `shows` table for recent plays.

**Why No Denormalization**:
- Recent plays change frequently (every time user plays a show)
- Only needed for "Recently played" query (limited use case)
- Not worth the sync overhead and potential stale data
- Table is small enough that JOIN/fetch is fast

**Query Pattern**:
```kotlin
// Get recent show IDs, then fetch show data
val recentIds = recentShowDao.getRecentShows().map { it.showId }
val shows = showDao.getShowsByIds(recentIds)
```

---

## Code Locations

### Android

- **Entity**: `androidApp/v2/core/database/src/main/java/com/deadly/v2/core/database/entities/RecentShowEntity.kt:29`
- **DAO**: `androidApp/v2/core/database/src/main/java/com/deadly/v2/core/database/dao/RecentShowDao.kt:17`

### iOS

TBD (to be implemented)

---

## Implementation Notes

### When to Record Plays

**Trigger Points**:
1. **Track completion** - User listened to entire track
2. **Meaningful duration** - User listened for 30+ seconds
3. **Percentage threshold** - User listened to 50%+ of track

**Do NOT Record**:
- Rapid skipping through tracks
- Accidental taps (< 5 seconds)
- Seeking/scrubbing
- Background/cached track loading

**Implementation**:
```kotlin
// In MediaSessionService or ViewModel
private var trackStartTime: Long = 0
private var currentTrackDuration: Long = 0

fun onTrackStarted(track: Track) {
    trackStartTime = System.currentTimeMillis()
    currentTrackDuration = track.duration
}

fun onTrackEnded() {
    val playedDuration = System.currentTimeMillis() - trackStartTime
    val isMeaningfulPlay = playedDuration >= 30_000 ||
                           (playedDuration.toDouble() / currentTrackDuration >= 0.5)

    if (isMeaningfulPlay) {
        viewModelScope.launch {
            recentShowService.recordPlay(currentTrack.showId)
        }
    }
}
```

---

### Transaction Safety

UPSERT operations are inherently safe (single-table operation), but wrap in transaction for consistency:

```kotlin
database.withTransaction {
    val existing = recentShowDao.getShowById(showId)
    if (existing == null) {
        recentShowDao.insert(entity)
    } else {
        recentShowDao.updateShow(showId, timestamp, playCount)
    }
}
```

---

### Room @Upsert Support

Room 2.5+ supports `@Upsert` annotation (not used in current code):

```kotlin
@Upsert
suspend fun upsert(entity: RecentShowEntity)

// Usage
recentShowDao.upsert(
    RecentShowEntity(
        showId = showId,
        lastPlayedTimestamp = now,
        firstPlayedTimestamp = existing?.firstPlayedTimestamp ?: now,
        totalPlayCount = (existing?.totalPlayCount ?: 0) + 1
    )
)
```

**Current Implementation**: Manual `getShowById` + `insert`/`updateShow` pattern

**Why**: More explicit control over firstPlayedTimestamp preservation

---

## See Also

- [ShowEntity](shows.md) - Parent show entity
- [Design Philosophy](../design-philosophy.md#4-upsert-pattern-recent-shows) - Why UPSERT pattern
- [LibraryShowEntity](library-shows.md) - Similar user data pattern (but different sync requirements)
