# DEAD-119: Build Settings Screen (iOS)

## Context
The iOS app has a basic SettingsScreen embedded in MainNavigation.swift. The Android app has a full-featured settings screen with cache management, data management, preferences, and a separate About screen. This story brings the iOS settings to parity.

## Files to Modify

| File | Action |
|------|--------|
| `iosApp/deadly/Core/Service/AppPreferences.swift` | ADD `forceOnline` preference |
| `iosApp/deadly/Core/Service/NetworkMonitor.swift` | MODIFY to respect `forceOnline` override |
| `iosApp/deadly/Feature/Settings/SettingsScreen.swift` | CREATE — extracted + enhanced settings |
| `iosApp/deadly/Feature/Settings/AboutView.swift` | CREATE — About screen matching Android |
| `iosApp/deadly/App/MainNavigation.swift` | TRIM — remove inline SettingsScreen + LibraryExportShareSheet |

## Implementation Steps

### 1. Add `forceOnline` to AppPreferences
- Add `forceOnline: Bool` property with UserDefaults persistence (default: false)
- Follow existing `showOnlyRecordedShows` pattern

### 2. Wire `forceOnline` into NetworkMonitor
- Accept AppPreferences reference
- When `forceOnline` is true, `isConnected` returns true regardless of NWPathMonitor

### 3. Extract and enhance SettingsScreen
Move from MainNavigation.swift to Feature/Settings/SettingsScreen.swift. Sections:
- **Migration**: Import/Export library (existing functionality)
- **Cache Management**: Clear Archive Cache with confirmation
- **Data Management**: Force Re-Import with confirmation
- **Preferences**: Hide shows without recordings, Force Online Mode
- **About**: NavigationLink to AboutView

### 4. Create AboutView
Match Android's AboutScreen content with iOS-native List style:
- App header with version from Bundle
- Support the Archive (donate link)
- Our Mission
- Streaming & Recording Access Policy
- The Band's Taping & Sharing Tradition (with reference links)
- Internet Archive Collection Policy
- How This App Handles Streaming
- Offline Listening policy
- Official Commercial Releases
- Respect for Artists & Rights Holders

### 5. Trim MainNavigation.swift
Remove SettingsScreen struct and LibraryExportShareSheet from MainNavigation.swift.

## Verification
1. `make ios-build` compiles
2. Settings tab loads with all sections
3. Preferences persist across restart
4. Force Online override works
5. Cache/data buttons show confirmation and execute
6. About screen displays with correct version and all sections
