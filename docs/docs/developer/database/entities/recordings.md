# RecordingEntity (recordings)

Represents individual audio recordings of shows from Archive.org. Multiple recordings typically exist per show with varying quality levels (soundboard, audience, matrix, etc.).

---

## Purpose

`RecordingEntity` stores quality metrics and provenance information for recordings:

- **Quality ratings** - User ratings and review counts from Archive.org
- **Source type** - Recording type (soundboard, audience, etc.)
- **Provenance** - Taper, source equipment, lineage information
- **Show linkage** - Foreign key to parent show

**Why Separate Table**: Each show has multiple recordings (average 6-8, some have 20+). Separate table allows efficient quality-based queries.

---

## Schema

### Table Definition

```sql
CREATE TABLE recordings (
    identifier TEXT PRIMARY KEY,
    show_id TEXT NOT NULL,

    -- Source information
    source_type TEXT,

    -- Quality metrics
    rating REAL NOT NULL DEFAULT 0.0,
    raw_rating REAL NOT NULL DEFAULT 0.0,
    review_count INTEGER NOT NULL DEFAULT 0,
    confidence REAL NOT NULL DEFAULT 0.0,
    high_ratings INTEGER NOT NULL DEFAULT 0,
    low_ratings INTEGER NOT NULL DEFAULT 0,

    -- Provenance
    taper TEXT,
    source TEXT,
    lineage TEXT,
    source_type_string TEXT,

    -- Metadata
    collection_timestamp INTEGER NOT NULL,

    FOREIGN KEY (show_id) REFERENCES shows(show_id) ON DELETE CASCADE
);
```

### Indexes

```sql
CREATE INDEX idx_recordings_show_id ON recordings(show_id);
CREATE INDEX idx_recordings_source_type ON recordings(source_type);
CREATE INDEX idx_recordings_rating ON recordings(rating);
CREATE INDEX idx_recordings_show_id_rating ON recordings(show_id, rating);
```

---

## Fields

### Primary Key

#### `identifier` (TEXT, PRIMARY KEY)

Archive.org unique identifier.

**Format**: `{band}{date}.{source}.{taper}.{version}.{format}`

**Example**: `"gd1977-05-08.sbd.miller.97375.sbeok.flac16"`

**Breakdown**:
- `gd` - Grateful Dead
- `1977-05-08` - Show date
- `sbd` - Soundboard
- `miller` - Taper/source
- `97375` - Archive.org upload ID
- `sbeok` - Processing info
- `flac16` - Format

**Globally Unique**: Archive.org guarantees uniqueness

---

### Foreign Key

#### `show_id` (TEXT, NOT NULL, INDEXED, FK)

References parent show.

**Example**: `"1977-05-08-barton-hall-cornell-u-ithaca-ny-usa"`

**Foreign Key**: → `shows.show_id` with `CASCADE DELETE`

**Indexed**: Primary access pattern is "get recordings for show"

---

### Source Information

#### `source_type` (TEXT, NULLABLE, INDEXED)

Recording source type.

**Values**: `"SBD"`, `"AUD"`, `"FM"`, `"MATRIX"`, `"REMASTER"`, `"UNKNOWN"`, `null`

**Definitions**:
- **SBD** (Soundboard) - Direct from mixing board, highest quality
- **AUD** (Audience) - Microphones in audience, variable quality
- **FM** (FM Broadcast) - Radio broadcast recording
- **MATRIX** - Mix of soundboard and audience sources
- **REMASTER** - Remastered/enhanced version
- **UNKNOWN** - Source type unclear

**Indexed**: Filter by source type ("show me only soundboards")

**Usage**:
- Display badge to user (SBD = gold star)
- Filter recordings by quality preference
- Sort recordings (SBD typically higher priority)

---

### Quality Metrics

#### `rating` (REAL, NOT NULL, DEFAULT 0.0, INDEXED)

Weighted rating for internal ranking (0.0-5.0).

**Example**: `4.75`

**Algorithm**: Weighted average accounting for confidence
- More reviews = higher confidence
- Outlier reviews weighted less
- Used for "best recording" determination

**Indexed**: Sort recordings by quality

**Usage**: Internal ranking, "best recording" selection

---

#### `raw_rating` (REAL, NOT NULL, DEFAULT 0.0)

Simple average rating for display (0.0-5.0).

**Example**: `4.9`

**Algorithm**: Simple mean of all review ratings

**Usage**: Display star rating to user

---

#### `review_count` (INTEGER, NOT NULL, DEFAULT 0)

Number of user reviews on Archive.org.

**Example**: `156`

**Usage**:
- Display to user: "156 reviews"
- Confidence indicator (more reviews = more reliable rating)
- Filter "highly reviewed" recordings

---

#### `confidence` (REAL, NOT NULL, DEFAULT 0.0)

Rating confidence score (0.0-1.0).

**Example**: `0.95`

**Algorithm**: Based on review count and rating distribution
- More reviews = higher confidence
- Consistent ratings = higher confidence
- Few reviews or polarized ratings = lower confidence

**Usage**: Weight ratings in "best recording" algorithm

---

#### `high_ratings` (INTEGER, NOT NULL, DEFAULT 0)

Count of 4-5★ reviews.

**Example**: `142`

**Usage**: Rating distribution display, quality indicator

---

#### `low_ratings` (INTEGER, NOT NULL, DEFAULT 0)

Count of 1-2★ reviews.

**Example**: `3`

**Usage**: Rating distribution display, controversy indicator

---

### Provenance

#### `taper` (TEXT, NULLABLE)

Person who recorded the show.

**Example**: `"Betty Cantor-Jackson"`, `"Dan Healy"`, `"Miller"`

