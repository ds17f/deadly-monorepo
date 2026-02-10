# Home

Landing screen featuring recent shows, today in history, and featured collections.

---

## Overview

The Home screen is the app's landing page, providing quick access to personalized content and discovery features. It surfaces recent listening, historical context (shows that happened on today's date), and featured collections to encourage exploration.

**Purpose**: Personalized entry point for discovery and quick access

**Key Responsibilities**:
- Display recent shows for quick re-access
- Show "Today in Grateful Dead History" (shows on this date)
- Feature curated collections
- Provide navigation to all major features
- Encourage content discovery

---

## User Experience

### Screen Layout

**Vertical Scroll Structure**:
1. **Recent Shows Grid** (top) - 2x4 grid of recently played shows
2. **Today in Grateful Dead History** - Horizontal scrolling show cards
3. **Featured Collections** - Horizontal scrolling collection cards

**Spacing**: 24dp between sections for clear visual separation

---

### Recent Shows Grid

**Layout**: 2x4 grid (2 columns, 4 rows = 8 shows)

**Show Cards Display**:
- Date (formatted: "May 8, 1977")
- Venue name
- Location (city, state)

**Interaction**:
- Tap show → Navigate to show detail
- Long press → Context menu (future)

**Empty State**: Section hidden if no recent shows

**Purpose**: Quick access to continue listening where user left off

---

### Today in Grateful Dead History

**Concept**: Shows that happened on today's calendar date (any year)

**Example**: If today is May 8, show all shows from May 8 (1965, 1977, 1990, etc.)

**Display**:
- Section header: "Today In Grateful Dead History"
- Horizontal scrolling cards
- Each card shows:
    - Date (with year: "May 8, 1977")
    - Venue name
    - Location

**Interaction**:
- Swipe to scroll through shows
- Tap show → Navigate to show detail

**Educational Value**: Learn about historical shows and discover new content

---

### Featured Collections

**Display**:
- Section header: "Featured Collections"
- Horizontal scrolling cards
- Each card shows:
    - Collection name
    - Show count (e.g., "22 shows")

**Interaction**:
- Swipe to scroll through collections
- Tap collection → Navigate to collections screen with pre-selection

**Purpose**: Surface curated content for guided discovery

---

## Key Behaviors

### Dynamic Content

**Recent Shows**: Updates automatically as user listens to new shows

**Today in History**: Changes daily based on current date

**Featured Collections**: Static or admin-configured subset of all collections

**Reactive**: All sections use reactive state flows for automatic updates

---

### Empty States

**Recent Shows Empty**: Section hidden entirely (no "Get started" message)

**Today in History Empty**: Rare (most dates have at least one show), but would display "No shows on this date"

**Featured Collections Empty**: Display loading indicator or error

---

### Navigation

**From Home**:
- Show card → Show Detail
- Collection card → Collections screen
- Search button → Search screen
- Library button → Library screen

**To Home**:
- Bottom navigation bar (home icon)
- Back from other screens (if home is root)

---

## Integration with Other Features

### Recent History Integration

**Data Source**: Recent shows section powered by RecentShowsService

**Limit**: Displays 8 most recent shows (2x4 grid)

**Update**: Automatically refreshes when new shows played

---

### Collections Integration

**Data Source**: Featured collections from CollectionsService

**Subset**: Shows featured subset, not all collections

**Navigation**: Tapping collection navigates to full collections screen with pre-selection

---

### Show Detail Integration

**Navigation**: All show cards navigate to show detail

**Context**: Provides quick access to show info and playback

---

### Search Integration

**Entry Point**: Search button/icon on home screen

**Use Case**: User wants specific show, not suggested content

---

## Design Decisions

### Why Recent Shows Grid (Not List)?

**Decision**: 2x4 grid instead of vertical list

**Rationale**:
- More shows visible without scrolling
- Compact layout saves vertical space
- Visual scanning easier with grid
- Matches iOS Music app pattern

---

### Why "Today in History"?

**Decision**: Include historical shows from today's date

**Rationale**:
- Educational value (learn about Dead history)
- Serendipitous discovery (user wouldn't search for these)
- Emotional connection (anniversaries feel special)
- Content variety (different from recent shows)

---

### Why Three Sections, Not More?

**Decision**: Limit to 3 content sections

**Rationale**:
- Prevents overwhelming user with choices
- Each section serves distinct purpose (recency, history, curation)
- More sections would require excessive scrolling
- Can expand later if needed

---

### Why Hide Empty Recent Shows?

**Decision**: Don't show "Get started" message for empty recent

**Rationale**:
- New users haven't established patterns yet
- Other sections (today, collections) provide entry points
- Reduces visual clutter
- Prevents feeling like app is "empty"

---

## State Model

### UI State

**HomeUiState**:
```kotlin
data class HomeUiState(
    val isLoading: Boolean,
    val error: String?,
    val homeContent: HomeContent
)
```

**HomeContent**:
```kotlin
data class HomeContent(
    val recentShows: List<Show>,          // 8 shows max
    val todayInHistory: List<Show>,       // Variable count
    val featuredCollections: List<DeadCollection>  // ~5-10 collections
)
```

---

## Error Handling

### Content Load Failed

**Scenario**: Network error, database error

**UI Response**:
- Display error message
- Show retry button
- Log error for debugging

**Graceful Degradation**: If one section fails, others may still work

---

### Empty Content

**Scenario**: No recent shows, no shows today, no collections

**UI Response**:
- Recent: Hide section
- Today: Show "No shows on this date" (rare)
- Collections: Show loading or error state

---

## Technical Considerations

### Data Freshness

**Challenge**: Keep content up-to-date

**Approach**:
- Recent shows: Reactive StateFlow (auto-updates)
- Today in history: Query daily based on current date
- Featured collections: Static (refresh on app launch)

---

### Performance

**Challenge**: Multiple data sources on landing screen

**Approach**:
- Parallel loading (don't block on one source)
- Limit recent shows to 8 (small query)
- Today in history pre-filtered by date (fast)
- Featured collections subset (not all collections)

---

### Scroll Performance

**Challenge**: Many show cards may impact scroll

**Approach**:
- LazyColumn for efficient rendering
- Only render visible items
- Horizontal scrolling uses LazyRow

---

## Code References

**Android Implementation**:
- Screen: `androidApp/v2/feature/home/src/main/java/com/deadly/v2/feature/home/screens/main/HomeScreen.kt:32`
- ViewModel: `androidApp/v2/feature/home/src/main/java/com/deadly/v2/feature/home/screens/main/HomeViewModel.kt:1`

---

## See Also

- [Recent History](recent.md) - Recent shows data source
- [Collections](collections.md) - Featured collections
- [Show Detail](show-detail.md) - Navigation target from all cards
- [Search](search.md) - Alternative discovery method
