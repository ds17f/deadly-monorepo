# Player

Full-screen audio playback interface for streaming Grateful Dead concerts.

---

## Overview

The Player feature provides a rich, full-screen audio playback experience with comprehensive controls, metadata display, and queue management. It's the central interface for listening to shows, displaying track information, setlists, and playback progress.

**Purpose**: Primary listening interface for show playback

**Key Responsibilities**:
- Display current track and show information
- Provide playback controls (play/pause, next, previous, seek)
- Show playback progress with scrubbing support
- Enable queue navigation
- Share tracks and shows

---

## User Experience

### Primary Display

**Track Information**:
- Song title (e.g., "Scarlet Begonias")
- Artist (always "Grateful Dead")
- Album/Recording ID (e.g., "gd1977-05-08.sbd.miller")
- Show date (e.g., "May 8, 1977")
- Venue and location (e.g., "Barton Hall - Ithaca, NY")

**Progress Information**:
- Current position (MM:SS format)
- Total duration (MM:SS format)
- Visual progress bar (0-100%)
- Draggable scrubber for seeking

---

### Playback Controls

**Standard Controls**:

1. **Play/Pause Button**: Toggle between play and pause states
2. **Next Button**: Skip to next track in setlist
3. **Previous Button**: Smart previous behavior (see below)
4. **Progress Bar**: Drag to seek within current track

**Previous Button Behavior**:

The previous button uses a **3-second threshold** for intelligent navigation:

- **After 3+ seconds**: Restarts current track from beginning
- **Before 3 seconds**: Skips to previous track in setlist
- **First track exception**: If already at first track, always restarts (no previous track to skip to)

**Why 3 seconds?** Most users expect "previous" to mean "restart this song" once they've heard more than a few seconds. Early in the track, they likely want the actual previous song.

---

### Auto-Play on Navigation

**Design Decision**: When navigating to next/previous track while paused, automatically start playing the new track.

**Rationale**:
- Users navigating through tracks want to hear them, not manually play each one
- Creates smoother browsing experience
- Consistent with most music player UX patterns

**Behavior**:
- User is paused on track 5
- User taps "Next" → Track 6 starts playing automatically
- User taps "Previous" → Track 5 starts playing automatically

---

## Key Behaviors

### Queue Management

**Current Implementation**: Player displays single track at a time

**Queue State Tracking**:
- Current track index in recording
- Total tracks in recording
- Has next track available (boolean)
- Has previous track available (boolean)

---

### Metadata Display

**Show Context**: Player displays rich show information to provide context:

- **Date**: When the show happened
- **Venue**: Where the show happened
- **Location**: City and state
- **Recording Info**: Which recording/source is playing

**Why Show Context Matters**: Unlike studio albums, live shows are identified by date and venue. Displaying "May 8, 1977 - Barton Hall" is more meaningful to Deadheads than a generic album title.

---

### Contextual Information Panels

**Scrollable Content**: Below the player controls, users can scroll down to view contextual information panels.

**Panel Types**:

1. **About the Venue**
    - Historical information about the venue
    - Significance to Grateful Dead history
    - Notable shows at the venue

2. **Lyrics**
    - Song lyrics for the current track
    - Helps users follow along or learn songs
    - Educational context for new listeners

3. **Similar Shows**
    - Recommendations for related shows
    - Shows from same tour or era
    - Other notable performances of the same songs

4. **Credits**
    - Band lineup for this show
    - Personnel and instruments
    - Guest musicians (if applicable)

**Design**:
- Displayed as elevated Material3 cards with rounded corners
- Always expanded (not collapsible)
- Clean, readable typography
- Consistent spacing and padding

**Scroll Behavior**:
- When user scrolls down to view panels, a mini player overlay appears at the top
- Allows access to playback controls while reading contextual information
- User can tap mini player to scroll back to full controls

**Why Panels?** Provides rich educational context without cluttering the main player interface. Users can choose to explore or ignore as desired.

---

### In-Player Mini Player

**Purpose**: When scrolling down to read contextual panels, keep playback controls accessible without returning to top.

**Trigger**: Appears when scroll position exceeds ~1200px (main controls scrolled off screen)

**Position**: Top of screen (overlay)

**Display**:
- Track title
- Album/Recording ID
- Play/pause button
- Progress bar (view only, no scrubbing)

**Interaction**:
- Tap anywhere → Scroll back to top (shows full controls)
- Tap play/pause → Toggle playback without scrolling

**Visual Design**:
- Uses recording-based color theme (matches gradient)
- Elevated card with 72dp height
- 3dp progress bar at bottom edge
- White text and icons

**Why Separate from App-Wide Miniplayer?** This is specific to the Player screen scroll behavior. The app-wide Miniplayer (visible across all screens) is a different component for global playback access.

---

### Sharing Features

