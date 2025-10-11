# Architecture

This document describes the architectural patterns and design principles used in the Deadly application. The architecture is designed to be platform-agnostic, supporting both Android (Kotlin) and iOS (Swift) implementations.

## Overview

The application follows **Clean Architecture** principles with a **Service-based pattern** for business logic orchestration. The architecture is organized around three core principles:

1. **Dependency Inversion** - High-level modules (features) never depend on low-level modules (implementations). Both depend on abstractions (interfaces).
2. **Separation of Concerns** - Each module has a single, well-defined responsibility.
3. **Reactive State Management** - State flows unidirectionally from data sources to UI through reactive streams.

### Why This Architecture?

- **Scalability** - New features can be added without modifying existing code
- **Testability** - Business logic can be tested independently of UI and data layers
- **Platform Portability** - Core business logic can be shared across Android and iOS
- **Maintainability** - Clear boundaries between modules make the codebase easier to understand
- **Independent Development** - Teams can work on different features without conflicts

## Module Architecture

The application uses a modular architecture with clear naming conventions and dependency rules.

### Module Patterns

#### API Modules: `v2:core:api:{feature}`

**Purpose**: Define interface contracts without implementation details.

**Example**: `v2:core:api:search` contains `SearchService` interface

**Dependencies**: Only models and other API interfaces

**Why**: Enables dependency inversion - feature modules depend on interfaces, not implementations. This allows:
- Testing with stub implementations
- Swapping implementations without changing consumers
- Preventing circular dependencies between features

**Code Reference**: `androidApp/v2/core/api/search/src/main/java/com/deadly/v2/core/api/search/SearchService.kt:16`

#### Core Implementation Modules: `v2:core:{feature}`

**Purpose**: Implement business logic defined in corresponding API modules.

**Example**: `v2:core:search` contains `SearchServiceImpl` implementing `SearchService`

**Dependencies**: Corresponding API module, model, domain, database, network modules

**Rules**:
- Must implement an interface from an API module
- Never depends on feature modules
- Can depend on other core modules and their APIs

**Code Reference**: `androidApp/v2/core/search/src/main/java/com/deadly/v2/core/search/SearchServiceImpl.kt:25`

#### Feature Modules: `v2:feature:{feature}`

**Purpose**: Provide user interface and presentation logic (ViewModels, Compose screens).

**Example**: `v2:feature:home` contains home screen UI and `HomeViewModel`

**Dependencies**: **Only** core API modules, design system, and theme modules

**Critical Rule**: Feature modules **never depend on**:
- Other feature modules
- Core implementation modules (only APIs)
- Database or network modules directly

**Code Reference**: `androidApp/v2/feature/home/src/main/java/com/deadly/v2/feature/home/screens/main/HomeViewModel.kt:24`

#### Shared Infrastructure Modules

**`v2:core:model`** - Domain models shared across all modules

**`v2:core:domain`** - Domain interfaces (repositories) that data layer implements

**`v2:core:database`** - Database layer (entities, DAOs, repository implementations)

**`v2:core:network`** - Network layer (API clients, DTOs)

**`v2:core:design`** - Reusable UI components and design system

**`v2:core:theme`** - Theme system implementation

**`v2:core:theme-api`** - Theme interfaces for dependency inversion

### The API Pattern

The API pattern is the key to maintaining clean dependencies:

```
Feature depends on API ← implemented by Core

v2:feature:search → v2:core:api:search ← v2:core:search
     (UI)              (interface)        (implementation)
```

**Benefits**:
1. Features can be compiled and tested independently
2. Multiple implementations (real, stub, test) can coexist
3. Circular dependencies are impossible
4. Easier to port to other platforms (Swift protocols match Kotlin interfaces)

### Module Dependency Rules

1. **Features → API only** - Features only see interfaces
2. **Core → implements API** - Core modules provide implementations
3. **Domain → defines contracts** - Repository interfaces in domain layer
4. **Data → implements domain** - Database/network implement repository contracts
5. **No upward dependencies** - Lower layers never know about higher layers

### Module List

**API Interfaces** (8 modules):
- `v2:core:api:collections`, `v2:core:api:home`, `v2:core:api:library`
- `v2:core:api:miniplayer`, `v2:core:api:player`, `v2:core:api:playlist`
- `v2:core:api:recent`, `v2:core:api:search`

**Core Implementations** (15 modules):
- `v2:core:collections`, `v2:core:home`, `v2:core:library`
- `v2:core:miniplayer`, `v2:core:player`, `v2:core:playlist`
- `v2:core:recent`, `v2:core:search`
- `v2:core:database`, `v2:core:domain`, `v2:core:media`, `v2:core:model`
- `v2:core:network`, `v2:core:network:archive`
- `v2:core:theme`, `v2:core:theme-api`

