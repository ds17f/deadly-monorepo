# Platform Implementation Guide

This document defines what must be identical between Android and iOS implementations, and what can differ. The goal: ensure data compatibility and consistent behavior across platforms while allowing platform-specific optimizations.

---

## Status

**Android Implementation**: ✅ Complete (Room + SQLite + FTS4)

**iOS Implementation**: 🔄 Not Started

**iOS Framework Choices**: ⚠️ **TBD** - Research required before implementation

---

## 1. What Must Be Identical

These elements define the data contract and **must match exactly** between platforms.

### 1.1 Database Schemas

All 7 entity schemas must be replicated exactly in iOS:

| Entity | Schema Doc | Critical Fields |
|--------|------------|-----------------|
| ShowEntity | [shows.md](entities/shows.md) | All 30+ fields including denormalized venue data |
| RecordingEntity | [recordings.md](entities/recordings.md) | Quality metrics, FK to shows |
| LibraryShowEntity | [library-shows.md](entities/library-shows.md) | showId (PK/FK), isPinned, addedToLibraryAt |
| RecentShowEntity | [recent-shows.md](entities/recent-shows.md) | showId (PK), lastPlayedTimestamp, totalPlayCount |
| ShowSearchEntity | [show-search.md](entities/show-search.md) | FTS implementation with same searchText format |
| DeadCollectionEntity | [collections.md](entities/collections.md) | JSON storage for showIds and tags |
| DataVersionEntity | [data-version.md](entities/data-version.md) | Singleton pattern, version tracking |

**Why Identical**: Data export/import, backup/restore, potential sync features require identical schemas.

---

### 1.2 Denormalization Patterns

#### Venues in Shows Table

**Pattern**: Venue data stored directly in `shows` table (no separate venues table)

**Fields**: `venueName`, `city`, `state`, `country`, `locationRaw`

**Rationale**: Eliminates JOINs for 90% of queries

**iOS Must**: Store venue data inline in shows table, accept data duplication

