# Show Detail

Comprehensive view of a single Grateful Dead show with recordings, tracks, and metadata.

---

## Overview

The Show Detail screen (internally called "Playlist" in Android code) displays comprehensive information about a specific Grateful Dead show, including available recordings, track listings, ratings/reviews, and actions like play, download, and share.

**Purpose**: Central hub for exploring and playing a specific show

**Key Responsibilities**:
- Display show metadata (date, venue, location)
- List all available recordings for the show
- Show track list with playback controls
- Display ratings and reviews
- Navigate between chronological shows
- Provide quick actions (play, download, add to library, share)
- Show which collections contain this show

**Note**: Android implementation uses module name `v2:feature:playlist` - should be renamed to `show-detail` in future refactor.

---

## User Experience

### Main Display

**Spotify-Style Layout**:
- Large album art at top
- Show information (date, venue, location)
- Navigation arrows (previous/next show chronologically)
- Interactive rating display
- Action buttons row
- Scrollable track list

---

### Show Information

**Displayed Metadata**:
- Show date (formatted: "May 8, 1977")
- Venue name ("Barton Hall")
- Location ("Ithaca, NY")
- Current recording identifier
- Average rating (stars)
- Number of reviews

**Navigation**:
- Previous show button (chronological)
- Next show button (chronological)
- Arrows always enabled for responsive UX

---

### Ratings and Reviews

**Interactive Rating Display**:
- Average rating (e.g., 4.8 stars)
- Total review count
- Tap to see full review details

**Review Details Sheet** (bottom sheet):
- Rating distribution histogram (1-5 stars)
- Individual reviews with:
    - Reviewer name
    - Star rating
    - Review text
    - Review date
- Loading state while fetching
- Error handling if load fails

---

### Action Buttons

**Primary Actions Row**:

1. **Library Toggle**
    - Heart icon (filled if in library, outline if not)
    - Add to or remove from library
    - Visual feedback on tap

2. **Download**
    - Download icon
    - Downloads show for offline playback
    - Shows download progress/status

3. **Setlist**
    - List icon
    - Opens setlist details bottom sheet
    - Shows song organization (Set 1, Set 2, Encore)

4. **Collections**
    - Collection/box icon
    - Shows which collections contain this show
    - Opens collections bottom sheet
    - Navigate to collection from sheet

5. **Menu**
    - Three-dot menu icon
    - Opens action menu bottom sheet
    - Additional options (share, choose recording)

6. **Play/Pause**
    - Large circular button
    - Starts playback of entire show
    - Shows loading spinner when loading
    - Shows pause icon when playing

---

### Track List

**Track Display**:
- Track number
- Song title
- Duration
- Download icon (per track)
- Play icon (per track)

**Track Actions**:
- Tap track → Play from that track
- Tap download icon → Download individual track

**Progressive Loading**: Track list loads after show metadata to improve perceived performance

---

### Recording Selection

**Choose Recording Button**: Opens recording selection bottom sheet

**Recording Options Display**:
- List of all available recordings for the show
- For each recording:
    - Recording identifier (e.g., "sbd.miller.97065")
    - Source type (Soundboard, Audience, Matrix)
    - Format (FLAC, MP3, etc.)
    - Avg rating
    - Number of reviews
    - File size
    - "Recommended" badge (if applicable)
    - "Default" badge (if user set as default)

**Actions**:
- Tap recording → Switch to that recording
- "Set as Default" button → Remember preference for this show
- "Reset to Recommended" button → Clear default, use recommended

**Result**: Switching recording reloads track list with new recording's tracks

---

### Collections Sheet

**Trigger**: Tap collections icon in action row

**Display**:
- List of collections containing this show
- For each collection:
    - Collection name
    - Collection tags
    - Total shows in collection

**Actions**:
- Tap collection → Navigate to collection detail with this show pre-selected

---

### Setlist Details

**Trigger**: Tap setlist icon in action row

