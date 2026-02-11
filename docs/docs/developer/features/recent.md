# Recent History

Automatic tracking of recently played shows for quick access and discovery.

---

## Overview

Recent History automatically tracks which shows users have listened to, maintaining a chronological list for quick re-access. Unlike Library (manual bookmarking), Recent History is passive and automatic, capturing listening behavior without user action.

**Purpose**: Quick access to recently played shows, listening history tracking

**Key Responsibilities**:
- Automatically track show plays based on meaningful listening
- Maintain chronological list (most recent first)
- Use UPSERT pattern (one record per show)
- Provide reactive updates when new shows are played
- Support privacy controls (hide/clear history)

---

## User Experience

### Recent Shows Display

**Typically Shown On**:
- Home screen (e.g., "Continue Listening" section)
- Dedicated Recent History screen (future)
- Player/Show Detail (recent badge)

**Display Format**:
- 2x4 grid on home screen (8 shows)
- List format on dedicated screen (20+ shows)
- Show cards with: date, venue, location

**Order**: Most recently played first

---

### Automatic Tracking

**User Perspective**: Invisible, automatic

**When Tracked**:
- User plays a track from a show
- Track plays for meaningful duration (see threshold below)
- Show is added/updated in recent history

**No User Action Required**: Happens in background during normal listening

---

### Privacy Controls

**Hide from Recent** (future):
- Long press show → "Hide from Recent"
- Removes show from history
- Doesn't affect library or playback

**Clear All History** (settings):
- Delete all recent show tracking
- Fresh start
- Privacy reset

---

## Key Behaviors

### Meaningful Play Threshold

**Hybrid Threshold**: Track must meet ONE of these criteria:

1. **30 seconds of playback** - Absolute minimum
2. **25% of track duration** - Relative minimum

**Rationale**:
- Short tracks (1-2 min): 25% ensures meaningful listen
- Long tracks (20+ min): 30 seconds prevents accidental skips from counting
- Balances accuracy across track lengths

**Examples**:
- 1-minute track: Must play 25% = 15 seconds (uses 30-second minimum)
- 3-minute track: Must play 25% = 45 seconds OR 30 seconds (whichever first)
- 20-minute track: Must play 25% = 5 minutes OR 30 seconds (30 seconds wins)

---

### UPSERT Pattern

**One Record Per Show**: Each show appears only once in recent history

**On Play**:
- **Show exists**: Update `lastPlayedTimestamp`, increment `playCount`
- **Show new**: Insert with current timestamp, `playCount = 1`

**Why UPSERT**: Recent history shows "what I've listened to recently", not "every time I listened"

**Benefit**: Clean list without duplicates, most recent play determines order

---

### Show-Level vs Track-Level

**Granularity**: Recent History tracks at show level, not track level

**Observation**: Service observes MediaController track plays, converts to show plays

**Deduplication**: Playing multiple tracks from same show = one show entry (timestamp updates)

**Rationale**: Users think "I listened to Cornell '77", not "I listened to Scarlet Begonias from Cornell '77"

---

### Real-Time Updates

**Reactive State**: Recent shows list exposed as `StateFlow`

**Updates**:
- Track passes meaningful threshold → Show added/updated
- Recent shows StateFlow emits new list
- UI automatically updates

**No Polling**: Reactive observation ensures instant updates

---

## Integration with Other Features

### Home Screen Integration

**Display**: "Continue Listening" or "Recent Shows" section

**Count**: Typically 8 shows in 2x4 grid

**Action**: Tap show → Navigate to show detail

---

### Player Integration

**Tracking**: Player plays tracks → Recent History observes

**Badge** (future): Show detail could display "Recently Played" badge

---

### Library Integration

**Relationship**: Completely separate

**Difference**:
- Recent: Automatic, temporary, based on actual plays
- Library: Manual, permanent, based on user preference

**Common Flow**: User plays show → Appears in recent → Likes it → Adds to library

---

### Recommendations** (future)

