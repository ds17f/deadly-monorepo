# Database Design

## Overview

The application uses a local database to store a queryable catalog of Grateful Dead shows and recordings. The database is optimized for read-heavy workloads with strategic denormalization and indexing.

**Technology**:
- **Android**: Room + SQLite
- **iOS**: TBD (GRDB recommended)

**Database Size**: ~10-15 MB (2,500 shows, 15,000-20,000 recordings)

**Key Characteristics**:

- Denormalized for fast queries (no JOINs)
- Full-text search with FTS4
- Two-tier architecture (database + filesystem cache)
- User data separate from catalog data

---

## Quick Start

**New to this codebase?** Start here:

1. Read [Design Philosophy](design-philosophy.md) - understand the "why"
2. Read [Data Sources](data-sources.md) - understand where data comes from
3. Browse entity docs below - understand what we store

**Implementing iOS version?** Read:

1. [Platform Implementation](platform-implementation.md) - Android vs iOS
2. Entity docs - replicate schemas exactly
3. [Data Sources](data-sources.md) - implement import + cache

---

## Core Concepts

### [Design Philosophy](design-philosophy.md)

The key design decisions that shape the database:

- **Denormalization** - Why venues are stored in show records
- **JSON Storage** - When and why we use JSON columns
- **FTS4 Search** - How full-text search works
- **UPSERT Pattern** - Recent shows implementation
- **Two-Tier Architecture** - Database vs filesystem cache

### [Data Sources](data-sources.md)

Where data comes from and how it flows into the system:

- **Dead Metadata Package** - Bulk import from GitHub repo (one-time)
- **Archive.org APIs** - On-demand fetching with filesystem cache (24h TTL)

### [Query Patterns](query-patterns.md)

What questions the app asks and how the schema optimizes for them:

- Browse by date/year/venue/location
- Full-text search across all show data
- Chronological navigation (next/previous)
- Library and recent history

### [Data Flow](data-flow.md)

How data moves through the system:

- Initial setup: metadata import
- Runtime: show detail page loading
- User actions: library management, play tracking

### [Platform Implementation](platform-implementation.md)

Android vs iOS considerations:

- What must be identical (schemas, denormalization, JSON format)
- What can differ (framework, DI, migrations)
- iOS implementation guidance

---

## Database Entities

### Catalog Entities (Read-Only After Import)

| Entity | Purpose | Relationships | Doc |
|--------|---------|---------------|-----|
| **ShowEntity** | Concert metadata, venue, setlist | 1:N recordings, 1:1 search | [shows.md](entities/shows.md) |
| **RecordingEntity** | Individual audio recordings | N:1 show (FK CASCADE) | [recordings.md](entities/recordings.md) |
| **DeadCollectionEntity** | Curated collections | N:M shows (via JSON) | [collections.md](entities/collections.md) |
| **ShowSearchEntity** | FTS4 full-text search index | 1:1 show (linked by ID) | [show-search.md](entities/show-search.md) |
| **DataVersionEntity** | Import version tracking | Singleton (1 row) | [data-version.md](entities/data-version.md) |

### User Data Entities (Mutable)

| Entity | Purpose | Relationships | Doc |
|--------|---------|---------------|-----|
| **LibraryShowEntity** | User's saved shows | 1:1 show (FK CASCADE) | [library-shows.md](entities/library-shows.md) |
| **RecentShowEntity** | Play history (UPSERT pattern) | Linked to shows (no FK) | [recent-shows.md](entities/recent-shows.md) |

---

## Entity Relationship Diagram

```
ShowEntity (shows)
    ├─→ RecordingEntity (recordings) [1:N, FK CASCADE]
    ├─→ LibraryShowEntity (library_shows) [1:0..1, FK CASCADE]
    └─→ ShowSearchEntity (show_search) [1:1, FTS4]

DeadCollectionEntity (collections)
    └─→ shows [N:M via JSON array]

RecentShowEntity (recent_shows)
    └─→ shows [linked by showId, no FK]

DataVersionEntity (data_version)
    [singleton, no relationships]
```

---

## Quick Reference

### Foreign Keys