**Reference**: [design-philosophy.md - Denormalization](design-philosophy.md#1-denormalization-venues-in-shows)

---

#### Library Status in Shows Table

**Pattern**: `shows.is_in_library` and `shows.library_added_at` mirror `library_shows` table

**Why**: Fast membership checks without JOIN

**iOS Must**:
- Mirror library status in shows table
- Update both tables in transaction (atomic consistency)
- Keep denormalized fields in sync

**Reference**: [library-shows.md - Denormalization](entities/library-shows.md#denormalization-in-showentity)

---

### 1.3 JSON Storage Formats

#### Setlist JSON

**Format**:
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
    "songs": [...]
  }
]
```

**Storage**: TEXT column `setlist_raw`

**iOS Must**: Parse/serialize to identical JSON structure

---

#### Lineup JSON

**Format**:
```json
[
  {"name": "Jerry Garcia", "instruments": "guitar, vocals"},
  {"name": "Bob Weir", "instruments": "guitar, vocals"}
]
```

**Storage**: TEXT column `lineup_raw`

**iOS Must**: Parse/serialize to identical JSON structure

---

#### Collection Show IDs JSON

**Format**:
```json
["1977-05-08-barton-hall-cornell-u-ithaca-ny-usa", "1977-05-09-buffalo-memorial-auditorium-buffalo-ny-usa"]
```

**Storage**: TEXT column `show_ids_json` in `collections` table

**iOS Must**: Parse/serialize to identical JSON array format

---

#### Tags JSON

**Format**:
```json
["era", "early-dead", "psychedelic"]
```

**Storage**: TEXT column `tags_json` in `collections` table

**iOS Must**: Parse/serialize to identical JSON array format

---

### 1.4 Primary Keys and Foreign Keys

#### Primary Keys

| Table | Primary Key | Type | Notes |
|-------|-------------|------|-------|
| shows | `show_id` | TEXT | Format: `{date}-{venue-slug}-{city-slug}-{state}-{country}` |
| recordings | `identifier` | TEXT | Archive.org identifier |
| library_shows | `show_id` | TEXT | FK to shows |
| recent_shows | `show_id` | TEXT | No FK (logical reference) |
| show_search | `rowid` | INTEGER | FTS auto-generated |
| collections | `id` | TEXT | Kebab-case slug |
| data_version | `id` | INTEGER | Always `1` (singleton) |

**iOS Must**: Use identical primary key types and formats

---

#### Foreign Keys with CASCADE

```sql
recordings.show_id → shows.show_id (ON DELETE CASCADE)
library_shows.show_id → shows.show_id (ON DELETE CASCADE)
```

**iOS Must**: Implement CASCADE DELETE (or equivalent behavior)

**Reference**: [Design sections in entity docs](entities/shows.md#relationships)

---

### 1.5 Required Indexes

These indexes are **critical for performance** and must exist on iOS:

**Shows Table**:
- `idx_shows_date` - Chronological browsing (most common query)
- `idx_shows_year` - Year filtering
- `idx_shows_yearMonth` - Month filtering
- `idx_shows_city` - Location browsing
- `idx_shows_state` - Location browsing

**Recordings Table**:
- `idx_recordings_show_id_rating` - **Composite index** (show_id, rating) - Best recording queries
- `idx_recordings_source_type` - Filter by SBD/AUD/etc

**Library Table**:
- `idx_library_shows_is_pinned` - Sort pinned first
- `idx_library_shows_added_at` - Chronological order

**Recent Shows Table**:
- `idx_recent_shows_last_played DESC` - **Descending index** for recent history

**Collections Table**:
- `idx_collections_primary_tag` - Tag filtering
- `idx_collections_total_shows` - Sort by size

**iOS Must**: Create all these indexes with same column order

**Reference**: [query-patterns.md](query-patterns.md) - Shows which queries use which indexes

---

### 1.6 Full-Text Search Implementation

#### Search Text Format

**Must Index** (in this order):
```
{date} {short-date-variations} {year} {decade} {venue} {city} {state} {country} {members} {songs}
```

**Example**:
```
1977-05-08 5-8-77 5/8/77 5.8.77 1977 77 197 Barton Hall Cornell University Ithaca NY New York USA Jerry Garcia Bob Weir Phil Lesh Scarlet Begonias Fire on the Mountain
```

**Why So Many Date Formats**: Users search many ways - "5-8-77", "Cornell 77", "May 1977"

**iOS Must**: Build identical searchText string during import

**Reference**: [show-search.md](entities/show-search.md), [DataImportService.kt:460-514](entities/show-search.md#building-search-text)

---

#### Tokenization Requirements

**Android Uses**: FTS4 with `unicode61` tokenizer, `tokenchars=-.`

**Effect**: Preserves dashes and dots in tokens
- `"5-8-77"` → single token (not split)
- `"5.8.77"` → single token

**iOS Framework Options**:
- **FTS5**: Supports `tokenchars` (recommended if available)
- **FTS4**: Same as Android
- **Custom tokenizer**: Must preserve date tokens

**iOS Must**: Ensure date strings like "5-8-77" are searchable as single tokens

**Reference**: [show-search.md - Tokenization](entities/show-search.md#token-characters----and-)

---

### 1.7 Two-Tier Architecture

#### Database (Tier 1)

**Stores**: Shows, recordings (metadata only), collections, library, recent plays

**Lifecycle**: Imported once, rarely updated

**iOS Must**: Replicate all 7 tables

---

#### Filesystem Cache (Tier 2)

**Stores**: Track lists, reviews, detailed recording metadata (from Archive.org API)

**Lifecycle**: On-demand fetch, 24h TTL, auto-cleanup

**Cache Structure**:
```
<cache_dir>/
  archive/
    {recordingId}.metadata.json
    {recordingId}.tracks.json
    {recordingId}.reviews.json
```

**Example**: `gd1977-05-08.sbd.miller.97065.flac16.tracks.json`

**iOS Must**:
- Use same cache directory structure
- Use same file naming convention
- Implement 24h TTL
- Store as JSON files (not in database)

**Why**: Tracks/reviews are large, change on Archive.org, only needed for viewed shows

**Reference**: [data-flow.md - Cache Management](data-flow.md#4-cache-management-flow)

---

### 1.8 UPSERT Pattern (Recent Shows)

**Pattern**: One row per show, updated on each play (not event log)

**Fields**: `lastPlayedTimestamp` (updated), `firstPlayedTimestamp` (preserved), `totalPlayCount` (incremented)

**iOS Must**:
- Check if show exists in `recent_shows`
- If exists: UPDATE timestamp and increment count
- If not: INSERT with count=1
- Keep single row per show (enforce with PRIMARY KEY)

**Why**: Simple queries without GROUP BY

**Reference**: [recent-shows.md - UPSERT Pattern](entities/recent-shows.md#upsert-pattern)

---

### 1.9 Data Import Format

#### Dead Metadata Package

**Source**: GitHub releases - `deadly-metadata` repository

**Format**: ZIP containing:
```
shows/
  {show-id}.json (2,400 files)
