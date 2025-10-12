# DeadCollectionEntity (dead_collections)

Represents curated collections of shows (e.g., "Dick's Picks", "Road Trips", "The Acid Tests"). Collections group shows by theme, era, or release series, helping users discover related shows.

---

## Purpose

`DeadCollectionEntity` stores curated show collections:

- **Collection metadata** - Name, description, tags
- **Show membership** - Which shows belong to collection
- **Discovery features** - Browse collections, explore eras
- **Categorization** - Tag-based organization

**Why Collections**: Helps users navigate 2,400+ shows via curated groupings. Archive.org doesn't provide structured collections, so we maintain them ourselves.

---

## Schema

### Table Definition

```sql
CREATE TABLE dead_collections (
    id TEXT PRIMARY KEY,

    -- Metadata
    name TEXT NOT NULL,
    description TEXT NOT NULL,

    -- JSON serialized data
    tags_json TEXT NOT NULL,
    show_ids_json TEXT NOT NULL,

    -- Precomputed for performance
    total_shows INTEGER NOT NULL,
    primary_tag TEXT,

    -- Timestamps
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

### Indexes

```sql
CREATE INDEX idx_dead_collections_primary_tag ON dead_collections(primary_tag);
CREATE INDEX idx_dead_collections_total_shows ON dead_collections(total_shows);
```

---

## Fields

### Primary Key

#### `id` (TEXT, PRIMARY KEY, UNIQUE)

Unique collection identifier.

**Format**: Kebab-case slug

**Example**: `"dicks-picks"`, `"acid-tests"`, `"cornell-77"`

**Source**: Generated from collection name during import

**Usage**: URL-safe identifiers, stable references

---

### Metadata

#### `name` (TEXT, NOT NULL)

Display name for collection.

**Example**: `"Dick's Picks"`, `"The Acid Tests"`, `"Europe '72"`

**Format**: Human-readable title

**Usage**: Display to user in collection browser

---

#### `description` (TEXT, NOT NULL)

Detailed description of collection.

**Example**: `"The early days of the Grateful Dead and the Merry Pranksters, featuring legendary shows from the Acid Tests era (1965-1966)."`

**Format**: Plain text (markdown could be supported in future)

**Length**: Typically 1-3 sentences

**Usage**: Display on collection detail page

---

### JSON Serialized Data

#### `tags_json` (TEXT, NOT NULL)

Array of tags as JSON.

**Format**: JSON array of strings

**Example**: `["era", "early-dead", "psychedelic"]`

**Usage**:
- Filter collections by tag
- Display collection categories
- Discovery features ("Similar collections")

**Why JSON**: Tags are variable-length list. JSON avoids creating separate `collection_tags` join table (collections are read-only, small dataset, simple schema).

**Deserialization**:
```kotlin
val tags: List<String> = Json.decodeFromString(collection.tagsJson)
```

---

#### `show_ids_json` (TEXT, NOT NULL)

Array of show IDs as JSON.

**Format**: JSON array of strings

**Example**: `["1977-05-08-barton-hall-cornell-u-ithaca-ny-usa", "1977-05-09-buffalo-memorial-auditorium-buffalo-ny-usa"]`

**Why JSON**: Maintains N:M relationship without join table. Collections are read-only curated data (not user-editable), making JSON acceptable. Avoids `collection_shows` join table for simple read-only data.

**Typical Size**: 3-50 shows per collection

**Deserialization**:
```kotlin
val showIds: List<String> = Json.decodeFromString(collection.showIdsJson)
```

**Query Pattern**:
```kotlin
// Get shows in collection
val showIds = Json.decodeFromString<List<String>>(collection.showIdsJson)
val shows = showDao.getShowsByIds(showIds)
```

---

### Precomputed Fields

#### `total_shows` (INTEGER, NOT NULL, INDEXED)

Count of shows in collection.

**Example**: `23`

**Precomputed**: Calculated during import from `showIdsJson.length`

**Indexed**: Sort collections by size ("largest collections")

**Why Precomputed**: Avoid JSON parsing for sorting/filtering

**Usage**:
- Display "23 shows" on collection card
- Sort by collection size
- Filter "collections with 10+ shows"

---

#### `primary_tag` (TEXT, NULLABLE, INDEXED)

First tag from tags array for fast filtering.

**Example**: `"era"`

**Precomputed**: First element of `tagsJson` array

**Indexed**: Filter collections by primary tag without JSON parsing

**Why Precomputed**: Avoid JSON parsing for common filter queries

**Usage**:
- Filter collections by category
- Group collections in UI
- "Era" vs "Release Series" tabs

**Nullable**: Some collections may have no tags

---

### Timestamps

#### `created_at` (INTEGER, NOT NULL)

Timestamp when collection was created.

**Format**: Unix timestamp (milliseconds)

**Example**: `1678901234000`

**Source**: Set during import

**Usage**: Audit trail, debugging

---

#### `updated_at` (INTEGER, NOT NULL)

Timestamp when collection was last updated.

**Format**: Unix timestamp (milliseconds)

**Example**: `1678901234000`

**Source**: Updated when collection metadata or shows change

**Usage**: Track data freshness, sync logic

---

## Relationships

### To ShowEntity (N:M via JSON)

**Cardinality**: Many collections contain many shows

**Implementation**: `show_ids_json` field contains array of show IDs

**No Foreign Key**: JSON-stored IDs are not enforced by database

**Query Pattern**:
```kotlin
// Get shows in collection
val showIds = Json.decodeFromString<List<String>>(collection.showIdsJson)
val shows = showDao.getShowsByIds(showIds)