**Usage**:
- Display to user (collectors care about tapers)
- Search by taper
- Credit original recorder

**Notable Tapers**:
- Betty Cantor-Jackson (legendary GD soundboard engineer)
- Dan Healy (GD sound engineer)
- Miller (prolific taper)

---

#### `source` (TEXT, NULLABLE)

Source equipment chain.

**Example**: `"SBD > Reel > DAT > CD > EAC > FLAC"`

**Format**: Freeform text, typically `device > device > ...`

**Usage**:
- Display to audiophiles
- Quality assessment
- Recording history

---

#### `lineage` (TEXT, NULLABLE)

Digital transfer chain and processing history.

**Example**: `"Digital transfer by Miller using Sonic Solutions. Remastered by Smith 2015."`

**Format**: Freeform text

**Usage**:
- Display to audiophiles
- Provenance documentation
- Processing history

---

#### `source_type_string` (TEXT, NULLABLE)

Raw source type string from Archive.org data.

**Example**: `"Soundboard"`, `"Audience Recording"`

**Why Separate from `source_type`**: Original text preserved, `source_type` is normalized enum

**Usage**: Fallback display if `source_type` is missing

---

### Metadata

#### `collection_timestamp` (INTEGER, NOT NULL)

Timestamp when recording metadata was collected.

**Format**: Unix timestamp (milliseconds)

**Example**: `1678901234000`

**Default**: `System.currentTimeMillis()` at import

**Usage**: Track data freshness, debugging

---

## Relationships

### To ShowEntity (N:1)

```sql
FOREIGN KEY (show_id) REFERENCES shows(show_id) ON DELETE CASCADE
```

**Cardinality**: Many recordings belong to one show

**Cascade Behavior**: If show deleted, all recordings deleted (orphaned recordings meaningless)

**Query Pattern**:
```sql
SELECT * FROM recordings WHERE show_id = '1977-05-08-...' ORDER BY rating DESC;
```

---

## Common Queries

### Get All Recordings for Show (Quality Sorted)

```sql
SELECT * FROM recordings
WHERE show_id = '1977-05-08-barton-hall-cornell-u-ithaca-ny-usa'
ORDER BY rating DESC;
```

**Performance**: Fast via `(show_id, rating)` composite index

**Usage**: Show detail page - list recordings by quality

---

### Get Best Recording for Show

```sql
SELECT * FROM recordings
WHERE show_id = '1977-05-08-barton-hall-cornell-u-ithaca-ny-usa'
ORDER BY rating DESC
LIMIT 1;
```

**Performance**: Fast via `(show_id, rating)` composite index

**Usage**: Auto-select best recording on show load

---

### Filter by Source Type

```sql
SELECT * FROM recordings
WHERE show_id = '1977-05-08-...' AND source_type = 'SBD'
ORDER BY rating DESC;
```

**Performance**: Fast via indexes

**Usage**: "Show me only soundboards"

---

### Get Top Rated Recordings (Global)

```sql
SELECT * FROM recordings
WHERE rating > 4.0 AND review_count >= 10
ORDER BY rating DESC, review_count DESC
LIMIT 50;
```

**Performance**: Table scan (no global rating index)

**Usage**: "Best recordings ever" discovery feature

---

### Recording Statistics by Source Type

```sql
SELECT
    source_type,
    COUNT(*) as count,
    AVG(rating) as avg_rating,
    AVG(review_count) as avg_reviews
FROM recordings
WHERE source_type IS NOT NULL
GROUP BY source_type
ORDER BY count DESC;
```

**Performance**: Full table scan with GROUP BY

**Usage**: Analytics, debugging

---

## Code Locations

### Android

- **Entity**: `androidApp/v2/core/database/src/main/java/com/deadly/v2/core/database/entities/RecordingEntity.kt:26`
- **DAO**: `androidApp/v2/core/database/src/main/java/com/deadly/v2/core/database/dao/RecordingDao.kt:11`

### iOS

TBD (to be implemented)

---

## Implementation Notes

### Best Recording Algorithm

The "best recording" for a show is determined by:

1. **Source type priority**: SBD > MATRIX > FM > REMASTER > AUD
2. **Rating**: Higher `rating` (weighted) preferred
3. **Confidence**: Higher `confidence` breaks ties
4. **Review count**: More reviews breaks ties

**Implementation**:
```kotlin
val bestRecording = recordings
    .sortedWith(
        compareByDescending<Recording> { it.sourceTypePriority() }
        .thenByDescending { it.rating }
        .thenByDescending { it.confidence }
        .thenByDescending { it.reviewCount }
    )
    .firstOrNull()
```

---

### Rating Confidence

Confidence is calculated during metadata package compilation (not at runtime):

```
confidence = min(review_count / 20.0, 1.0) * (1.0 - rating_variance)
```

- More reviews increase confidence (caps at 20 reviews)
- Lower variance (consistent ratings) increases confidence

---

### Source Type Normalization

Archive.org uses inconsistent source type strings. Metadata package normalizes to enum:

```kotlin
fun normalizeSourceType(raw: String?): String {
    return when {
        raw?.contains("soundboard", ignoreCase = true) == true -> "SBD"
        raw?.contains("audience", ignoreCase = true) == true -> "AUD"
        raw?.contains("matrix", ignoreCase = true) == true -> "MATRIX"
        raw?.contains("fm", ignoreCase = true) == true -> "FM"
        raw?.contains("remaster", ignoreCase = true) == true -> "REMASTER"
        else -> "UNKNOWN"
    }
}
```

---

## See Also

- [ShowEntity](shows.md) - Parent show entity
- [Design Philosophy](../design-philosophy.md) - Why separate table
- [Data Sources](../data-sources.md) - Where recording data comes from