**Display**:
- Organized by set (Set 1, Set 2, Encore, etc.)
- Song names in order
- Segues indicated (e.g., "Scarlet Begonias > Fire on the Mountain")
- Song timing if available

**Purpose**: See show structure and flow without cluttering main screen

---

### Menu Options

**Trigger**: Tap three-dot menu icon

**Options**:
- Share Show
- Choose Recording (opens recording selection sheet)
- Additional options (future)

---

## Key Behaviors

### Entry Points

**From Search Results**: Tap show → Navigate to show detail

**From Library**: Tap show → Navigate to show detail

**From Collections**: Tap show within collection → Navigate to show detail

**From Player**: Tap show context → Navigate to show detail for currently playing show

**From Recent History**: Tap recent show → Navigate to show detail

---

### Recording Selection Logic

**Default Behavior**:
1. Check if user has set a default recording for this show → Use that
2. Otherwise, use Archive.org recommended recording
3. Fall back to first available recording if no recommendation

**User Override**: User can set any recording as default for a show

**Reset**: User can reset to recommended recording, clearing their default preference

---

### Chronological Navigation

**Previous/Next Show Logic**:
- Navigate through all shows in chronological order by date
- Wraps around (last show → first show, first show → last show)
- Always enabled (no disabled state)

**Purpose**: Easy browsing through Dead history in order

---

### Playback Integration

**Play Button Behavior**:
- If show not currently playing → Start playing from first track
- If show currently playing → Toggle play/pause

**Track Selection**: Tapping a track starts playback from that track

**Current Track Highlight**: Currently playing track highlighted in list (when on correct show/recording)

---

## Integration with Other Features

### Player Integration

**Relationship**: Show Detail feeds playback queue to Player

**Navigation**:
- Show Detail → Player: Tap play button or track
- Player → Show Detail: Tap show context in player

**State Sharing**: Both observe same playback state (currently playing track, play/pause)

---

### Library Integration

**Heart Icon**: Shows library status, toggles membership

**Actions**:
- Add to library from show detail
- Remove from library from show detail
- Instant visual feedback

---

### Collections Integration

**Display**: Shows which collections contain this show

**Navigation**: Navigate to collection detail with show pre-selected

---

### Recent History Integration

**Tracking**: Viewing show detail may update recent history (debatable)

**Navigation**: Recent history links to show detail

---

## Design Decisions

### Why "Playlist" Name in Code?

**Current State**: Android uses `v2:feature:playlist` module name

**Confusion**: Not user-created playlists, but show detail/track list

**Future**: Should rename to `show-detail` for clarity

---

### Why Spotify-Style Layout?

**Decision**: Vertical scroll with large album art at top

**Rationale**:
- Familiar pattern for music apps
- Puts visual focus on album art
- Efficient use of vertical space
- Natural scroll experience

---

### Why Progressive Track Loading?

**Decision**: Load show metadata first, then tracks

**Rationale**:
- Show info is small and loads quickly
- Track lists can be large (30+ tracks)
- Users see content faster
- Better perceived performance

---

### Why Recording Selection Sheet?

**Decision**: Bottom sheet instead of inline dropdown

**Rationale**:
- More space to show recording details
- Easier to compare recordings
- Doesn't clutter main UI
- Can show rich metadata per recording

---

### Why Chronological Navigation?

**Decision**: Previous/next buttons navigate chronologically through all shows

**Rationale**:
- Natural way to explore Dead history
- Discover shows adjacent to favorites
- No confusion about navigation logic
- Always enabled (no disabled state confusion)

---

## State Model

### UI State

**Core State**:
- `isLoading: Boolean` - Initial show load
- `isTrackListLoading: Boolean` - Track list progressive load
- `error: String?` - Error message if load failed
- `showData: PlaylistShowViewModel?` - Show metadata
- `trackData: List<PlaylistTrackViewModel>` - Track list