// Get collections containing show (reverse lookup)
val collections = collectionsDao.getCollectionsContainingShow(showId)
// Uses LIKE query: WHERE showIdsJson LIKE '%showId%'
```

**Why No Join Table**:
- Collections are read-only curated data (not user-editable)
- Small dataset (~20-50 collections total)
- JSON is simpler than managing join table
- Acceptable performance trade-off for read-heavy use case

---

## Common Queries

### Get All Collections

```sql
SELECT * FROM dead_collections
ORDER BY name ASC;
```

**Performance**: Fast (small table, typically 20-50 rows)

**Usage**: Collection browser, discovery page

---

### Get Collection by ID

```sql
SELECT * FROM dead_collections
WHERE id = 'dicks-picks';
```

**Performance**: O(1) via PRIMARY KEY index

**Usage**: Collection detail page

---

### Get Featured Collections (Largest)

```sql
SELECT * FROM dead_collections
ORDER BY total_shows DESC
LIMIT 6;
```

**Performance**: Fast via `total_shows` index

**Usage**: Home screen "Featured Collections" section

---

### Filter by Primary Tag

```sql
SELECT * FROM dead_collections
WHERE primary_tag = 'era'
ORDER BY name ASC;
```

**Performance**: Fast via `primary_tag` index

**Usage**: "Era" vs "Release Series" tabs in UI

---

### Search Collections

```sql
SELECT * FROM dead_collections
WHERE name LIKE '%query%' OR description LIKE '%query%'
ORDER BY name ASC;
```

**Performance**: Full table scan, but table is small

**Usage**: Collection search bar

**Note**: Collections could use FTS4 if search performance becomes issue, but unlikely with small dataset

---

### Get Collections Containing Show (Reverse Lookup)

```sql
SELECT * FROM dead_collections
WHERE show_ids_json LIKE '%1977-05-08-barton-hall-cornell-u-ithaca-ny-usa%'
ORDER BY name ASC;
```

**Performance**: Full table scan with string matching (acceptable for small table)

**Usage**: Show detail page - "Part of collections: Dick's Picks Vol. 15, Spring '77"

**Caveat**: LIKE query can have false positives if show ID is substring of another. In practice, show IDs are unique enough that this is not an issue.

---

### Get Large Collections

```sql
SELECT * FROM dead_collections
WHERE total_shows >= 10
ORDER BY total_shows DESC;
```

**Performance**: Fast via `total_shows` index

**Usage**: Filter "substantial collections"

---

### Count Total Collections

```sql
SELECT COUNT(*) FROM dead_collections;
```

**Performance**: Fast (simple count)

**Usage**: Analytics, debugging

---

## Common Operations

### Get Shows in Collection

```kotlin
suspend fun getShowsInCollection(collectionId: String): List<Show> {
    val collection = collectionsDao.getCollectionById(collectionId) ?: return emptyList()

    // Parse JSON array of show IDs
    val showIds = Json.decodeFromString<List<String>>(collection.showIdsJson)

    // Fetch shows by IDs
    return showDao.getShowsByIds(showIds)
}
```

---

### Get Collections for Show

```kotlin
suspend fun getCollectionsForShow(showId: String): List<DeadCollectionEntity> {
    return collectionsDao.getCollectionsContainingShow(showId)
}
```

**Usage**: Display "This show appears in: Dick's Picks Vol. 8, Road Trips Vol. 2"

---

### Parse Tags

```kotlin
fun getTagsForCollection(collection: DeadCollectionEntity): List<String> {
    return Json.decodeFromString(collection.tagsJson)
}
```

---

### Group Collections by Primary Tag

```kotlin
suspend fun getCollectionsByCategory(): Map<String, List<DeadCollectionEntity>> {
    val allCollections = collectionsDao.getAllCollections()
    return allCollections.groupBy { it.primaryTag ?: "Uncategorized" }
}
```

**Usage**: Tabbed UI - "Eras", "Release Series", "Special Events"

---

## Data Source

Collections come from **dead-metadata package** (curated dataset).

### Import File

**Path**: `collections.json` in metadata ZIP

**Format**:
```json
{
  "collections": [
    {
      "id": "dicks-picks",
      "name": "Dick's Picks",
      "description": "Official release series...",
      "tags": ["official-release", "soundboard"],
      "shows": [
        {
          "identifier": "gd1977-05-08...",
          "volume": 15,
          "notes": "Cornell '77"
        }
      ]
    }
  ]
}
```

### Import Process

1. Read `collections.json` from metadata ZIP
2. For each collection:
   - Generate `id` (slug from name)
   - Extract show IDs from `shows` array
   - Serialize `tags` to JSON string
   - Serialize `showIds` to JSON string
   - Count shows for `total_shows`
   - Extract first tag for `primary_tag`
   - Set timestamps
3. Insert as `DeadCollectionEntity` with `REPLACE` conflict strategy
4. Verify all referenced shows exist in `shows` table

**See Also**: [Data Sources](../data-sources.md#collections-import)

---

## JSON Storage Trade-offs

### Advantages

**Simplicity**: No join table, simpler schema

**Performance**: Read-only data with infrequent queries - acceptable

**Atomicity**: Collection shows always loaded together

**Size**: Small dataset (~20-50 collections, ~3-50 shows each)

### Disadvantages

**No Foreign Key Enforcement**: Can reference non-existent shows

**LIKE Query Performance**: Reverse lookup (show → collections) requires string matching

**Update Complexity**: Modifying collection shows requires JSON parsing + serialization

**Query Limitations**: Cannot JOIN on shows directly

### Why It's Acceptable

1. **Read-only data**: Collections are curated, not user-editable
2. **Small dataset**: 20-50 collections total, bounded size
3. **Infrequent queries**: Not performance-critical path
4. **Simple use case**: Loading collection shows is atomic operation
5. **Alternative is overkill**: Join table adds complexity for minimal benefit

### If We Used Join Table

**Alternative Schema** (not implemented):
```sql
CREATE TABLE collection_shows (
    collection_id TEXT NOT NULL,
    show_id TEXT NOT NULL,
    display_order INTEGER,
    notes TEXT,
    PRIMARY KEY (collection_id, show_id),
    FOREIGN KEY (collection_id) REFERENCES dead_collections(id) ON DELETE CASCADE,
    FOREIGN KEY (show_id) REFERENCES shows(show_id) ON DELETE CASCADE
);
```

**Trade-offs**:
- ✅ Foreign key enforcement
- ✅ Efficient reverse lookup (show → collections)
- ✅ Can ORDER BY display_order
- ❌ More complex schema
- ❌ More tables to maintain
- ❌ More joins for queries
- ❌ Overkill for read-only curated data

---

## Reactive Queries

Use Flow for automatic UI updates:

```kotlin
// ViewModel
val featuredCollections: StateFlow<List<DeadCollectionEntity>> =
    collectionsDao.getFeaturedCollectionsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

