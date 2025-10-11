# TODO: Architecture Patterns Documentation

**Priority**: Critical
**Status**: Not Started
**Estimated Effort**: 8-10 hours

## Problem

The Android application has a sophisticated multi-module architecture, but there is no documentation explaining:

- Overall architectural approach (Clean Architecture? MVI? MVVM?)
- Module organization and dependencies
- Design patterns used throughout the codebase
- State management approach
- Navigation architecture
- Why these patterns were chosen

Without this documentation:

- New developers don't understand the "why" behind code organization
- Contributors may introduce code that doesn't follow established patterns
- Refactoring becomes risky without understanding architectural decisions
- The learning curve for the codebase is unnecessarily steep

## What Needs Documentation

### 1. High-Level Architecture Overview

Document the overall architectural approach:

- Is this Clean Architecture?
- Which layers exist? (Presentation, Domain, Data?)
- How do layers communicate?
- What are the key principles?

Include a diagram showing:

```
UI Layer (Features) → Domain Layer (Core) → Data Layer (Network/Database)
```

### 2. Modular Architecture

The Android app has a complex module structure that needs comprehensive documentation:

#### Module Categories

**Core Modules** (Business logic and data):

- `v2:core:api:*` - API interfaces for features to consume
- `v2:core:collections` - Collections domain logic
- `v2:core:database` - Local database (Room?)
- `v2:core:design` - Design system components
- `v2:core:domain` - Domain models and use cases
- `v2:core:home` - Home screen business logic
- `v2:core:library` - Library management
- `v2:core:media` - Media playback core
- `v2:core:miniplayer` - Miniplayer logic
- `v2:core:model` - Data models
- `v2:core:network` - Network layer
- `v2:core:player` - Player logic
- `v2:core:playlist` - Playlist management
- `v2:core:recent` - Recent history
- `v2:core:search` - Search functionality
- `v2:core:theme` - Theme implementation
- `v2:core:theme-api` - Theme interfaces

**Feature Modules** (UI and user-facing features):

- `v2:feature:collections` - Collections UI
- `v2:feature:home` - Home screen UI
- `v2:feature:library` - Library UI
- `v2:feature:miniplayer` - Miniplayer UI
- `v2:feature:player` - Player UI
- `v2:feature:playlist` - Playlist UI
- `v2:feature:search` - Search UI
- `v2:feature:settings` - Settings UI
- `v2:feature:splash` - Splash screen

Document for each module:

- **Purpose**: What is this module responsible for?
- **Dependencies**: What does it depend on?
- **Consumers**: Who depends on it?
- **Key classes**: Main entry points
- **Public API**: What does it expose?

### 3. The API Pattern

Multiple modules follow an `api` pattern (e.g., `core:api:search`, `core:api:player`). Document:

- What is the API pattern?
- Why separate API from implementation?
- How does this enable decoupling?
- When to create an API module vs direct dependency?

Example:

```
:v2:feature:search → :v2:core:api:search ← :v2:core:search
                           ↑
                    (Interface only)
```

### 4. Module Dependency Graph

Create a comprehensive dependency diagram showing:

- How modules depend on each other
- Dependency direction (features depend on core, never reverse)
- Shared dependencies
- Circular dependency prevention

Tools to consider:

- Gradle module dependency plugin
- Manual diagram (Mermaid, PlantUML)

### 5. Architectural Patterns

Document key patterns used:

#### State Management

- What pattern? (MVI? MVVM? Redux-like?)
- Where is state held? (ViewModel? StateFlow?)
- How do UI components observe state?
- Example of state flow from user action to UI update

#### Dependency Injection

- Using Hilt (confirmed from build.gradle.kts)
- Module organization
- Component hierarchy
- Scopes used (@Singleton, @ActivityScoped, etc.)
- How to provide dependencies
- How to inject dependencies

#### Repository Pattern

- Is there a repository layer?
- How do repositories coordinate data sources?
- Local vs remote data handling
- Caching strategy

#### Use Cases / Interactors

- Are use cases a separate layer?
- Where do they live?
- How are they invoked?
- Single responsibility per use case?

#### ViewModels

- ViewModel responsibilities
- How they interact with domain layer
- State management within ViewModels
- Lifecycle handling

### 6. Navigation Architecture

Document how navigation works:

- Using Jetpack Navigation Compose (confirmed from dependencies)
- Navigation graph structure
- Deep linking
- Passing arguments between screens
- Handling back stack
- Nested navigation

### 7. Data Flow

Document the complete data flow:

```
User Action → UI Event → ViewModel → Use Case → Repository →
  → API Service → Network → Response → Mapping → Domain Model →
  → Repository → Use Case → ViewModel → UI State → UI Update
```

Include:

- Error handling at each layer
- Loading states
- Success/failure flows

### 8. Database Architecture

Document:

- What database? (Room? SQLDelight?)
- Database schema
- Entities vs domain models
- DAOs and queries
- Migrations strategy

### 9. Design System

The `core:design` module suggests a design system. Document:

- Reusable UI components
- Theming system (see `core:theme`)
- Typography scale
- Color system
- Spacing/sizing system
- How to use design system components

### 10. Testing Architecture

