# TODO: Architecture Patterns Documentation

**Status**: Phase 1 Complete ✅ | Platform-specific work remaining

## Checklist

### Phase 1: General Architecture (Complete ✅)
- [x] Overall architectural approach (Clean Architecture, layers, principles)
- [x] Core module documentation (purpose, dependencies, APIs)
- [x] Feature module documentation
- [x] API pattern explanation
- [x] State management patterns with examples
- [x] Dependency injection setup
- [x] Repository pattern
- [x] Service layer (instead of use cases)
- [x] ViewModel responsibilities
- [x] Data flow with diagrams
- [x] Database architecture (complete with entities, DAOs, migrations, FTS4)
- [x] Code organization principles
- [x] Code examples for patterns
- [x] Decision records (why these patterns)
- [x] Code location references

**Documentation**: `docs/docs/developer/architecture.md` ✅

### Phase 2: Android-Specific Documentation
- [ ] Android module dependency diagram (33 modules)
- [ ] Android navigation details (Navigation Compose)
- [ ] Android design system (Jetpack Compose components)
- [ ] Android testing architecture

### Phase 3: iOS-Specific Documentation
- [ ] iOS project structure
- [ ] iOS navigation (SwiftUI NavigationStack)
- [ ] iOS design system (SwiftUI components)
- [ ] iOS testing architecture

---

## Android-Specific Work

### Module Dependency Diagram
**File**: `docs/docs/developer/architecture/android-modules.md`

Create visual Mermaid diagram showing:
- All 33 modules (8 API, 15 core, 8 feature, 2 infrastructure)
- Dependency direction (features → API ← core → domain → data)
- Circular dependency prevention

### Navigation Architecture
**File**: `docs/docs/developer/architecture/android-navigation.md`

Document Navigation Compose specifics:
- Deep linking implementation
- Argument passing patterns with examples
- Nested NavHost handling
- Back stack management with NavController

### Design System
**File**: `docs/docs/developer/design-system/android.md`

Document `v2:core:design` module:
- Reusable Compose components
- Material Design 3 theming
- Typography scale and usage
- Color system and theming
- Spacing/sizing tokens
- Component usage examples
- Dark mode / theme switching

### Testing Architecture
**File**: `docs/docs/developer/testing/android.md`

Document Android testing:
- Unit testing conventions (JUnit, Hilt testing)
- Integration testing strategy
- UI testing with Compose Test APIs
- Test doubles strategy (mocks, fakes, stubs)
- Testing coroutines and flows
- ViewModel testing patterns
- Repository testing patterns
- Code coverage tooling

---

## iOS-Specific Work

### Project Structure
**File**: `docs/docs/developer/architecture/ios-structure.md`

Document current Xcode project:
- Xcode project organization
- File/folder structure
- Target configuration
- How it differs from Android's modular approach

### Navigation Architecture
**File**: `docs/docs/developer/architecture/ios-navigation.md`

Document SwiftUI navigation:
- NavigationStack/NavigationPath usage
- URL-based deep linking
- Argument passing with NavigationLink
- Back stack management

### Design System
**File**: `docs/docs/developer/design-system/ios.md`

Document SwiftUI design system:
- Reusable SwiftUI components
- SwiftUI styling and modifiers
- Typography scale
- Color system and theming
- Environment values for theming
- Dark mode / theme switching

### Testing Architecture
**File**: `docs/docs/developer/testing/ios.md`

Document iOS testing:
- XCTest conventions
- XCUITest for UI testing
- Testing async/await or Combine
- Test doubles and mocking
- ViewModel testing patterns

---

## Shared Design System Principles
**File**: `docs/docs/developer/design-system/principles.md`

Document platform-agnostic design decisions:
- Color token definitions
- Spacing/sizing scales
- Typography scales
- Design principles
- Accessibility guidelines

---

## Why This Structure?

The Android app uses a sophisticated 33-module Gradle architecture with dependency inversion via API modules. The iOS app currently uses a traditional monolithic Xcode project structure.

**Phase 1** documented the shared architectural concepts (Clean Architecture, patterns, principles) that apply to both platforms.

**Phase 2 & 3** will document platform-specific implementations, as the module structures and tools are completely different between Android and iOS.
