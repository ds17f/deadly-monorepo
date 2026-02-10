# Collections

Curated collections of Grateful Dead shows organized by theme, era, or significance.

---

## Overview

Collections are pre-curated sets of shows grouped by meaningful themes (e.g., "Spring 1977 Tour", "Dick's Picks Vol. 1", "Europe '72"). They provide an editorial layer on top of the raw show database, helping users discover notable shows and understand their context.

**Purpose**: Guided discovery through curated show collections

**Key Responsibilities**:
- Display available collections with filtering by tags
- Show collection details (name, description, tags, show count)
- Browse shows within a collection
- Navigate between collections via carousel
- Filter collections by hierarchical tags
- Navigate to show detail from collection

---

## User Experience

### Collections Screen Layout

**Main Components**:
1. **Hierarchical Tag Filter** (top) - Filter collections by official/guest/era tags
2. **Collections Carousel** (middle) - Horizontally scrollable collection cards
3. **Navigation Slider** - iPod-style position slider for carousel
4. **Shows List** (bottom) - Shows in currently selected collection

**Scrollable**: Entire screen scrolls vertically, carousel scrolls horizontally

---

### Collection Cards

**Card Display**:
- Collection name (e.g., "Spring 1977 Tour")
- Show count (e.g., "22 shows")
- Tags (e.g., "Official", "Era: 70s")
- Visual design with elevation

**Interaction**:
- Tap collection → Scroll to shows section
- Swipe carousel → Navigate between collections
- Use slider → Jump to specific collection

---

### Hierarchical Tag Filtering

**First Level - Collection Types**:
- **Official**: Archive.org curated collections
- **Guest**: Guest curator collections
- **Era**: Decade/period-based collections

**Second Level - Subcategories** (under Official):
- **Dick's Picks**: Official Dick's Picks series
- **Dave's Picks**: Official Dave's Picks series
- **Road Trips**: Road Trips series
- **Download Series**: Download Series

**Behavior**:
- Select type → Shows collections of that type
- Select subcategory → Shows specific series
- Clear filter → Shows all collections

**Dynamic Count**: Filter header shows count ("Official Collections (15)")

---

### Collection Carousel

**Display**:
- Large cards showing collection info
- Current selection highlighted
- Horizontal paging (one collection visible at a time)
- Smooth swipe transitions

**Navigation**:
- Swipe left/right → Next/previous collection
- Tap card → Scroll to shows list
- Use slider → Jump to position

**Sync with Shows**: Selecting collection updates shows list below

---

### Navigation Slider

**iPod-Style Control**:
- Horizontal slider below carousel
- Shows current position (e.g., "5 / 23")
- Drag to jump to collection
- Visual feedback

**Purpose**: Quick navigation through many collections without repeated swiping

---

### Shows List

**Display**:
- Header: "Shows (N)" where N is count
- List of show cards:
    - Date
    - Venue
    - Location
    - Rating (if available)

**Interaction**:
- Tap show → Navigate to show detail
- Shows are from currently selected collection

**Empty State**: If collection has no shows, displays "No shows available for this collection"

---

## Key Behaviors

### Collection Selection

**Entry Point**:
- Default: First collection in filtered list auto-selected
- From Navigation: If `collectionId` provided, pre-select that collection

**Selection Flow**:
1. User selects collection (tap, swipe, slider)
2. Collection card updates visually
3. Shows list updates with collection's shows
4. Carousel syncs to position

---

### Filtering

**Tag-Based Filtering**:
- Collections have tags (e.g., "official", "dicks-picks", "era-70s")
- Filter tree matches these tags
- Filtered collections update carousel
- Shows list updates for new selection

**Dynamic Updates**: Changing filter immediately updates visible collections

---

### Navigation from Show Detail

**Scenario**: User viewing show detail, sees it's in a collection

**Flow**:
1. Show detail → Tap collection name
2. Navigate to Collections screen with `collectionId`
3. Collections screen pre-selects that collection
4. User sees collection context and other shows

**Purpose**: Discover related shows from same collection

---

## Integration with Other Features

### Show Detail Integration

**From Collections**: Tap show in list → Navigate to show detail

**To Collections**: Show detail displays collections badge → Navigate to collections with pre-selection

**Bidirectional**: Easy navigation between collection context and show detail