Document testing patterns:

- Unit testing approach
- Integration testing
- UI testing with Compose
- Test doubles (mocks, fakes, stubs)
- Testing coroutines and flows
- Testing ViewModels
- Testing repositories

### 11. Code Organization Principles

Document the principles:

- **Separation of Concerns**: How is it enforced?
- **Dependency Rule**: Dependencies point inward
- **Single Responsibility**: Module boundaries
- **Open/Closed**: Extension points
- **Interface Segregation**: API modules

### 12. iOS Architecture

Document iOS app architecture:

- SwiftUI architecture patterns
- State management (ObservableObject? Combine?)
- Navigation approach
- Dependency injection
- How it differs from Android

## Structure

Create: `docs/docs/developer/architecture.md`

Suggested outline:

```markdown
# Architecture

## Overview

[High-level architectural approach]

## Principles

[Guiding principles]

## Layers

[Presentation, Domain, Data]

## Modular Architecture

### Module Categories

### Core Modules

### Feature Modules

### API Pattern

### Dependency Graph

## Design Patterns

### State Management

### Dependency Injection

### Repository Pattern

### Use Cases

### ViewModels

## Data Flow

[Complete flow diagram]

## Database

[Schema and patterns]

## Navigation

[Navigation architecture]

## Design System

[Component library]

## Testing

[Testing patterns]

## Platform Differences

[Android vs iOS]

## References
```

## Research Required

To write this documentation, investigate:

1. **Module structure**: Examine `androidApp/settings.gradle.kts` for all modules
2. **ViewModels**: Look for ViewModel pattern in feature modules
3. **Use Cases**: Check domain layer for use case classes
4. **Repositories**: Look for repository pattern in data layer
5. **State management**: Examine how StateFlow/LiveData is used
6. **DI setup**: Review Hilt modules and component configuration
7. **Database**: Find Room/SQLDelight configuration
8. **Navigation**: Check navigation setup in app module
9. **Design system**: Explore `core:design` module
10. **iOS patterns**: Review SwiftUI code structure

## Checklist

- [x] Document overall architectural approach
- [ ] Create module dependency diagram
    - [ ] Generate visual Mermaid or PlantUML diagram
    - [ ] Show all 33 modules (8 API, 15 core, 8 feature, 2 infrastructure)
    - [ ] Illustrate dependency direction (features → API ← core → domain → data)
    - [ ] Highlight circular dependency prevention
- [x] Document all core modules (purpose, dependencies, API)
- [x] Document all feature modules
- [x] Explain the API pattern in detail
- [x] Document state management approach with examples
- [x] Document dependency injection setup
- [x] Document repository pattern usage
- [x] Document use case layer (if exists) - Services used instead, documented
- [x] Document ViewModel responsibilities
- [x] Document navigation architecture (basic - could expand)
    - [ ] Add deep linking implementation details
    - [ ] Document argument passing patterns with examples
    - [ ] Document nested navigation handling
    - [ ] Document back stack management strategies
- [x] Document data flow with diagrams
- [ ] Document database architecture
    - [ ] Confirm database technology (appears to be Room based on entity references)
    - [ ] Document complete database schema
    - [ ] Document all entities and their relationships
    - [ ] Document DAOs and query patterns
    - [ ] Document migration strategy and versioning
    - [ ] Document FTS5 search implementation details
- [ ] Document design system usage
    - [ ] Document reusable UI components in `v2:core:design`
    - [ ] Document typography scale and usage
    - [ ] Document color system and theming
    - [ ] Document spacing/sizing tokens
    - [ ] Provide examples of using design system components
    - [ ] Document dark mode / theme switching
- [ ] Document testing architecture
    - [ ] Document unit testing approach and conventions
    - [ ] Document integration testing strategy
    - [ ] Document UI testing with Compose
    - [ ] Document test doubles strategy (mocks, fakes, stubs)
    - [ ] Document testing coroutines and flows
    - [ ] Document ViewModel testing patterns
    - [ ] Document repository testing patterns
    - [ ] Document code coverage requirements and tooling
- [ ] Document iOS architecture
    - [ ] Document SwiftUI architecture patterns used
    - [ ] Document state management (ObservableObject? Combine? @Observable?)
    - [ ] Document navigation approach (NavigationStack?)
    - [ ] Document dependency injection pattern
    - [ ] Document how iOS architecture parallels Android
    - [ ] Document iOS-specific differences and constraints
- [x] Add code examples for each pattern
- [x] Create decision records (why these patterns?)
- [x] Link to relevant code locations

## Success Criteria

A new developer should be able to:

- Understand the overall architecture in 30 minutes
- Know where to place new code
- Follow established patterns without guidance
- Understand module boundaries and dependencies
- Navigate the codebase efficiently
- Make architectural decisions consistent with existing patterns
- Refactor confidently within the established architecture

## Notes

This is the most complex documentation task because it requires deep understanding of the codebase. Consider creating this incrementally:

1. Start with high-level overview and module map
2. Document one complete feature as an example
3. Generalize patterns found in the example
4. Document remaining modules

The multi-module architecture is actually a strength of this project, but only if it's properly documented. Without documentation, it becomes a barrier to contribution.
