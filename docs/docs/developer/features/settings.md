# Settings

App configuration and management tools.

---

## Overview

The Settings screen provides access to app configuration options, theme management, cache control, and development tools. It's organized into logical sections for easy navigation.

**Purpose**: App configuration, theme management, and data control

**Key Responsibilities**:
- Theme import and management
- Cache clearing
- Data management (delete database, data files)
- App version control (V1/V2 toggle)

---

## User Experience

### Settings Screen Layout

**Vertical Scroll Structure**:
- Card-based sections
- Each section groups related settings
- Clear section titles
- Consistent button styling

**Sections**:
1. **Themes**
2. **Cache Management**
3. **Data Management**
4. **App Version**

---

## Settings Sections

### Themes Section

**Purpose**: Manage app visual themes

**Controls**:

1. **Theme Chooser**
    - Import custom themes from files
    - Select active theme
    - Theme preview

2. **Clear All Themes Button**
    - Deletes all imported themes
    - Restarts app to restore default theme
    - Confirmation dialog before action
    - Red/destructive styling

**Workflow**:
- User imports theme file → Theme becomes available
- User selects theme → App applies theme
- User clears themes → All customs deleted, app restarts with default

---

### Cache Management Section

**Purpose**: Manage temporary cached data

**Controls**:

1. **Clear Archive Cache Button**
    - Deletes cached track lists and reviews from Archive.org
    - Data re-downloaded when needed
    - Frees up storage space
    - Confirmation dialog
    - Secondary styling (less destructive)

**Use Case**: Free up storage, troubleshoot stale data

---

### Data Management Section

**Purpose**: Manage persistent app data

**Controls**:

1. **Delete Data.zip Button**
    - Deletes the data.zip file (contains show database)
    - App will re-download show data when needed
    - Red/destructive styling
    - Confirmation dialog
    - Success/failure feedback dialog

2. **Delete Database Files Button**
    - Deletes all deadly_db* files (Room database)
    - Removes all stored shows, favorites, app data
    - Most destructive action
    - Red/destructive styling
    - Confirmation dialog with strong warning
    - Success/failure feedback dialog

**Use Case**: Factory reset, troubleshoot corruption, free space

---

### App Version Section

**Purpose**: Toggle between V1 and V2 app implementations

**Controls**:

1. **Back to V1 App Button**
    - Restarts app with V1 implementation
    - Development/testing feature
    - Confirmation dialog
    - Primary styling (not destructive)

**Use Case**: Compare V1/V2, fallback if V2 has issues

**Note**: Temporary feature for development, will be removed when V2 is stable

---

## Key Behaviors

### Confirmation Dialogs

**All Destructive Actions**: Show confirmation dialog before executing

**Dialog Structure**:
- Title: Action name
- Body: Explanation of what will happen
- Confirm button: Proceeds with action (red for destructive)
- Cancel button: Dismisses dialog

**Purpose**: Prevent accidental data loss

---

### Feedback Dialogs

**Data Deletion Actions**: Show success/failure feedback after completion

**Dialog Structure**:
- Title: "Data.zip Deleted" or "Deletion Failed"
- Body: Success message or error explanation
- OK button: Dismisses dialog

**Purpose**: User confidence that action completed (or awareness of failure)

---

### App Restarts

**Some Actions Require Restart**:
- Clear all themes → Restart to load default
- Back to V1 app → Restart with V1 implementation

**Implementation**: Use `exitProcess(0)` to terminate app

**User Experience**: App closes, user re-opens manually

---

## Design Decisions

### Why Card-Based Sections?

**Decision**: Group settings in elevated cards instead of flat list

**Rationale**:
- Clear visual grouping
- Easier scanning
- Consistent with Material Design 3
- Separates related controls

---

### Why Confirmation Dialogs?

**Decision**: Require confirmation for all destructive actions

**Rationale**:
- Prevents accidental data loss
- User has moment to reconsider
- Industry best practice
- Legal/ethical requirement

---

### Why Clear vs Delete Terminology?

**Decision**: "Clear" for cache, "Delete" for persistent data

**Rationale**:
- "Clear" implies temporary/recoverable
- "Delete" implies permanent/destructive
- Matches user expectations
- Consistent with other apps

---

### Why Include V1 Toggle?

**Decision**: Temporary feature to switch back to V1

**Rationale**:
- Development safety net (if V2 breaks)
- A/B comparison for testing
- User escape hatch during migration
- Will be removed when V2 is stable

---

## State Model

### No Complex State

**Settings screen has minimal state**:
- Confirmation dialog visibility (per action)
- Feedback dialog visibility (per action)
- Success/failure status (for feedback)

**Why Simple**: Settings are immediate actions, not ongoing state

---

## Error Handling

### File Deletion Failed

**Scenario**: File doesn't exist, in use, permissions error

**Response**:
- Show failure dialog with explanation
- Log error for debugging
- Don't crash app

---

### Theme Import Failed

**Scenario**: Invalid theme file, corrupted data

**Response**:
- Show error message
- Don't apply broken theme
- Log error details

---

## Technical Considerations

### File System Access

**Challenge**: Safely delete files without corrupting app state

**Approach**:
- Close database connections before deletion (if needed)
- Use try-catch to handle errors gracefully
- Verify file exists before attempting delete

---

### Cache Directory Structure

**Archive Cache Location**: `context.cacheDir/archive`

**Purpose**: Temporary storage for track lists, reviews

**Clearing**: Delete entire directory recursively

---

### Database File Locations

**Database Files**: `context.getDatabasePath("deadly_db")`

**Pattern**: deadly_db, deadly_db-shm, deadly_db-wal

**Deletion**: Must delete all related files (main + journal)

---

### App Restart Implementation

**Current Approach**: `exitProcess(0)`

**Limitation**: User must manually reopen app

**Alternative** (future): Use `ProcessPhoenix` library for automatic restart

---

## Code References

**Android Implementation**:
- Screen: `androidApp/v2/feature/settings/src/main/java/com/deadly/v2/feature/settings/screens/main/SettingsScreen.kt:27`
- ViewModel: `androidApp/v2/feature/settings/src/main/java/com/deadly/v2/feature/settings/screens/main/models/SettingsViewModel.kt:1`

---

## See Also

- [Architecture](../architecture.md) - V2 architecture overview
- [Database](../database/overview.md) - Database structure being managed
- [Themes](../../user/themes.md) - Theme system documentation (if exists)
