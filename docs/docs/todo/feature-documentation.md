# TODO: Feature Documentation

**Priority**: Critical
**Status**: Not Started
**Estimated Effort**: 8-10 hours

## Problem

The application has multiple user-facing features, but there is no comprehensive documentation that explains:
- What each feature does
- How features are implemented
- Where feature code lives
- How features interact with each other
- User flows through features
- Feature-specific business logic

Without this documentation:
- Developers don't know where to find feature code
- It's unclear how features are supposed to work
- Bug reports can't be efficiently triaged
- New features may duplicate existing functionality
- QA doesn't have a reference for testing

## What Needs Documentation

### 1. Feature Overview

Create a high-level feature map showing all application features:

From docs/docs/index.md and module structure, the app has:
- **Search** - Finding concerts
- **Playlist/Collections** - Curated concert lists
- **Player** - Audio playback
- **Miniplayer** - Minimized playback controls
- **Library** - Personal collection
- **Recent/History** - Listening history
- **Home** - Main screen
- **Settings** - App configuration
- **Splash** - Launch screen

### 2. Search Feature

Document the search functionality in detail:

#### Search Capabilities
From the app description:
- Search by **song names** ("Truckin'", "Dark Star")
- Search by **band members** (specific musicians)
- Search by **cities & venues** (locations)
- Search by **dates & tours** (time periods)

#### Implementation Details
- **Module**: `v2:feature:search` (UI) + `v2:core:search` (logic)
- **Entry point**: Where is search accessed?
- **Search UI**: What does the search interface look like?
- **Query construction**: How are searches sent to Internet Archive?
- **Results display**: How are results shown?
- **Filtering**: Can results be filtered?
- **Sorting**: How are results ordered?
- **Pagination**: How are large result sets handled?

#### User Flows
```
User enters search query →
  Select search type (song/venue/date/member) →
  Results display →
  User selects a show →
  Navigate to show details
```

#### Technical Implementation
- Search state management
- Debouncing user input
- Search history
- Recent searches
- Search suggestions/autocomplete

### 3. Player Feature

Document the audio player:

#### Player Capabilities
- Play/pause
- Next/previous track
- Seek within track
- Volume control
- Playback speed (if supported)
- Shuffle/repeat modes

#### Implementation Details
- **Module**: `v2:feature:player` (UI) + `v2:core:player` (logic)
- **Media framework**: Media3 (confirmed from dependencies)
- **Now playing screen**: Full player UI
- **Background playback**: Does it play when app is backgrounded?
- **Lock screen controls**: Media session integration
- **Notification controls**: Playback notification

#### State Management
- Current track
- Play/pause state
- Queue management
- Playback position
- Buffering state

#### Integration Points
- How playlists feed into player
- How search results start playback
- Queue modification
- Persistent playback state

### 4. Miniplayer Feature

Document the minimized player:

#### Miniplayer Capabilities
- Appears when content is playing
- Shows current track info
- Basic controls (play/pause, next)
- Tap to expand to full player

#### Implementation Details
- **Module**: `v2:feature:miniplayer` + `v2:core:miniplayer`
- **Positioning**: Bottom of screen, above nav bar?
- **Animation**: How it appears/disappears
- **Gesture handling**: Swipe to dismiss?

#### State Synchronization
- How it stays in sync with main player
- Shared state management
- Updates when track changes

### 5. Playlist/Collections Feature

Document playlist and collection management:

#### Capabilities
- Create playlists
- Add shows to playlists
- Remove shows from playlists
- Reorder playlist items
- Delete playlists
- Rename playlists
- Browse curated collections?

#### Implementation Details
- **Module**: `v2:feature:playlist` + `v2:core:playlist`
- **Module**: `v2:feature:collections` + `v2:core:collections`
- **Storage**: Local database
- **Sync**: Cloud sync? (if any)

#### Collections vs Playlists
- What's the difference?
- Collections = curated by app?
- Playlists = user-created?

### 6. Library Feature

Document the personal library:

#### Library Capabilities
- View favorite shows
- Add shows to library
- Remove shows from library
- Organize library
- Search within library

#### Implementation Details
- **Module**: `v2:feature:library` + `v2:core:library`
- **Storage**: Local database
- **Categories**: How is library organized?
- **Sorting options**: By date, venue, recently added?

#### Integration
- Adding from search results
- Adding from player
- Quick add functionality

### 7. Recent/History Feature

Document listening history:

#### Capabilities
- View recently played shows
- View recently played tracks
- Resume playback
- Clear history

#### Implementation Details
- **Module**: `v2:core:recent`
- **Storage**: Database
- **Tracking**: When is history recorded?
- **Privacy**: Can history be disabled?

### 8. Home Feature

Document the home screen:

#### Home Screen Sections
- What's displayed on home?
- Featured shows?
- Continue listening?
- Recommended content?
- Quick access to library/search?

#### Implementation Details
- **Module**: `v2:feature:home` + `v2:core:home`
- **Content sources**: Where does home content come from?
- **Personalization**: Is it customized per user?

### 9. Settings Feature

Document app settings:

#### Settings Available
- Playback settings (quality, buffering)
- Appearance (theme, dark mode)
- Storage (cache management)
- About (version, licenses)
- Account settings (if applicable)