**Feature UI** (8 modules):
- `v2:feature:collections`, `v2:feature:home`, `v2:feature:library`
- `v2:feature:miniplayer`, `v2:feature:player`, `v2:feature:playlist`
- `v2:feature:search`, `v2:feature:settings`, `v2:feature:splash`

**Infrastructure** (2 modules):
- `v2:core:design` - Design system components
- `v2:app` - Main application and navigation

## Architectural Layers

The application is organized into three architectural layers following Clean Architecture principles:

### Presentation Layer

**Responsibility**: Display data and capture user interactions.

**Components**:
- **ViewModels** - Manage UI state and orchestrate business logic calls
- **Composable UI Functions** - Render state and emit user events
- **UI State Models** - Immutable data classes representing screen state

**Pattern**: ViewModels observe reactive streams from Services and transform them into UI state:

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeService: HomeService  // Depends on API interface
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState.initial())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeHomeService()  // Reactive subscription
    }
}
```

**Code Reference**: `androidApp/v2/feature/home/src/main/java/com/deadly/v2/feature/home/screens/main/HomeViewModel.kt:24`

**Dependencies**: Only API interfaces, never implementations

### Domain Layer

**Responsibility**: Define business rules and data contracts.

**Components**:
- **Service Interfaces** - Define business operations (in API modules)
- **Repository Interfaces** - Define data access contracts
- **Domain Models** - Pure business entities with no framework dependencies

**Pattern**: Services orchestrate business logic by coordinating repositories and other services:

```kotlin
interface SearchService {
    val searchResults: Flow<List<SearchResultShow>>
    val searchStatus: Flow<SearchStatus>

    suspend fun updateSearchQuery(query: String): Result<Unit>
}
```

**Code Reference**: `androidApp/v2/core/api/search/src/main/java/com/deadly/v2/core/api/search/SearchService.kt:16`

**Why Services Instead of Use Cases**: Services provide cohesive feature APIs rather than single-responsibility use cases, reducing boilerplate while maintaining testability.

### Data Layer

**Responsibility**: Provide and persist data from various sources.

**Components**:
- **Repository Implementations** - Implement domain repository interfaces
- **DAOs** - Database access objects (database-specific)
- **API Clients** - Network communication (network-specific)
- **Mappers** - Convert between data entities and domain models

**Pattern**: Repositories act as boundaries, converting data entities to domain models:

```kotlin
@Singleton
class ShowRepositoryImpl @Inject constructor(
    @V2Database private val showDao: ShowDao,
    private val showMappers: ShowMappers
) : ShowRepository {

    override suspend fun getShowById(showId: String): Show? {
        return showDao.getShowById(showId)?.let { entity ->
            showMappers.entityToDomain(entity)  // Boundary conversion
        }
    }
}
```

**Code Reference**: `androidApp/v2/core/database/src/main/java/com/deadly/v2/core/database/repository/ShowRepositoryImpl.kt:24`

**Key Principle**: Entity-to-domain conversion happens at the repository boundary. Domain layer only sees clean domain models, never database entities or network DTOs.

## Data Flow

Data flows unidirectionally through the architecture, following reactive principles:

### Complete Flow Example: Search Feature

```
1. User types in search box
   ↓
2. UI emits event to ViewModel
   ↓
3. ViewModel calls SearchService.updateSearchQuery()
   ↓
4. Service calls ShowRepository.searchShows()
   ↓
5. Repository queries ShowSearchDao (FTS5 database)
   ↓
6. DAO returns entities
   ↓
7. Repository converts entities → domain models
   ↓
8. Service transforms to SearchResultShow
   ↓
9. Service emits to searchResults reactive stream
   ↓
10. ViewModel observes stream and updates UI state
   ↓