---

### Home Screen Integration

**Featured Collections**: Home screen may display featured collections carousel

**Quick Access**: Direct link to full collections screen

---

### Search Integration

**Relationship**: Collections complement search

**Difference**:
- Search: User-driven query for specific criteria
- Collections: Editorial guidance for discovery

**Use Case**: "I don't know what to listen to" → Browse collections

---

## Design Decisions

### Why Curated Collections?

**Decision**: Provide pre-made collections instead of only user-created playlists

**Rationale**:
- New users need guidance (2,400+ shows is overwhelming)
- Captures expert knowledge (Dick's Picks are historically significant)
- Educational value (learn about eras and notable tours)
- Reduces decision fatigue

---

### Why Carousel Instead of Grid?

**Decision**: Horizontal carousel with one collection visible at a time

**Rationale**:
- Focus attention on one collection
- Natural swipe interaction
- More space for collection details
- Matches music app patterns (album browsing)

---

### Why Hierarchical Tag Filter?

**Decision**: Two-level hierarchy instead of flat tag list

**Rationale**:
- Collections have natural hierarchy (Official → Dick's Picks)
- Too many flat tags would be overwhelming
- Matches user mental model
- Progressive disclosure (type first, then subcategory)

---

### Why Shows List Below Carousel?

**Decision**: Vertically scroll to see shows, don't use tab/modal

**Rationale**:
- Natural scroll-down gesture
- Keeps collection context visible while browsing shows
- No context switching (modals/tabs)
- Can scroll back to carousel to switch collections

---

## State Model

### UI State

**Core State**:
- `isLoading: Boolean` - Initial load
- `error: String?` - Error message
- `searchQuery: String` - Search filter (future)

**Collections State**:
- `featuredCollections: List<DeadCollection>` - Featured subset
- `filteredCollections: List<DeadCollection>` - Filtered by tags
- `selectedCollection: DeadCollection?` - Currently selected
- `selectedCollectionId: String?` - ID of selection
- `selectedCollectionIndex: Int` - Position in filtered list

**Filter State**:
- `filterPath: FilterPath` - Hierarchical filter selection

---

### Collection Model

**DeadCollection**:
- `id: String` - Unique identifier
- `name: String` - Collection name
- `description: String?` - Optional description
- `tags: List<String>` - Tag list (e.g., ["official", "dicks-picks"])
- `shows: List<Show>` - Shows in collection
- `showCountText: String` - Formatted count ("22 shows")

**Show** (in collection):
- `id: String` - Show ID
- `date: String` - Show date
- `venue: String` - Venue name
- `location: String` - City, state
- `rating: Float?` - Average rating

---

## Error Handling

### Collections Load Failed

**Scenario**: Network error, database error

**UI Response**:
- Display error message
- Show retry button
- Log error for debugging

---

### Empty Filtered Results

**Scenario**: Filter selection yields no collections

**UI Response**:
- Display "No collections match your filter" message
- Suggest clearing filter
- Show all collections button

---

## Technical Considerations

### Carousel Sync

**Challenge**: Keep carousel position, selected collection, and shows list in sync

**Approach**:
- Carousel swipe → Update selected collection
- Filter change → Reset to first collection
- Direct selection → Animate carousel to position

---

### Large Collection Count

**Challenge**: Many collections (50+) may impact carousel performance

**Approach**:
- Lazy loading in carousel
- Only render visible + adjacent cards
- Efficient state updates

---

### Filtering Performance

**Challenge**: Re-filtering on every filter change

**Approach**:
- Tag matching is fast (list contains check)
- Filtering done in memory (collections already loaded)
- Reactive flows prevent unnecessary recalculations

---

## Code References

**Android Implementation**:
- Screen: `androidApp/v2/feature/collections/src/main/java/com/deadly/v2/feature/collections/screens/main/CollectionsScreen.kt:44`
- ViewModel: `androidApp/v2/feature/collections/src/main/java/com/deadly/v2/feature/collections/screens/main/models/CollectionsViewModel.kt:1`

---

## See Also

- [Show Detail](show-detail.md) - Viewing individual shows
- [Home](home.md) - Featured collections integration
- [Search](search.md) - Alternative discovery method
- [Collections Entity](../database/entities/collections.md) - Database schema
