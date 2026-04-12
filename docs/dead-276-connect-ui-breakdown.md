# DEAD-276: Move Connect Controls into the Player

> Parent: DEAD-240 (Connect v2)
> Branch: `connect-v2`

## Goal

Replace the current Settings-based connect dialog with a Spotify-style player-integrated experience. The connect device picker should be a bottom sheet accessible from both the miniplayer and the full player, with a polished device list and a "Playing on..." tooltip on startup.

## Current State

| Platform | Connect UI location                | Miniplayer                       | Connect in player? |
| -------- | ---------------------------------- | -------------------------------- | ------------------ |
| iOS      | `SettingsScreen` → `ConnectScreen` | Yes (title, play/pause)          | No                 |
| Android  | `SettingsScreen` → `ConnectScreen` | Yes (artwork, title, play/pause) | No                 |
| Web      | `UserMenu` → `DeviceList`          | No (header player)               | No                 |

All three platforms have a working device list and transfer logic — the work is moving it, not building it from scratch.

---

## Subtasks

### 1. Connect button in the full player (iOS + Android)

**What:** Add a connect/devices icon button to the full player screen. Tapping it opens a bottom sheet with the device picker.

- iOS: `PlayerScreen.swift` — add button, present sheet
- Android: `PlayerScreen.kt` — add button, present `ModalBottomSheet`

**Design notes:**

- Use a speaker/cast-style icon (Spotify uses a monitor+speaker icon)
- When connected to a remote device, the icon should be highlighted/active

### 2. Connect button in the miniplayer (iOS + Android)

**What:** Add the same connect icon to the miniplayer row. Tapping opens the same bottom sheet.

- iOS: `MiniPlayerOverlay.swift`
- Android: `PlayerMiniPlayer.kt`

**Design notes:**

- Small icon, same style as the player one
- Keep the miniplayer compact — icon should fit without pushing other elements

### 3. Redesign device list as a bottom sheet (iOS + Android)

**What:** The current `ConnectScreen` is a full settings page. Redesign it as a reusable bottom sheet component.

- Each device row: icon on left (device type: phone/laptop/tablet), name on right
- The active/playing device row is highlighted (green accent or similar)
- Optional: animated "playing" indicator (like Spotify's equalizer bars) on the active row
- Whole row is tappable — tapping transfers playback to that device
- "This device" label for the local device

**Files to create/modify:**

- iOS: New `ConnectSheet.swift` (extract from `ConnectScreen.swift`)
- Android: New `ConnectSheet.kt` composable (extract from `ConnectScreen.kt`)

### 4. "Playing on..." tooltip / floating bubble (iOS + Android)

**What:** On app launch, if playback is active on a _different_ device, show a floating tooltip/bubble above the miniplayer that says "Playing on {deviceName}". Auto-dismisses after ~4 seconds, or on tap (tapping could open the connect sheet).

**Design notes:**

- Spotify does this as a speech-bubble callout pointing at the miniplayer
- Only show when: (a) the user opens the app, (b) another device is the active device, (c) something is loaded/playing
- Don't show if this device is the active device or if nothing is loaded

### 5. Web: Move device list into the player area

**What:** Move `DeviceList` from the `UserMenu` dropdown into the player controls area. Could be a popover/dropdown triggered by a connect icon in `HeaderPlayer.tsx`.

**Design notes:**

- Less dramatic change than mobile since web has a persistent header player
- A small connect icon in the player controls that opens a dropdown/popover with the device list

### 6. Volume control placeholder (all platforms)

**What:** Add a volume slider to the device list UI, but disable it / show it as "coming soon" or simply keep it in mind for the layout. Per the v2 architecture doc, remote volume is deferred — but the UI should have room for it.

**Status:** Low priority, can be deferred. Just ensure the device list layout leaves room.

### 7. Remove connect from Settings (iOS + Android)

**What:** Once the player-integrated UI is live, remove the old connect entry point from the Settings screen. This is the cleanup step — do it last.

---

## Suggested Order

```
3 → 1 → 2 → 4 → 5 → 7
          ↓
          6 (optional, anytime)
```

1. **Start with the bottom sheet** (subtask 3) — this is the core reusable component
2. **Wire it into the full player** (subtask 1) — primary entry point
3. **Wire it into the miniplayer** (subtask 2) — secondary entry point
4. **Add the tooltip** (subtask 4) — polish, depends on miniplayer placement
5. **Web** (subtask 5) — independent, can be done in parallel
6. **Remove old Settings path** (subtask 7) — cleanup, do last
7. **Volume placeholder** (subtask 6) — optional, layout consideration

## Platform Implementation Order

Per the connect-v2 pattern: **web first** (fastest iteration), then **iOS**, then **Android**. But since this is mostly a UI/UX task and the existing connect logic already works on all platforms, we could also do iOS and Android in parallel.

## Open Questions

- **Icon choice**: What specific icon for the connect button? Spotify uses a monitor+speaker. We could use `airplayaudio` (SF Symbols) on iOS and a cast/devices Material icon on Android.
  - Yes, that sounds fine
- **Active device indicator**: Green highlight? Animated equalizer bars? Both?
  - Take a look at what we do on each platform for the playing icon on the playlist. We should follow suit
- **Tooltip trigger**: Only on cold launch, or also on foreground resume?
  - Probably just cold launch
- **Keep Settings link?**: Should we keep a secondary path to connect in Settings for discoverability, or remove it entirely?
  - Probably remove it.
