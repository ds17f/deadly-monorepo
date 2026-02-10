# Features

This section documents the user-facing features of TheDeadly app from a developer perspective. Each feature page explains what the feature does, why it exists, and how it's implemented.

## Feature Overview

TheDeadly provides nine core features that work together to create a comprehensive Grateful Dead concert streaming experience:

| Feature | Purpose | Primary Modules |
|---------|---------|-----------------|
| **[Search](search.md)** | Find shows by songs, venues, dates, members | `v2:feature:search`, `v2:core:search` |
| **[Player](player.md)** | Full-screen audio playback with controls | `v2:feature:player`, `v2:core:player` |
| **[Miniplayer](miniplayer.md)** | Minimized playback controls overlay | `v2:feature:miniplayer`, `v2:core:miniplayer` |
| **[Library](library.md)** | Personal favorites and bookmarks | `v2:feature:library`, `v2:core:library` |
| **[Playlists](playlists.md)** | User-created show collections | `v2:feature:playlist`, `v2:core:playlist` |
| **[Collections](collections.md)** | App-curated box sets | `v2:feature:collections`, `v2:core:collections` |
| **[Recent](recent.md)** | Listening history tracking | `v2:core:recent` |
| **[Home](home.md)** | Main dashboard and navigation hub | `v2:feature:home`, `v2:core:home` |
| **[Settings](settings.md)** | App configuration and preferences | `v2:feature:settings` |

## Feature Map

```
┌─────────────────────────────────────────────────────────────┐
│                         HOME SCREEN                          │
│  - Continue Listening                                        │
│  - Featured Collections                                      │
│  - Quick Access                                              │
└─────┬───────────────────────────────────┬───────────────────┘
      │                                   │
      ├───────────┐              ┌────────┴────────┐
      │           │              │                 │
      ▼           ▼              ▼                 ▼
  ┌────────┐  ┌─────────┐  ┌─────────┐      ┌──────────┐
  │ SEARCH │  │ LIBRARY │  │ RECENT  │      │ SETTINGS │
  └───┬────┘  └────┬────┘  └────┬────┘      └──────────┘
      │            │             │
      │            │             │
      ▼            ▼             ▼
  ┌──────────────────────────────────┐
  │       SHOW DETAIL                │
  │  - Setlist                       │
  │  - Recordings                    │
  │  - Reviews                       │
  └─────────────┬────────────────────┘
                │
                ▼
  ┌─────────────────────────────────┐
  │         PLAYER                   │
  │  - Full playback UI              │
  │  - Track list                    │
  │  - Queue management              │
  └──────────────────────────────────┘
                │
                ▼ (minimize)
  ┌─────────────────────────────────┐
  │       MINIPLAYER                 │
  │  - Always visible overlay        │
  │  - Basic controls                │
  │  - Tap to expand                 │
  └──────────────────────────────────┘

  ┌──────────────┐     ┌──────────────┐
  │  PLAYLISTS   │     │ COLLECTIONS  │
  │  (user-made) │     │  (curated)   │
  └──────────────┘     └──────────────┘
```

## Cross-Feature Patterns

### Navigation

All features participate in the app's navigation graph:

- **Bottom Navigation**: Home, Search, Library, Collections (primary destinations)
- **Deep Linking**: Show detail screens accessible from multiple entry points
- **Back Stack**: Managed by Navigation Compose (Android) / NavigationStack (iOS)

### State Sharing

Features share state through multiple mechanisms:

1. **Player State**: Shared across Player and Miniplayer
    - Current track, playback position, play/pause state
    - Queue management
    - Synchronized via shared `PlayerService`

2. **Library State**: Shared across Library, Playlists, Collections
    - Favorite status changes
    - Playlist membership
    - Synchronized via database observers

3. **Search State**: Recent searches and history
    - Persisted to database
    - Accessed by Search and Home features

### Data Flow

```
User Action (UI)
    ↓
ViewModel (feature module)
    ↓
Repository/Service (core module)
    ↓
┌────────────┬──────────────┐
│            │              │
▼            ▼              ▼
Database    API Cache    PlayerService
│            │              │
└────────────┴──────────────┘
             ↓
     StateFlow/LiveData
             ↓
       UI Updates
```

## Module Architecture

Features follow a consistent two-module pattern:

### Feature Module (`v2:feature:X`)

- **Purpose**: UI layer (Composables, Activities, Fragments)
- **Dependencies**: Can depend on `v2:core:api:X` only (not implementation)
- **Responsibilities**:
    - Composable screens
    - ViewModels (presentation logic)
    - Navigation handling
    - User input processing

### Core Module (`v2:core:X`)

- **Purpose**: Business logic and data access
- **Dependencies**: Can depend on database, network, other core modules
- **Responsibilities**:
    - Repositories
    - Services
    - Data transformations
    - Business rules
    - Use cases (if applicable)

### API Module (`v2:core:api:X`)

- **Purpose**: Contracts between feature and core
- **Dependencies**: Domain models only
- **Responsibilities**:
    - Repository interfaces
    - Service interfaces
    - Domain models
    - Result types

## Common Implementation Patterns

### State Management

All features use similar state management patterns:

```kotlin
// ViewModel holds UI state
data class FeatureUiState(
    val isLoading: Boolean = false,
    val data: List<Item> = emptyList(),
    val error: String? = null
)

// Exposed as StateFlow
val uiState: StateFlow<FeatureUiState>

// UI observes and renders
LaunchedEffect(Unit) {
    viewModel.uiState.collect { state ->
        // Update UI
    }
}
```

### Error Handling

All features handle errors consistently:

- **Network errors**: Show retry UI
- **Not found**: Show empty state
- **Unexpected errors**: Log and show generic error message

### Loading States

All features show loading indicators during async operations:

- **Initial load**: Full-screen loading
- **Refresh**: Pull-to-refresh indicator
- **Background operations**: Silent or subtle indicators

## Testing Strategy

Each feature should have tests at multiple levels:

1. **Unit Tests**: ViewModel logic, data transformations
2. **Integration Tests**: Repository + database interactions
3. **UI Tests**: Composable rendering and user interactions

See individual feature pages for specific test examples.

## Platform Differences

### Android (Current Implementation)
- Jetpack Compose for UI
- Navigation Compose for navigation
- Hilt for dependency injection
- Media3 for audio playback

### iOS (Future Implementation)
- SwiftUI for UI
- NavigationStack for navigation
- Property injection or factory for DI
- AVPlayer for audio playback

Feature functionality should be identical across platforms, but implementation details will differ based on platform conventions.

## Adding a New Feature

To add a new feature:

1. **Create modules**:
    - `v2:core:api:featurename` - Interfaces and models
    - `v2:core:featurename` - Implementation
    - `v2:feature:featurename` - UI

2. **Define API contract** in API module:
    - Repository interface
    - Domain models
    - Result types

3. **Implement core logic**:
    - Repository implementation
    - Service layer (if needed)
    - Database entities (if needed)

4. **Implement UI**:
    - ViewModels
    - Composables
    - Navigation integration

5. **Add navigation**:
    - Define routes
    - Add to navigation graph
    - Update bottom nav (if primary destination)

6. **Write tests**:
    - Unit tests for ViewModels
    - Integration tests for repositories
    - UI tests for key flows

7. **Document**:
    - Add page to this features section
    - Update this index with feature summary

## References

- [Architecture](../architecture.md) - Overall architecture patterns
- [Database](../database/index.md) - Database design and entities
- [API Integration](../api-integration.md) - Archive.org API integration