#### Implementation Details
- **Module**: `v2:feature:settings`
- **Storage**: SharedPreferences? DataStore?
- **Settings sync**: Across devices?

### 10. Splash Screen

Document the launch experience:

#### Splash Capabilities
- App branding
- Loading indicator
- Initial data fetch

#### Implementation Details
- **Module**: `v2:feature:splash`
- **Duration**: How long?
- **Loading tasks**: What happens during splash?
- **Navigation**: Where does user go after splash?

### 11. Cross-Feature Interactions

Document how features work together:

#### Navigation Flows
```
Home → Search → Results → Show Detail → Player
Home → Library → Show → Player
Player → Playlist → Show → Continue Playing
```

#### Shared State
- What state is shared across features?
- How is state synchronized?
- Event bus or shared ViewModels?

#### Deep Linking
- Can users deep link to shows?
- Can users share shows?
- URL scheme handling

## Structure

Create: `docs/docs/developer/features.md`

Suggested outline:
```markdown
# Features

## Overview
[Feature map]

## Search
### Capabilities
### Implementation
### User Flows
### Technical Details

## Player
### Capabilities
### Implementation
### Media Controls
### State Management

## Miniplayer
### Capabilities
### Implementation
### Synchronization

## Playlists & Collections
### Capabilities
### Implementation
### Differences

## Library
### Capabilities
### Implementation
### Organization

## Recent History
### Capabilities
### Implementation
### Privacy

## Home Screen
### Content
### Implementation
### Personalization

## Settings
### Available Settings
### Implementation
### Persistence

## Splash Screen
### Purpose
### Implementation

## Cross-Feature Integration
### Navigation
### Shared State
### Deep Linking

## Adding a New Feature
[Step-by-step guide]

## References
```

## Research Required

To write this documentation, investigate:

1. **Feature modules**: Examine each `v2:feature:*` module
2. **Core modules**: Examine corresponding `v2:core:*` modules
3. **Navigation**: Check navigation graph in app module
4. **UI flows**: Trace user journeys through the code
5. **State management**: How is state handled in each feature?
6. **Database**: What feature data is persisted?
7. **Shared state**: How do features communicate?
8. **Media controls**: Player implementation with Media3
9. **Settings**: What's configurable?
10. **Deep linking**: Is it implemented?

## Code References

Key files to examine for each feature:

**Search**:
- `androidApp/v2/feature/search/` (UI code)
- `androidApp/v2/core/search/` (business logic)
- `androidApp/v2/core/api/search/` (API contract)

**Player**:
- `androidApp/v2/feature/player/` (UI)
- `androidApp/v2/core/player/` (playback logic)
- Media3 integration code

**Miniplayer**:
- `androidApp/v2/feature/miniplayer/`
- `androidApp/v2/core/miniplayer/`

**Library**:
- `androidApp/v2/feature/library/`
- `androidApp/v2/core/library/`

**Playlists**:
- `androidApp/v2/feature/playlist/`
- `androidApp/v2/core/playlist/`

**Collections**:
- `androidApp/v2/feature/collections/`
- `androidApp/v2/core/collections/`

**Home**:
- `androidApp/v2/feature/home/`
- `androidApp/v2/core/home/`

**Settings**:
- `androidApp/v2/feature/settings/`

**Recent**:
- `androidApp/v2/core/recent/`

**Splash**:
- `androidApp/v2/feature/splash/`

## Checklist

- [ ] Document search feature (all search types)
- [ ] Document player feature (full details)
- [ ] Document miniplayer feature
- [ ] Document playlist management
- [ ] Document collections feature
- [ ] Clarify difference between playlists and collections
- [ ] Document library feature
- [ ] Document recent/history feature
- [ ] Document home screen
- [ ] Document settings feature
- [ ] Document splash screen
- [ ] Map all navigation flows
- [ ] Document cross-feature state sharing
- [ ] Document deep linking (if exists)
- [ ] Add screenshots or wireframes for each feature
- [ ] Include user flow diagrams
- [ ] Add code examples for common feature tasks
- [ ] Document iOS implementation differences
- [ ] Create feature implementation guide
- [ ] Add troubleshooting section per feature

## Success Criteria

A developer should be able to:
- Understand what each feature does
- Find the code for any feature
- Understand how features interact
- Add a new feature following established patterns
- Modify an existing feature confidently
- Debug feature-specific issues
- Explain any feature to a non-technical person

A QA engineer should be able to:
- Understand expected feature behavior
- Create comprehensive test cases
- Know what edge cases to test
- File detailed bug reports

A product manager should be able to:
- Understand current feature capabilities
- Plan new features with context
- Understand technical constraints

## Notes

Feature documentation bridges technical and product knowledge. It should be written for multiple audiences:
- **Developers**: Technical implementation details
- **QA**: Expected behavior and test cases
- **Product**: Capabilities and limitations
- **Design**: User flows and interactions

Consider creating two versions:
1. **User-facing**: What features do and how to use them
2. **Developer-facing**: How features are implemented

Or combine into one comprehensive document with clear sections for different audiences.

## iOS Consideration

Don't forget to document iOS implementation:
- Are features the same across platforms?
- What are the differences?
- Are they in feature parity?
- Platform-specific considerations