**Share Current Track**:
- Generates shareable link to specific track
- Includes show, recording, and track information
- Recipients can view the track in context of the full show

**Share Current Show**:
- Generates shareable link to show and recording
- Shows full setlist and recording details
- Enables discovery and recommendations

---

## Integration with Other Features

### Miniplayer Integration

**Relationship**: Player and Miniplayer show the same playback state

**State Sharing**:
- Both display same track information
- Both show same play/pause state
- Both show same progress
- Controls from either affect same underlying playback

**Navigation**:
- Miniplayer → Player: Tap to expand to full-screen
- Player → Miniplayer: Swipe down or back button to minimize

---

### Recent History Integration

**Behavior**: Playing a track updates recent history

**Updates**:
- Show added to recent history on first play
- Last played timestamp updated on each play
- Play count incremented

**Storage**: Uses UPSERT pattern (one row per show, not event log)

---

### Home Screen Integration

**Continue Listening**: Recent history feeds "Continue Listening" section on home screen

- Shows most recently played shows
- Tapping continues from where user left off

---

## Design Decisions

### Why Full-Screen Player?

**Decision**: Dedicated full-screen player instead of inline mini-player everywhere

**Rationale**:
- Live shows are primary content (not background music)
- Users want to see setlist context and show details
- Full screen provides space for rich metadata display
- Minimizes to Miniplayer when user navigates away

---

### Why Smart Previous Button?

**Decision**: 3-second threshold instead of always going to previous track

**Rationale**:
- Users early in track (0-3s) likely want previous track
- Users later in track (3s+) likely want to restart current track
- Avoids frustration of accidentally skipping backward when intending to restart
- Industry standard behavior (Spotify, Apple Music use similar threshold)

---

### Why Auto-Play on Navigation?

**Decision**: Auto-play next/previous track even when paused

**Rationale**:
- Users browsing tracks want to hear them quickly
- Reduces interaction cost (no need to play after every skip)
- Matches behavior of browse-heavy music apps
- Can always pause immediately if unwanted

---

### Why No Shuffle/Repeat?

**Decision**: No shuffle or repeat controls (at least initially)

**Rationale**:
- Grateful Dead shows follow setlist order (narrative arc)
- Shuffling songs loses flow (segues like Scarlet→Fire are intentional)
- Users can replay show manually or build playlists for shuffle
- Keeps UI focused and uncluttered

---

## State Model

### Playback State

**Core State**:
- `isPlaying: Boolean` - Currently playing or paused
- `isLoading: Boolean` - Buffering or loading track
- `currentPosition: Long` - Milliseconds into current track
- `duration: Long` - Total track length in milliseconds
- `progress: Float` - Computed progress (0.0 to 1.0)

---

### Track State

**Metadata**:
- `songTitle: String` - Track name
- `artist: String` - Always "Grateful Dead"
- `album: String` - Recording identifier
- `showDate: String` - Formatted date
- `venue: String` - Venue name and location
- `showId: String` - For navigation to show detail
- `recordingId: String` - For fetching tracks/metadata

---

### Queue State

**Navigation Info**:
- `currentIndex: Int` - Current track position in setlist
- `totalTracks: Int` - Total tracks in recording
- `hasNext: Boolean` - Can navigate to next track
- `hasPrevious: Boolean` - Can navigate to previous track

---

## Error Handling

### No Track Playing

**Scenario**: Player opened with no active playback

**UI Response**:
- Display "No Track Playing" message
- Disable all controls
- Show empty progress bar

---

### Track Load Failed

**Scenario**: Network error or track unavailable

**UI Response**:
- Display error message
- Show retry button
- Log error details for debugging

---

### Network Interruption During Playback

**Scenario**: Stream buffering or connection lost

**UI Response**:
- Show loading/buffering indicator
- Automatically retry when connection restored
- Preserve playback position

---

## Technical Considerations

### State Synchronization

**Challenge**: Keep Player and Miniplayer perfectly synchronized

**Approach**: Single source of truth for playback state, observed by both UIs

---

### Progress Updates

**Challenge**: Update progress bar smoothly without excessive updates

**Approach**: Update at reasonable interval (~250ms), use efficient state updates

---

### Metadata Enrichment

**Challenge**: Media player has limited metadata fields

**Approach**: Enrich with database queries for show/venue information on-demand

---

## Code References

**Android Implementation**:
- ViewModel: `androidApp/v2/feature/player/src/main/java/com/deadly/v2/feature/player/screens/main/models/PlayerViewModel.kt:22`
- Service Interface: `androidApp/v2/core/api/player/src/main/java/com/deadly/v2/core/api/player/PlayerService.kt:16`

---

## See Also

- [Miniplayer](miniplayer.md) - Minimized playback overlay
- [Recent History](recent.md) - Playback tracking
- [Home](home.md) - "Continue Listening" integration