**Playback State** (from Player service):
- `isPlaying: Boolean` - Currently playing
- `mediaLoading: Boolean` - Loading playback
- `isCurrentShowAndRecording: Boolean` - Is this show/recording currently playing?

**Bottom Sheet State**:
- `showReviewDetails: Boolean` - Review details sheet visible
- `showMenu: Boolean` - Menu sheet visible
- `recordingSelection: RecordingSelectionState` - Recording selection state
- `showCollectionsSheet: Boolean` - Collections sheet visible
- `showSetlistModal: Boolean` - Setlist sheet visible

---

### Show Data Model

**PlaylistShowViewModel**:
- `showId: String`
- `date: String` - YYYY-MM-DD format
- `displayDate: String` - "May 8, 1977"
- `venue: String` - Venue name
- `location: String` - City, state
- `recordingId: String` - Current recording
- `averageRating: Float` - Archive.org rating
- `totalReviews: Int` - Number of reviews
- `isInLibrary: Boolean` - Library status

---

### Track Data Model

**PlaylistTrackViewModel**:
- `trackIndex: Int` - Position in recording
- `title: String` - Song title
- `duration: String` - Formatted duration ("5:23")
- `isCurrentTrack: Boolean` - Currently playing
- `isDownloaded: Boolean` - Downloaded for offline
- `isDownloading: Boolean` - Download in progress

---

### Recording Selection Model

**RecordingOptionsResult**:
- `recordings: List<RecordingOption>` - All available recordings
- `currentRecordingId: String` - Currently selected
- `recommendedRecordingId: String?` - Archive.org recommendation
- `userDefaultRecordingId: String?` - User's saved default

**RecordingOption**:
- `recordingId: String` - Identifier
- `sourceType: String` - "Soundboard", "Audience", etc.
- `format: String` - "FLAC", "MP3", etc.
- `averageRating: Float`
- `reviewCount: Int`
- `fileSize: String` - Human-readable size
- `isRecommended: Boolean`
- `isUserDefault: Boolean`

---

## Error Handling

### Show Load Failed

**Scenario**: Network error, show not found, database error

**UI Response**:
- Display error message
- Show "Retry" button
- Log error for debugging

---

### Track List Load Failed

**Scenario**: Network error, recording unavailable

**UI Response**:
- Show error message in track list area
- Show info remains visible
- Provide retry option

---

### Playback Failed

**Scenario**: Track unavailable, network error

**UI Response**:
- Show error toast/snackbar
- Log error details
- Player handles error state

---

## Technical Considerations

### Progressive Loading

**Challenge**: Balance between fast initial render and complete data

**Approach**:
1. Load show metadata first (fast, small payload)
2. Display show info immediately
3. Load track list in background
4. Display tracks when ready

---

### State Synchronization with Player

**Challenge**: Keep show detail and player in sync

**Approach**: Observe shared playback state from media controller

---

### Recording Switching

**Challenge**: Switching recordings requires reloading entire track list

**Approach**:
- Show loading indicator
- Cancel any in-progress track loads
- Load new recording's tracks
- Update UI when ready

---

## Code References

**Android Implementation** (Note: "playlist" module should be renamed "show-detail"):
- Screen: `androidApp/v2/feature/playlist/src/main/java/com/deadly/v2/feature/playlist/screens/main/PlaylistScreen.kt:38`
- ViewModel: `androidApp/v2/feature/playlist/src/main/java/com/deadly/v2/feature/playlist/screens/main/models/PlaylistViewModel.kt:1`
- Service Interface: `androidApp/v2/core/api/playlist/src/main/java/com/deadly/v2/core/api/playlist/PlaylistService.kt:13`

**Rename Note**: Throughout Android codebase, "playlist" refers to show detail, not user-created playlists. This should be renamed for clarity.

---

## See Also

- [Player](player.md) - Full-screen playback
- [Library](library.md) - Personal favorites
- [Collections](collections.md) - Curated show collections
- [Search](search.md) - Finding shows
- [Recent History](recent.md) - Recently played shows
