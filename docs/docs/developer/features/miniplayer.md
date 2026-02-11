# Miniplayer

Persistent overlay showing current playback with basic controls, visible across all screens.

---

## Overview

The Miniplayer is a minimized playback control that stays visible at the bottom of the screen while users navigate through the app. It provides quick access to playback controls and track information without requiring the full Player screen.

**Purpose**: Keep playback accessible and visible across the entire app

**Key Responsibilities**:
- Display current track information (condensed)
- Provide play/pause control
- Show playback progress
- Enable quick expansion to full Player
- Persist across all navigation screens

---

## User Experience

### Display

**Layout**: Compact horizontal bar at bottom of screen

**Track Information** (condensed):
- Song title
- Show date (brief format)
- Recording ID or venue (abbreviated if needed)

**Visual Elements**:
- Small progress bar (no scrubbing)
- Play/pause button
- Compact layout fitting in ~60-80dp height

**Always Visible**: Appears on all screens except:
- Full Player screen (redundant)
- Initial app launch (no track playing yet)

---

### Interactions

**Primary Actions**:

1. **Tap Anywhere** (on track info): Expand to full Player screen
2. **Tap Play/Pause**: Toggle playback without expanding
3. **Visual Progress**: Shows current position (view only, no scrubbing)

**No Additional Controls**: Deliberately minimal to save space
- No next/previous buttons (must expand to full Player)
- No seek control (must expand to full Player)
- No close/dismiss button (Miniplayer stays until playback stops)

---

## Key Behaviors

### Visibility Rules

**Show Miniplayer When**:
- Track is loaded and metadata is available
- User is on any screen except full Player

**Hide Miniplayer When**:
- No track is playing (app just launched)
- User is viewing full Player screen

---

### State Synchronization

**Shared State with Player**: Miniplayer observes the same playback state as full Player

**Synchronized Elements**:
- Play/pause button state
- Track information
- Playback progress
- Loading states

**Single Source of Truth**: Both UIs react to same underlying media player state

**Example Flow**:
1. User taps play in Miniplayer → Playback starts
2. Full Player (if open) automatically updates to show playing state
3. Progress updates in both UIs simultaneously

---

### Navigation Integration

**Tap to Expand**:
- Tapping Miniplayer (track info area) navigates to full Player screen
- Carries over showId and recordingId for context
- Player displays same track that was in Miniplayer

**Return from Player**:
- Minimizing Player (back button, swipe down) returns to previous screen
- Miniplayer reappears at bottom
- State remains synchronized

---

## Integration with Other Features

### Player Integration

**Relationship**: Miniplayer is a condensed view of Player state

**Shared State**:
- Same track playing
- Same playback position
- Same play/pause state
- Controls affect same media player

**UI Difference**:
- Miniplayer: Compact, minimal controls, persistent overlay
- Player: Full-screen, rich metadata, all controls

**Not the Same as In-Player Mini Player**: The Player screen has its own scroll-based mini player at the top. This Miniplayer is app-wide and appears at the bottom.

---

### Navigation Integration

**Overlay Behavior**: Miniplayer appears above navigation content

**Z-Index**: Positioned above bottom navigation bar (if present) but below modals/dialogs

**Screen Compatibility**: Works with all app screens:
- Home
- Search
- Library
- Collections
- Show Detail
- Settings

**Layout Coordination**: Screens add bottom padding to prevent content from being covered by Miniplayer

---

### Recent History Integration

**Passive Tracking**: Miniplayer doesn't directly interact with recent history

**Indirect Link**: Playing tracks via Miniplayer updates recent history (through shared player service)

---

## Design Decisions

### Why Persistent Overlay?

**Decision**: Show Miniplayer across all screens instead of only on specific pages

**Rationale**:
- Users expect continuous access to playback controls
- Reinforces that music is always playing in background
- Reduces need to return to Player screen frequently
- Industry standard (Spotify, YouTube Music, Apple Music)

---

### Why Minimal Controls?

**Decision**: Only play/pause in Miniplayer, no next/previous/seek

**Rationale**:
- Saves vertical space (important on mobile)
- Encourages tapping to full Player for complex operations
- Play/pause is 90% of quick interactions
- Keeps UI clean and uncluttered

---

### Why No Scrubbing in Progress Bar?

**Decision**: Progress bar is view-only, not interactive

**Rationale**:
- Scrubbing requires precision (hard on small bar)
- Accidental touches would interrupt playback
- Full Player provides better scrubbing experience
- Miniplayer is for quick glance, not detailed control

---

### Why Bottom Position?

**Decision**: Place at bottom of screen instead of top

**Rationale**:
- More reachable on large phones (thumb-friendly)
- Doesn't interfere with top navigation/status bar
- Consistent with music app conventions
- Natural expansion gesture (swipe up or tap)

---

## State Model

### UI State

**Core State**:
- `shouldShow: Boolean` - Whether Miniplayer should be visible
- `isPlaying: Boolean` - Play or pause state
- `isLoading: Boolean` - Buffering state
- `progress: Float` - Playback progress (0.0 to 1.0)

**Track Metadata**:
- `currentTrack: CurrentTrackInfo?` - Song title, show date, venue
- `showId: String?` - For navigation to Player
- `recordingId: String?` - For navigation to Player

**Error State**:
- `error: String?` - Playback error message (if any)

---

## Technical Considerations

### Overlay Implementation

**Challenge**: Keep Miniplayer visible across navigation stack

**Approach**: Render at root navigation level, above all screens

---

### State Observation

**Challenge**: Efficiently observe playback state without draining battery

**Approach**: Share same state flows as Player (no duplicate subscriptions)

---

### Layout Coordination

**Challenge**: Prevent Miniplayer from covering content

**Approach**: Add bottom padding to screen content equal to Miniplayer height

---

## Error Handling

### Track Metadata Missing

**Scenario**: Playback started but metadata not yet loaded

**UI Response**:
- Show loading indicator
- Display placeholder text ("Loading...")
- Keep play/pause control available

---

### Playback Error

**Scenario**: Network error or stream failed

**UI Response**:
- Show error icon/color
- Display brief error message
- Allow retry via play button

---

## Code References

**Android Implementation**:
- ViewModel: `androidApp/v2/feature/miniplayer/src/main/java/com/deadly/v2/feature/miniplayer/screens/main/models/MiniPlayerViewModel.kt:25`
- Service Interface: `androidApp/v2/core/api/miniplayer/src/main/java/com/deadly/v2/core/api/miniplayer/MiniPlayerService.kt:17`

---

## See Also

- [Player](player.md) - Full-screen playback interface (includes in-player mini player)
- [Recent History](recent.md) - Playback tracking
- [Home](home.md) - Miniplayer persists on home screen