| Child Table | Parent Table | Cascade Behavior | Why |
|-------------|--------------|------------------|-----|
| `recordings` | `shows` | CASCADE DELETE | Recordings meaningless without show |
| `library_shows` | `shows` | CASCADE DELETE | Library entry orphaned if show deleted |

### Indexes

See individual entity docs for complete index lists and rationale.

**Most Important Indexes**:
- `shows.date` - Chronological browsing
- `shows.year`, `shows.yearMonth` - Date-based filtering
- `shows.city`, `shows.state`, `shows.venueName` - Location browsing
- `recordings.show_id` - Recording lookup by show
- `recordings.(show_id, rating)` - Best recording queries
- `recent_shows.lastPlayedTimestamp DESC` - Recent history

### Data Sizes

| Entity | Row Count | Avg Row Size | Total Size |
|--------|-----------|--------------|------------|
| shows | ~2,500 | ~2 KB | ~5 MB |
| recordings | ~15,000-20,000 | ~500 bytes | ~8 MB |
| show_search | ~2,500 | ~500 bytes | ~1 MB |
| library_shows | User-dependent (10-100) | ~100 bytes | ~10 KB |
| recent_shows | User-dependent (<100) | ~50 bytes | ~5 KB |
| collections | ~20-30 | ~2 KB | ~50 KB |

**Total**: ~10-15 MB

---

## Common Patterns

### Denormalization

Venue data stored in shows table:
- **Why**: Eliminates JOINs for 90% of queries
- **Trade-off**: Data duplication (acceptable, read-only)
- **Details**: [Design Philosophy](design-philosophy.md#denormalization)

### JSON Storage

Complex nested data stored as JSON strings:
- **Examples**: Setlists, lineups, tags
- **Why**: Flexible schema, atomic updates, display-only
- **Details**: [Design Philosophy](design-philosophy.md#json-storage)

### UPSERT Pattern

Recent shows use one record per show:
- **Why**: Simple queries, no GROUP BY
- **Trade-off**: Lose granular play history (acceptable)
- **Details**: [Recent Shows](entities/recent-shows.md)

### Two-Tier Architecture

Database for catalog, filesystem cache for API responses:
- **Database**: Shows, recordings (queryable catalog)
- **Cache**: Tracks, reviews (ephemeral, 24h TTL)
- **Why**: Different lifecycles, different access patterns
- **Details**: [Data Sources](data-sources.md)

---

## Implementation Notes

### Android (Current)

- **Framework**: Room Persistence Library
- **Location**: `androidApp/v2/core/database/`
- **Migration**: Destructive (V2 dev phase) - **must change before production**
- **DI**: Hilt `@Singleton` scope
- **Key Classes**:
  - `DeadlyDatabase.kt:35` - Database class
  - `entities/*.kt` - Entity definitions
  - `dao/*.kt` - Data access objects
  - `service/DataImportService.kt:114` - Import logic

### iOS (To Be Implemented)

- **Framework**: GRDB recommended (SQLite wrapper with FTS5)
- **Must Match**: Schemas, indexes, denormalization, JSON format
- **Can Differ**: Framework, DI, migrations, query syntax
- **Details**: [Platform Implementation](platform-implementation.md)

---

## Next Steps

**For Understanding**:

1. Read [Design Philosophy](design-philosophy.md)
2. Read [Data Sources](data-sources.md)
3. Browse entity docs for specifics

**For Implementation**:

1. Start with [ShowEntity](entities/shows.md) - the core entity
2. Add [RecordingEntity](entities/recordings.md) - the second most important
3. Add [ShowSearchEntity](entities/show-search.md) - search is critical
4. Add user data entities (library, recent)
5. Add supporting entities (collections, version)

**For iOS Development**:

1. Read [Platform Implementation](platform-implementation.md)
2. Set up GRDB or chosen framework
3. Replicate schemas from entity docs
4. Implement import from [Data Sources](data-sources.md)
5. Implement filesystem cache from [Data Sources](data-sources.md)

---

## Questions?

- **Not sure which entity?** Check the [Entity Relationship Diagram](#entity-relationship-diagram) above
- **Not sure why something is designed this way?** Read [Design Philosophy](design-philosophy.md)
- **Not sure how to implement on iOS?** Read [Platform Implementation](platform-implementation.md)
- **Not sure how data flows?** Read [Data Flow](data-flow.md)
