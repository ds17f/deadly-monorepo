# LibraryShowEntity (library_shows)

Represents shows that users have added to their personal library. Tracks user-specific metadata like pin status and notes.

---

## Purpose

`LibraryShowEntity` stores user's saved shows with:

- **Library membership** - Which shows user has saved
- **Pin status** - Prioritized shows displayed first
- **Personal notes** - User's comments about the show
- **Timestamps** - When added, last accessed

**Why Separate Table**: User data separate from catalog data. Allows clean sync, backup, and clear separation of mutable user data from immutable catalog.

---

## Schema

### Table Definition

```sql
CREATE TABLE library_shows (
    show_id TEXT PRIMARY KEY,

    -- Library metadata
    added_to_library_at INTEGER NOT NULL,
    is_pinned INTEGER NOT NULL DEFAULT 0,
    library_notes TEXT,

    -- Future expansion
    custom_rating REAL,
    last_accessed_at INTEGER,
    tags TEXT,

    FOREIGN KEY (show_id) REFERENCES shows(show_id) ON DELETE CASCADE
);
```

### Indexes

```sql
CREATE UNIQUE INDEX idx_library_shows_show_id ON library_shows(show_id);
CREATE INDEX idx_library_shows_added_at ON library_shows(added_to_library_at);
CREATE INDEX idx_library_shows_is_pinned ON library_shows(is_pinned);
```

---

## Fields

### Primary Key

#### `show_id` (TEXT, PRIMARY KEY, FK, UNIQUE)

References parent show.

**Example**: `"1977-05-08-barton-hall-cornell-u-ithaca-ny-usa"`

**Foreign Key**: → `shows.show_id` with `CASCADE DELETE`

**Unique Index**: Ensures one library entry per show

**Usage**: Fast lookups, enforce one-entry-per-show constraint

---

### Library Metadata

#### `added_to_library_at` (INTEGER, NOT NULL, INDEXED)

Timestamp when user added show to library.

**Format**: Unix timestamp (milliseconds)

**Example**: `1678901234000`

**Indexed**: Sort library by "recently added"

**Usage**:
- Display "Added on March 15, 2023"
- Sort library chronologically
- Track user's library growth over time

---

#### `is_pinned` (INTEGER, NOT NULL, DEFAULT 0, INDEXED)

Boolean flag (0/1) for pinned shows.

**Values**: `0` (false), `1` (true)

**Indexed**: Filter pinned shows, sort pins first

**Usage**:
- Display pinned shows at top of library
- Quick access to favorite shows
- User prioritization

**UI Pattern**:
```
Library
├─ 📌 1977-05-08 Cornell (pinned)
├─ 📌 1972-08-27 Veneta (pinned)
├─ 1979-11-05 Nassau Coliseum
└─ 1974-05-19 Portland
```

---

#### `library_notes` (TEXT, NULLABLE)

User's personal notes about the show.

**Example**: `"Amazing Scarlet > Fire. First show I attended!"`

**Format**: Plain text (markdown could be supported in future)

**Nullable**: Most shows won't have notes

**Usage**:
- Display on show detail page
- Personal reminders
- Context for why show is in library

---

### Future Expansion Fields

These fields exist in schema but are not yet implemented in the app.

#### `custom_rating` (REAL, NULLABLE)

User's personal rating override (0.0-5.0).

**Example**: `5.0`

**Why**: User may disagree with Archive.org ratings

**Future Usage**:
- Display alongside public rating
- Sort library by personal rating
- Private rating vs public rating comparison

---

#### `last_accessed_at` (INTEGER, NULLABLE)

Timestamp when user last viewed/played this show.

**Format**: Unix timestamp (milliseconds)

**Future Usage**:
- "Recently viewed" in library
- Usage analytics
- Stale show detection ("haven't listened in 6 months")

---

#### `tags` (TEXT, NULLABLE)

Comma-separated user tags.

**Example**: `"favorite,1977,soundboard"`

**Format**: Comma-separated strings

**Future Usage**:
- Tag-based filtering
- Custom organization
- Smart playlists ("all shows tagged 'favorite'")

---

## Relationships

### To ShowEntity (1:1)

```sql
FOREIGN KEY (show_id) REFERENCES shows(show_id) ON DELETE CASCADE
```

**Cardinality**: Each library entry references one show, each show has max one library entry

**Cascade Behavior**: If show deleted from catalog (rare), library entry deleted (orphaned entry meaningless)

**Unique Constraint**: `show_id` is PRIMARY KEY and has UNIQUE index, enforcing one-entry-per-show

---

## Denormalization in ShowEntity

The `shows` table has denormalized fields for performance:

- `shows.is_in_library` - Mirrors presence in `library_shows` table
- `shows.library_added_at` - Mirrors `added_to_library_at` timestamp

