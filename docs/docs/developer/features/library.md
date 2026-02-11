# Library

Personal collection of favorite shows saved by the user.

---

## Overview

The Library feature allows users to save their favorite Grateful Dead shows for quick access. It acts as a personal bookmarking system, letting users curate their own collection from the 2,400+ available shows.

**Purpose**: Personalized favorites and bookmarks for easy access to preferred shows

**Key Responsibilities**:
- Add/remove shows from personal library
- Display saved shows with sorting/filtering
- Pin shows for priority display
- Track download status for offline listening
- Share shows with others

---

## User Experience

### Library Screen

**Main Display**:
- List or grid view of all saved shows
- Show card displaying: date, venue, location, rating, review count
- Status indicators (pinned, downloaded, downloading)
- Hierarchical decade/season filters
- Sort controls
- Display mode toggle (list/grid)

**Empty State**:
- Displayed when no shows in library
- Message: "Your Library is Empty"
- "Populate Test Data" button for development/testing

---

### Display Modes

**List View**:
- Vertical scrolling list
- Full show cards with all metadata
- More detailed information visible

**Grid View**:
- 3-column grid layout
- Compact show cards
- More shows visible at once
- Good for browsing large collections

**Toggle**: Switch between modes via display mode button in toolbar

---

### Adding Shows to Library

**From Show Detail**:
- Heart/bookmark icon to add to library
- Visual feedback confirming addition
- Icon changes to "filled" state when added

**Result**: Show appears in library immediately (reactive update)

---

### Removing Shows from Library

**Long Press Menu**: Long press on show card reveals action bottom sheet with remove option

**Confirmation**: Show removed immediately (no confirmation dialog)

**Result**: Show removed from library with reactive update

---

### Pinning Shows

**Purpose**: Mark favorite-of-favorites for priority display

**How to Pin**:
- Long press on show card
- Select "Pin" from action menu

**Visual Indicator**: Pinned shows display pin icon

**Sorting Priority**: Pinned shows always appear first, regardless of other sort criteria

**Unpinning**: Long press → "Unpin" option in action menu

---

### Filtering Shows

**Hierarchical Decade/Season Filter**:

**First Level - Decades**:
- 1960s
- 1970s
- 1980s
- 1990s

**Second Level - Seasons** (within selected decade):
- Spring (March, April, May)
- Summer (June, July, August)
- Fall (September, October, November)
- Winter (December, January, February)

**Behavior**:
- Select decade → Shows only shows from that decade
- Select season within decade → Shows only shows from that decade and season
- Clear filter → Shows all library shows

---

### Sorting Shows

**Sort Options**:
- **Date of Show**: Chronological order (ascending/descending)
- **Date Added**: When show was added to library (newest/oldest first)
- **Venue**: Alphabetical by venue name (A-Z/Z-A)
- **Rating**: Archive.org average rating (highest/lowest)

**Pin Priority**: Pinned shows always appear first, regardless of sort option

**Example**: Sorting by "Date of Show (Ascending)" with 2 pinned shows:
1. Pinned shows (sorted by show date ascending)
2. Unpinned shows (sorted by show date ascending)

**Sort Control**: Button opens bottom sheet with sort options

---

### Long Press Actions

**Trigger**: Long press on any show card

**Actions Available**:
- **Share Show**: Open share sheet with show link
- **Remove from Library**: Delete show from library
- **Download Show**: Download for offline playback
- **Remove Download**: Delete downloaded files
- **Pin Show**: Pin to top of library
- **Unpin Show**: Remove pin (only shown if already pinned)

**Dismiss**: Tap outside bottom sheet or select action

---

## Key Behaviors

### Real-Time Updates

**Reactive State**: Library updates automatically when changes occur

**Synchronization**:
- Add show → Appears immediately in library
- Remove show → Disappears immediately from library
- Pin show → Moves to top of list immediately
- Download complete → Status indicator updates

**No Manual Refresh**: Changes propagate automatically via reactive flows

---

### Denormalization

**Database Pattern**: Library status stored in two places:

1. **library_shows table**: Primary storage for library membership
2. **shows.is_in_library field**: Denormalized flag for fast queries

**Why Both**: Eliminates JOIN queries when checking library status

**Consistency**: Both fields updated atomically in transaction