// UI updates automatically when collections change (rare - only on reimport)
```

---

## Example Collections

**Official Release Series**:
- Dick's Picks (36 volumes)
- Dave's Picks (ongoing)
- Road Trips (multiple volumes)
- Download Series

**Era-Based**:
- The Acid Tests (1965-1966)
- Primal Dead (1966-1968)
- Europe '72
- Spring 1977
- Brent Era (1979-1990)

**Venue-Based**:
- Fillmore East Shows
- Winterland Arena Shows
- Red Rocks Shows

**Special Events**:
- New Year's Eve Shows
- Halloween Shows
- Festival Appearances

---

## Code Locations

### Android

- **Entity**: `androidApp/v2/core/database/src/main/java/com/deadly/v2/core/database/entities/DeadCollectionEntity.kt:21`
- **DAO**: `androidApp/v2/core/database/src/main/java/com/deadly/v2/core/database/dao/CollectionsDao.kt:19`

### iOS

TBD (to be implemented)

---

## Implementation Notes

### Validating Show References

During import, verify all show IDs exist:

```kotlin
suspend fun importCollections(collections: List<CollectionJson>) {
    collections.forEach { collectionJson ->
        // Validate all shows exist
        val invalidShows = collectionJson.shows
            .filter { showDao.getShowById(it.showId) == null }

        if (invalidShows.isNotEmpty()) {
            Log.warn("Collection ${collectionJson.id} references non-existent shows: $invalidShows")
        }

        // Insert collection (even if some shows missing - graceful degradation)
        val entity = DeadCollectionEntity(
            id = collectionJson.id,
            name = collectionJson.name,
            description = collectionJson.description,
            tagsJson = Json.encodeToString(collectionJson.tags),
            showIdsJson = Json.encodeToString(collectionJson.shows.map { it.showId }),
            totalShows = collectionJson.shows.size,
            primaryTag = collectionJson.tags.firstOrNull(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        collectionsDao.insertCollection(entity)
    }
}
```

---

### Handling Missing Shows

When displaying collection shows, handle missing shows gracefully:

```kotlin
suspend fun getShowsInCollection(collectionId: String): List<Show> {
    val collection = collectionsDao.getCollectionById(collectionId) ?: return emptyList()
    val showIds = Json.decodeFromString<List<String>>(collection.showIdsJson)

    // getShowsByIds returns only existing shows (missing IDs omitted)
    val shows = showDao.getShowsByIds(showIds)

    if (shows.size < showIds.size) {
        Log.warn("Collection $collectionId missing ${showIds.size - shows.size} shows")
    }

    return shows
}
```

---

### Updating Collections

Collections are typically read-only, but if updating:

```kotlin
suspend fun updateCollectionShows(collectionId: String, newShowIds: List<String>) {
    val collection = collectionsDao.getCollectionById(collectionId) ?: return

    val updatedCollection = collection.copy(
        showIdsJson = Json.encodeToString(newShowIds),
        totalShows = newShowIds.size,
        updatedAt = System.currentTimeMillis()
    )

    collectionsDao.updateCollection(updatedCollection)
}
```

---

## See Also

- [ShowEntity](shows.md) - Shows referenced by collections
- [Design Philosophy](../design-philosophy.md#2-json-storage-setlists-lineups-collections) - Why JSON storage
- [Data Sources](../data-sources.md#collections) - Where collection data comes from