11. UI recomposes with new state
```

**Code Flow**:
- UI: Search text field in `SearchScreen`
- ViewModel: `SearchViewModel.kt` observes `SearchService.searchResults`
- Service: `SearchServiceImpl.kt:49` implements search logic
- Repository: `ShowRepositoryImpl.kt` queries database
- DAO: `ShowSearchDao.kt` executes FTS5 query
- Mapper: `ShowMappers.kt` converts entities to domain

### State Management Pattern

State flows through reactive streams (StateFlow in Kotlin, similar to Combine Publishers in Swift):

**Service Layer**:
```kotlin
private val _searchResults = MutableStateFlow<List<SearchResultShow>>(emptyList())
val searchResults: Flow<List<SearchResultShow>> = _searchResults.asStateFlow()
```

**ViewModel Layer**:
```kotlin
viewModelScope.launch {
    searchService.searchResults.collect { results ->
        _uiState.value = _uiState.value.copy(searchResults = results)
    }
}
```

**UI Layer**:
```kotlin
val uiState by viewModel.uiState.collectAsState()
// UI automatically recomposes when state changes
```

### Error Handling

Errors are handled at each layer using Result types:

- **Service Layer**: Returns `Result<Unit>` or `Result<Data>` for operations
- **ViewModel Layer**: Transforms failures into UI-friendly error messages
- **UI Layer**: Displays error states to users

Example:
```kotlin
val result = searchService.updateSearchQuery(query)
if (result.isFailure) {
    _uiState.value = _uiState.value.copy(error = "Search failed")
}
```

## Key Architectural Patterns

### 1. Reactive State Management

State is managed through reactive streams that emit updates over time.

**Characteristics**:
- Immutable state objects
- Unidirectional data flow
- Declarative UI that reacts to state changes
- No manual UI updates

**Implementation**: Kotlin uses StateFlow, Swift would use Combine or @Observable

### 2. Repository Pattern

Repositories abstract data sources and provide a clean domain API.

**Responsibilities**:
- Abstract data source details (database, network, cache)
- Convert between data entities and domain models
- Coordinate multiple data sources
- Provide reactive streams of data

**Code Reference**: `androidApp/v2/core/database/src/main/java/com/deadly/v2/core/database/repository/ShowRepositoryImpl.kt:24`

### 3. Service Pattern

Services orchestrate business logic by coordinating repositories and other services.

**Why Services Instead of Use Cases**:
- Cohesive feature APIs rather than many single-purpose classes
- Natural fit for reactive streams (services maintain state)
- Simpler dependency graphs
- Easier to test (mock one service vs many use cases)

**Example**: `SearchService` coordinates search logic, FTS5 queries, result ranking, and state management in one cohesive API.

**Code Reference**: `androidApp/v2/core/api/search/src/main/java/com/deadly/v2/core/api/search/SearchService.kt:16`

### 4. Dependency Injection

All dependencies are injected through constructor injection.

**Principles**:
- Components never create their own dependencies
- Dependencies are interfaces, not implementations
- Scopes control lifecycle (Singleton for services, scoped for ViewModels)

**Platform Notes**:
- Kotlin: Uses Hilt (Dagger-based) with `@Inject`, `@HiltViewModel`, `@Module`
- Swift: Can use any DI pattern (property injection, factory pattern, etc.)

### 5. Entity-to-Domain Mapping

Data entities are converted to domain models at repository boundaries.

**Why**:
- Domain layer has no database/network dependencies
- Can change database schema without affecting business logic
- Domain models can have computed properties and business methods
- Platform portability (entities are platform-specific, domain models are not)

**Pattern**:
```kotlin
// Data Entity (Room-specific)
@Entity data class ShowEntity(...)

// Domain Model (pure Kotlin/portable)
data class Show(...)

// Mapper (repository boundary)
class ShowMappers {
    fun entityToDomain(entity: ShowEntity): Show = ...
}
```

**Code Reference**: `androidApp/v2/core/database/src/main/java/com/deadly/v2/core/database/mappers/ShowMappers.kt`

### 6. Navigation Architecture

Navigation is centralized with feature-owned subgraphs.

**Structure**:
- Main navigation coordinator in app module
- Each feature owns its navigation graph
- Features expose navigation builders
- Deep linking and argument passing handled declaratively

**Code Reference**: `androidApp/v2/app/src/main/java/com/deadly/v2/app/MainNavigation.kt:61`

**Pattern**: Features provide `{feature}Graph(navController)` functions that app module calls.

## Adding a New Feature

To add a new feature following this architecture:

1. **Create API module** - `v2:core:api:{feature}` with interface
2. **Create core module** - `v2:core:{feature}` with implementation
3. **Create feature module** - `v2:feature:{feature}` with UI
4. **Define navigation** - Add navigation graph in feature module
5. **Wire in app module** - Import feature graph in `MainNavigation.kt`

Dependencies flow: Feature → API ← Core → Domain → Data

## Platform Implementation Notes

### Kotlin (Android)

- Reactive streams: StateFlow/Flow
- DI: Hilt with @Inject annotations
- Database: Room with @Entity/@Dao
- UI: Jetpack Compose
- Navigation: Navigation Compose

See `architecture-kotlin.md` for Kotlin-specific details.

### Swift (iOS)

- Reactive streams: Combine Publishers or @Observable
- DI: Property injection or factory pattern
- Database: Core Data or SQLite
- UI: SwiftUI
- Navigation: NavigationStack

See `architecture-swift.md` for Swift-specific details (to be written during porting).

## Further Reading

- Clean Architecture by Robert C. Martin
- Kotlin Multiplatform Architecture Patterns
- SwiftUI Architecture Best Practices