**Reference**: See [library-shows.md](../database/entities/library-shows.md#denormalization-in-showentity)

---

### Download Management

**Download States**:
- **Not Downloaded**: Default state
- **Downloading**: Progress indicator shows percentage
- **Downloaded**: Checkmark icon, available offline
- **Download Failed**: Error icon, retry option

**Download Actions**:
- Download show for offline playback (via long press menu)
- Remove download to free space (via long press menu)

**Storage**: Downloads stored in device storage, tracked in database

---

## Integration with Other Features

### Show Detail Integration

**Library Status Indicator**:
- Heart icon shows if show is in library
- Tapping icon toggles library membership
- Visual feedback on state change

**Quick Actions**:
- Add to library (if not already)
- Remove from library (if already added)

---

### Search Integration

**Library Indicator**: Search results show which shows are in library

**Quick Add**: Add to library directly from search results

---

### Player Integration

**Now Playing Context**: Player can show if currently playing show is in library

**Quick Add**: Add currently playing show to library from player screen

---

### Recent History Integration

**Relationship**: Library and Recent History are separate features

**Difference**:
- Library: Manually curated favorites (explicit add action)
- Recent History: Automatically tracked plays (implicit from listening)

**Overlap**: Users often add recently played shows to library

---

### Home Screen Integration

**Library Section**: Home screen may display recently added library shows

**Quick Access**: Direct link to full library screen

---

## Design Decisions

### Why Manual Bookmarking?

**Decision**: Require explicit add action instead of auto-adding played shows

**Rationale**:
- Library is curated favorites, not history
- Users want control over what appears in library
- Prevents library from becoming cluttered with one-time listens
- Recent History serves the auto-tracking use case

---

### Why Pinning?

**Decision**: Support pinning in addition to favorites

**Rationale**:
- Users have favorites-of-favorites (classic shows they return to frequently)
- Pinning provides quick access to most-played shows
- Two-tier system (library + pins) is intuitive
- Avoids overwhelming users with 100+ show library

---

### Why Hierarchical Decade/Season Filter?

**Decision**: Use decade → season hierarchy instead of flat year list

**Rationale**:
- Grateful Dead touring patterns align with decades and seasons
- Easier to browse than scrolling through 30+ years
- Seasons capture tour patterns (spring tours, summer festivals, fall runs)
- Matches how Deadheads think about eras ("Spring '77", "Fall '89")

---

### Why Pin Priority in Sorting?

**Decision**: Pinned shows always appear first, regardless of sort order

**Rationale**:
- Pins are for quick access to most important shows
- If pins were mixed in, they'd be harder to find
- Consistent with other apps (pinned emails, pinned messages)
- Users can still sort pinned shows among themselves

---

### Why Long Press for Actions?

**Decision**: Use long press menu instead of swipe gestures

**Rationale**:
- Long press is discoverable (common mobile pattern)
- Allows multiple actions in single menu
- Avoids accidental deletions from swipe
- Consistent with Material Design patterns

---

### Why Denormalization?

**Decision**: Store library status in both library_shows table and shows.is_in_library field

**Rationale**:
- Eliminates expensive JOIN queries
- Fast library status checks (no JOIN needed)
- Acceptable data duplication for performance
- Atomic updates ensure consistency

**Reference**: See [design-philosophy.md](../database/design-philosophy.md#1-denormalization-venues-in-shows)

---

## State Model

### UI State

**Core State**:
- `isLoading: Boolean` - Initial library load
- `error: String?` - Error message if load failed
- `shows: List<LibraryShowViewModel>` - Library shows

**LibraryShowViewModel**:
- `showId: String`
- `date: String` - Show date (YYYY-MM-DD)
- `displayDate: String` - Formatted date ("May 8, 1977")
- `venue: String` - Venue name
- `location: String` - City, state
- `rating: Float?` - Average rating (nullable)
- `reviewCount: Int` - Number of reviews
- `addedToLibraryAt: Long` - Timestamp when added
- `isPinned: Boolean` - Pinned status
- `downloadStatus: LibraryDownloadStatus` - Download state
- `isDownloaded: Boolean` - Fully downloaded
- `isDownloading: Boolean` - Download in progress
- `libraryStatusDescription: String` - Human-readable status

---

### Sort and Filter State

**LibrarySortOption**:
- `DATE_OF_SHOW` - Sort by show date
- `DATE_ADDED` - Sort by when added to library
- `VENUE` - Sort alphabetically by venue
- `RATING` - Sort by Archive.org rating

**LibrarySortDirection**:
- `ASCENDING` - Low to high / A to Z / oldest to newest
- `DESCENDING` - High to low / Z to A / newest to oldest

**LibraryDisplayMode**:
- `LIST` - Vertical list with detailed cards
- `GRID` - 3-column grid with compact cards

**FilterPath**: Hierarchical filter selection (decade → season)

---

## Error Handling

### Library Load Failed

**Scenario**: Database error or initialization failure

**UI Response**:
- Display "Error Loading Library" message
- Show error details
- Provide "Retry" button
- Log error for debugging

---

### Add/Remove Failed

**Scenario**: Database write error

**UI Response**:
- Continue showing UI (optimistic update)
- Log error details
- May show toast on failure (future enhancement)

---

### Download Failed

**Scenario**: Network error or storage full

**UI Response**:
- Show error status on show card
- Provide retry option in long press menu
- Log error details

---

## Technical Considerations

### State Synchronization

**Challenge**: Keep library state in sync across database, service, and UI

**Approach**: Reactive flows propagate changes automatically

---

### Denormalization Consistency

**Challenge**: Keep library_shows and shows.is_in_library in sync

**Approach**: Update both fields atomically in database transaction

---

### Performance

**Challenge**: Large libraries (100+ shows) may be slow to render

**Approach**:
- Efficient list/grid rendering with Compose
- Database queries properly indexed
- Filter/sort operations use in-memory lists (already loaded)

---

## Code References

**Android Implementation**:
- Screen: `androidApp/v2/feature/library/src/main/java/com/deadly/v2/feature/library/screens/main/LibraryScreen.kt:41`
- ViewModel: `androidApp/v2/feature/library/src/main/java/com/deadly/v2/feature/library/screens/main/models/LibraryViewModel.kt:25`
- Service Interface: `androidApp/v2/core/api/library/src/main/java/com/deadly/v2/core/api/library/LibraryService.kt:13`

---

## See Also

- [Library Shows Entity](../database/entities/library-shows.md) - Database schema and design
- [Recent History](recent.md) - Automatic playback tracking
- [Collections](collections.md) - Curated show collections
- [Home](home.md) - Library integration on home screen