recordings/
  {recording-id}.json (16,000 files)
collections.json
manifest.json
```

**iOS Must**:
- Download same ZIP
- Parse same JSON structure
- Extract to same data model
- Build FTS index with same searchText format

**Reference**: [data-sources.md](data-sources.md), [data-flow.md - Import Flow](data-flow.md#1-initial-setup-flow)

---

## 2. What Can Differ

These elements are platform-specific and can be implemented differently.

### 2.1 Database Framework

**Android Uses**: Room Persistence Library (SQLite wrapper)

**iOS Options** (TBD - research required):
- **GRDB** - SQLite wrapper with FTS5, Swift-friendly
- **Core Data** - Apple's ORM (more complex, less direct SQL control)
- **SQLite.swift** - Lightweight SQLite wrapper
- **Raw SQLite** - Direct C API (most control, most work)

**Considerations**:
- FTS support (FTS4 or FTS5)
- Swift type safety
- Query builder vs raw SQL
- Migration support
- Community support

**Decision Required**: Choose based on FTS support, ergonomics, team preference

---

### 2.2 Dependency Injection

**Android Uses**: Hilt (Dagger-based)

**iOS Options** (TBD):
- No DI framework (manual injection)
- Custom DI container
- Third-party framework (Swinject, etc.)

**iOS Can**: Use any DI approach or none at all

**What Matters**: Database instances properly scoped (singleton for app lifetime)

---

### 2.3 Migration Strategy

**Android Uses**: Destructive migrations (V2 dev phase) - **will change before production**

**iOS Can**:
- Use same destructive approach during development
- Implement proper migrations earlier
- Use different migration strategy

**What Matters**: Production apps need safe migrations; development can be destructive

---

### 2.4 Reactive Patterns

**Android Uses**: Kotlin Flow, StateFlow, Room @Query returns Flow

**iOS Options** (TBD):
- Combine publishers
- AsyncSequence (Swift 5.5+)
- @Observable macro (Swift 5.9+)
- ObservableObject

**iOS Can**: Use any reactive pattern that works with SwiftUI

**What Matters**: UI updates when database changes

---

### 2.5 Query Language

**Android Uses**: Room @Query with SQL strings

**iOS Can**:
- Write raw SQL (if using GRDB/SQLite.swift)
- Use query builders (if using GRDB)
- Use Core Data NSFetchRequest (if using Core Data)

**What Matters**: Queries return same data, use same indexes

**Reference**: [query-patterns.md](query-patterns.md) - Shows required queries

---

### 2.6 Background Processing

**Android Uses**: Kotlin Coroutines with Dispatchers.IO

**iOS Can**:
- Swift concurrency (async/await with Task)
- DispatchQueue.global()
- Actors for data isolation

**What Matters**: Database operations off main thread, thread-safe access

---

### 2.7 JSON Serialization

**Android Uses**: kotlinx.serialization

**iOS Options**:
- Codable (Swift standard)
- JSONSerialization (Foundation)
- Third-party (SwiftyJSON, etc.)

**iOS Can**: Use any JSON library

**What Matters**: Serialize/deserialize to same JSON structure (see section 1.3)

---

## 3. Implementation Checklist

When implementing iOS database layer:

### Phase 1: Schema Setup
- [ ] Choose database framework (research FTS support)
- [ ] Create all 7 entity definitions matching schemas exactly
- [ ] Create all required indexes
- [ ] Implement foreign key constraints (or equivalent behavior)
- [ ] Test: Can create empty database with correct schema

### Phase 2: FTS Implementation
- [ ] Implement FTS table (FTS4 or FTS5)
- [ ] Verify tokenization preserves date tokens ("5-8-77")
- [ ] Build searchText string exactly as Android does
- [ ] Test: Search for "5-8-77" finds Cornell '77

### Phase 3: Data Import
- [ ] Download metadata ZIP from GitHub
- [ ] Parse show JSON files
- [ ] Parse recording JSON files
- [ ] Parse collections JSON
- [ ] Build FTS index during import
- [ ] Implement import progress tracking
- [ ] Test: Import all 2,400 shows successfully

### Phase 4: Core Queries
- [ ] Implement all queries from [query-patterns.md](query-patterns.md)
- [ ] Verify query performance (use indexes)
- [ ] Test: Chronological browse, search, navigation work

### Phase 5: User Data
- [ ] Implement library add/remove with denormalization sync
- [ ] Implement UPSERT pattern for recent shows
- [ ] Test: Library and recent history work correctly

### Phase 6: Cache Layer
- [ ] Create filesystem cache directory structure
- [ ] Implement 24h TTL check
- [ ] Fetch tracks/reviews from Archive.org API
- [ ] Cache JSON files
- [ ] Test: Cache hit/miss behavior

### Phase 7: Reactive Updates
- [ ] Wire database changes to SwiftUI views
- [ ] Test: UI updates when data changes

---

## 4. Critical Success Criteria

iOS implementation is correct if:

✅ **Same schemas**: All 7 tables match Android exactly

✅ **Same indexes**: Query performance is comparable

✅ **Same search behavior**: "5-8-77" finds Cornell '77

✅ **Same JSON formats**: Setlists/lineups parse correctly

✅ **Same denormalization**: Venue data in shows, library status mirrored

✅ **Same UPSERT pattern**: One row per show in recent_shows

✅ **Same cache structure**: Tracks in filesystem, not database

✅ **Data import works**: Can import dead-metadata package

✅ **Query patterns work**: All queries from query-patterns.md return correct data

---

## 5. Validation Strategy

### Cross-Platform Testing

1. **Export Android database** (after import)
2. **Import same data on iOS**
3. **Run same queries on both platforms**
4. **Compare results** (should be identical)

### Test Cases

**Shows Table**:
```
Query: WHERE year = 1977 ORDER BY date
Expected: ~100 shows starting with 1977-01-01
```

**FTS Search**:
```
Query: "Cornell 1977"
Expected: 1977-05-08-barton-hall-cornell-u-ithaca-ny-usa (top result)
```

**Recordings**:
```
Query: WHERE show_id = '1977-05-08-...' ORDER BY rating DESC LIMIT 1
Expected: Miller SBD (highest rated)
```

**Library Denormalization**:
```
Action: Add show to library
Check: shows.is_in_library = true AND library_shows entry exists
```

**Recent UPSERT**:
```
Action: Play same show twice
Check: recent_shows has 1 row with totalPlayCount = 2
```

---

## 6. Resources

**Schema Reference**: All entity docs in [entities/](entities/) directory

**Query Reference**: [query-patterns.md](query-patterns.md)

**Import Logic**: [data-flow.md - Initial Setup](data-flow.md#1-initial-setup-flow)

**Design Decisions**: [design-philosophy.md](design-philosophy.md)

**Android Implementation**: `androidApp/v2/core/database/` (reference implementation)

---

## 7. Open Questions (Require Research)

These questions must be answered before iOS implementation:

1. **Which iOS database framework?** (GRDB vs Core Data vs SQLite.swift vs raw SQLite)
    - Pros/cons of each
    - FTS4/FTS5 support
    - Swift ergonomics
    - Migration support

2. **FTS4 or FTS5?**
    - Is FTS4 `tokenchars=-.` supported on iOS?
    - Does FTS5 offer better performance?
    - Can we match Android's tokenization behavior?

3. **How to handle denormalization sync in Swift?**
    - Transaction API
    - Error handling
    - Rollback behavior

4. **Which reactive pattern for SwiftUI?**
    - Combine vs AsyncSequence vs @Observable
    - Best practices for database observation

5. **How to test database on iOS?**
    - In-memory database for tests?
    - XCTest patterns
    - Mock vs real database

---

## 8. Next Steps

**Before Implementation**:
1. Research iOS database frameworks (create comparison doc)
2. Prototype FTS search with different frameworks
3. Test tokenization behavior (ensure "5-8-77" works)
4. Choose framework and document decision

**During Implementation**:
1. Follow [Implementation Checklist](#3-implementation-checklist)
2. Validate against Android at each phase
3. Document iOS-specific patterns discovered
4. Update this doc with framework-specific guidance

---

## Summary

**Must Match**: Schemas, indexes, JSON formats, denormalization, UPSERT pattern, cache structure

**Can Differ**: Framework, DI, migrations, reactive patterns, query syntax

**Critical**: FTS tokenization must preserve date tokens for search to work

**Status**: iOS framework choices require research before implementation can begin