**Why Denormalized**: Fast library membership checks without JOIN. See [Design Philosophy](../design-philosophy.md#1-denormalization-venues-in-shows).

**Sync Required**: When adding/removing from library, update both tables:

```kotlin
// Add to library
transaction {
    libraryDao.addToLibrary(LibraryShowEntity(...))
    showDao.updateLibraryStatus(showId, isInLibrary = true, addedAt = now)
}

// Remove from library
transaction {
    libraryDao.removeFromLibraryById(showId)
    showDao.updateLibraryStatus(showId, isInLibrary = false, addedAt = null)
}
```

---

## Common Queries

### Get All Library Shows (Pins First)

```sql
SELECT * FROM library_shows
ORDER BY is_pinned DESC, added_to_library_at DESC;
```

**Performance**: Fast via `is_pinned` and `added_to_library_at` indexes

**Usage**: Display library with pinned shows first, then chronological

---

### Check if Show is in Library

```sql
SELECT EXISTS(SELECT 1 FROM library_shows WHERE show_id = '1977-05-08-...');
```

**Performance**: O(1) via PRIMARY KEY index

**Usage**: Display heart icon (filled/unfilled)

---

### Get Pinned Shows

```sql
SELECT * FROM library_shows
WHERE is_pinned = 1
ORDER BY added_to_library_at DESC;
```

**Performance**: Fast via `is_pinned` index

**Usage**: Quick access widget, favorites section

---

### Count Library Size

```sql
SELECT COUNT(*) FROM library_shows;
```

**Performance**: Fast (simple count)

**Usage**: Display "42 shows in library"

---

### Recently Added Shows

```sql
SELECT * FROM library_shows
ORDER BY added_to_library_at DESC
LIMIT 10;
```

**Performance**: Fast via `added_to_library_at` index

**Usage**: "Recently added" section

---

## Common Operations

### Add to Library

```kotlin
suspend fun addToLibrary(showId: String) {
    val entity = LibraryShowEntity(
        showId = showId,
        addedToLibraryAt = System.currentTimeMillis(),
        isPinned = false,
        libraryNotes = null
    )
    libraryDao.addToLibrary(entity)

    // Sync denormalized flag
    showDao.updateLibraryStatus(showId, isInLibrary = true, addedAt = entity.addedToLibraryAt)
}
```

---

### Remove from Library

```kotlin
suspend fun removeFromLibrary(showId: String) {
    libraryDao.removeFromLibraryById(showId)

    // Sync denormalized flag
    showDao.updateLibraryStatus(showId, isInLibrary = false, addedAt = null)
}
```

---

### Toggle Pin

```kotlin
suspend fun togglePin(showId: String) {
    val entity = libraryDao.getLibraryShowById(showId) ?: return
    libraryDao.updatePinStatus(showId, !entity.isPinned)
}
```

---

### Update Notes

```kotlin
suspend fun updateNotes(showId: String, notes: String?) {
    libraryDao.updateLibraryNotes(showId, notes)
}
```

---

## Code Locations

### Android

- **Entity**: `androidApp/v2/core/database/src/main/java/com/deadly/v2/core/database/entities/LibraryShowEntity.kt:31`
- **DAO**: `androidApp/v2/core/database/src/main/java/com/deadly/v2/core/database/dao/LibraryDao.kt:19`

### iOS

TBD (to be implemented)

---

## Implementation Notes

### Transaction Safety

Always update both tables in a transaction to maintain consistency:

```kotlin
database.withTransaction {
    libraryDao.addToLibrary(entity)
    showDao.updateLibraryStatus(showId, isInLibrary = true, addedAt = entity.addedToLibraryAt)
}
```

If transaction fails, both updates roll back (no inconsistent state).

---

### Reactive Queries

Use Flow for automatic UI updates:

```kotlin
// ViewModel
val libraryShows: StateFlow<List<Show>> = libraryDao.getAllLibraryShowsFlow()
    .map { libraryEntities ->
        // Join with shows table for full data
        val showIds = libraryEntities.map { it.showId }
        showDao.getShowsByIds(showIds)
    }
    .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

// UI updates automatically when library changes
```

---

### Backup/Sync Strategy

Library data is user-specific and should be backed up:

**Export** (future):
```json
{
  "library": [
    {
      "show_id": "1977-05-08-...",
      "added_at": 1678901234000,
      "is_pinned": true,
      "notes": "Amazing show!"
    }
  ]
}
```

**Import** (future):
- Parse JSON
- Validate show_ids exist in catalog
- Insert into library_shows table
- Sync denormalized flags in shows table

---

## See Also

- [ShowEntity](shows.md) - Parent show entity
- [Design Philosophy](../design-philosophy.md) - Why separate table, why denormalization
- [RecentShowEntity](recent-shows.md) - Similar user data pattern