**Use Case**: "More like recent shows" recommendations

**Data**: Recent history provides listening behavior for recommendation algorithms

---

## Design Decisions

### Why Automatic Tracking?

**Decision**: Track automatically without user action

**Rationale**:
- Reduces friction (no "add to recent" button)
- Captures actual behavior, not stated preference
- Users expect music apps to remember what they listened to
- Industry standard (Spotify, Apple Music)

---

### Why UPSERT Pattern?

**Decision**: One record per show, update on re-play

**Rationale**:
- Recent history is "what I've been listening to", not event log
- Prevents list clutter from re-plays
- Most recent play is most relevant
- Play count still available for analytics

---

### Why Show-Level, Not Track-Level?

**Decision**: Track shows, not individual tracks

**Rationale**:
- Users think in terms of shows ("I listened to Cornell '77")
- Track-level would create massive lists
- Show is the natural unit for Grateful Dead content
- Tracks within show are related, not separate content

---

### Why Hybrid Threshold?

**Decision**: 30 seconds OR 25% of track, whichever first

**Rationale**:
- Absolute threshold (30s) handles long tracks well
- Relative threshold (25%) handles short tracks well
- Hybrid captures both edge cases
- Balances false positives (accidental skips) and false negatives (short listens)

---

### Why Privacy Controls?

**Decision**: Allow users to hide/clear history

**Rationale**:
- Privacy concerns (shared devices)
- Embarrassment factor (experimenting with different shows)
- User control over data
- Legal/ethical best practice

---

## State Model

### Service State

**StateFlow**:
```kotlin
val recentShows: StateFlow<List<Show>>
```

**List Properties**:
- Ordered by `lastPlayedTimestamp` descending
- Typically limited to 8-10 shows (configurable)
- Automatically updates when new shows played

---

### Database Model

**RecentShowEntity**:
- `showId: String` - Primary key
- `lastPlayedTimestamp: Long` - Most recent play time
- `playCount: Int` - Number of times played
- `addedToRecentAt: Long` - First play time (historical)

**Index**: `lastPlayedTimestamp` for efficient ordering

---

## Technical Considerations

### Observation Architecture

**Flow**:
1. MediaControllerRepository emits `currentTrackInfo` StateFlow
2. RecentShowsService observes StateFlow
3. On track change, checks if previous track met threshold
4. If yes, extracts `showId` and calls `recordShowPlay()`
5. Database updates, StateFlow emits new list

**Challenge**: Avoid tracking every position update

**Solution**: Only check threshold on track change events

---

### Threshold Calculation

**Challenge**: Accurately track playback duration

**Approach**:
- Record track start time on play
- On track change, calculate elapsed time
- Compare against hybrid threshold
- Account for seeks (reset start time)

---

### Performance

**Challenge**: Frequent database writes

**Approach**:
- UPSERT is fast (single UPDATE or INSERT)
- Indexed by showId for fast lookups
- Limit query results (8-10 shows)
- StateFlow only emits on actual changes

---

## Error Handling

### Database Write Failed

**Scenario**: Database unavailable, disk full

**Response**:
- Log error
- Continue playback (don't interrupt)
- Retry on next track change

---

### Show Metadata Missing

**Scenario**: Track plays but showId unavailable

**Response**:
- Skip tracking (can't track without showId)
- Log warning for debugging
- Continue playback normally

---

## Code References

**Android Implementation**:
- Service Interface: `androidApp/v2/core/api/recent/src/main/java/com/deadly/v2/core/api/recent/RecentShowsService.kt:26`
- Database Entity: `androidApp/v2/core/database/src/main/java/com/deadly/v2/core/database/entities/RecentShowEntity.kt:1`

---

## See Also

- [Player](player.md) - Playback generates recent history
- [Library](library.md) - Manual bookmarking (contrast with automatic recent)
- [Home](home.md) - Recent shows display
- [Recent Shows Entity](../database/entities/recent-shows.md) - Database schema
