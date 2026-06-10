# Show Queue (ADR-0010) — build status

Branch `show-queue`. Design spec: [`docs/adr/0010-show-queue.md`](../docs/adr/0010-show-queue.md).
Roadmap: §1 bullet 1 in [`ROADMAP.md`](ROADMAP.md).

## Status 2026-06-10: feature COMPLETE, deployed both devices, ready for PR
The full ADR-0010 feature (queue + auto-advance + countdown + interrupt
snackbar + Settings + "Your Queue" home rail + add-to-queue entry points) is
built, deployed, and verified through two on-device feedback rounds on iOS
("Damian's iPhone") and Android ("Pixel 6 - 16"). Build/install commands:
- iOS: `KEYCHAIN_PASSWORD="Hack your world5" make ios-remote-install`
- Android: `cd androidApp && ./gradlew installDebug`

Server sync + web queue is a separate follow-up project:
[`show-queue-sync.md`](show-queue-sync.md).

## Done
- **ADR-0010** + ROADMAP §1 rewrite.
- **Android store/service** — `play_queue` Room table (DB v26 + `MIGRATION_25_26`),
  `QueuedShow`/`PlayQueueEntity`/`PlayQueueDao`, modules `core:api:playqueue` +
  `core:playqueue` (`PlayQueueService` + impl, Hilt). End-of-show pref in
  `AppPreferences` (`endOfShowMode`/`endOfShowImmediate`, `END_OF_SHOW_*`).
- **Android UI** — Favorites **Queue** segment (`FavoritesTab.QUEUE`): hydrated
  via `ShowRepository` in `FavoritesViewModel`; tap = play + pop, Remove, Clear.
  "Add to Queue" in `ShowActionsBottomSheet`.
- **iOS store/service** — GRDB `play_queue` (migration `v15-play-queue`),
  `PlayQueueRecord`/`PlayQueueDAO`, `@Observable PlayQueueService` in
  `AppContainer`.
- **iOS UI** — Favorites **Queue** segment (`FavoritesTab.queue`): reorder via
  EditButton, swipe play/remove, Clear, tap → detail. "Add to Queue" in the show
  context menu.

## File map (where everything lives)

**Shared/design**
- `docs/adr/0010-show-queue.md` — binding design (unified model, configurable
  end-of-show, interrupt snackbar, resume snapshot, alternatives considered).
- `PLANS/ROADMAP.md` §1 bullet 1 — rewritten to the agreed model.

**Android** (branch `show-queue`)
- `androidApp/core/model/.../QueuedShow.kt` — domain model (id, showId,
  recordingId, resumeTrackIndex, resumePositionMs).
- `androidApp/core/database/.../entities/PlayQueueEntity.kt`,
  `.../dao/PlayQueueDao.kt` — Room store (append/insertHead/popHead/reorder).
- `androidApp/core/database/.../DeadlyDatabase.kt` — DB **v26**, entity + DAO
  registered, `MIGRATION_25_26` in the addMigrations chain.
- `androidApp/core/database/.../migration/DatabaseMigrations.kt` —
  `MIGRATION_25_26` (creates `play_queue`).
- `androidApp/core/database/.../di/DatabaseModule.kt` — `providePlayQueueDao`.
- `androidApp/core/database/.../AppPreferences.kt` — `endOfShowMode` /
  `endOfShowImmediate` + `END_OF_SHOW_OFF/QUEUE/QUEUE_HISTORY` +
  `END_OF_SHOW_COUNTDOWN_SECONDS`.
- `androidApp/core/api/playqueue/` + `androidApp/core/playqueue/` — new modules:
  `PlayQueueService` (interface) + `PlayQueueServiceImpl` + `PlayQueueModule`
  (Hilt). Registered in `settings.gradle.kts`; app depends on both in
  `app/build.gradle.kts`.
- `androidApp/feature/favorites/...` — `FavoritesViewModel.kt` (queue hydration +
  `addToQueue/removeFromQueue/moveInQueue/clearQueue`), `FavoritesScreen.kt`
  (`QueueTabContent`), `components/FavoritesBottomSheets.kt` (`onAddToQueue`),
  `core/model/.../FavoritesModels.kt` (`FavoritesTab.QUEUE`),
  `feature/favorites/build.gradle.kts` (added `core:api:playqueue`, `core:domain`).

**iOS** (branch `show-queue`)
- `iosApp/deadly/Core/Database/Records/PlayQueueRecord.swift`,
  `.../DAOs/PlayQueueDAO.swift` (needs `import Foundation`),
  `.../AppDatabase.swift` (migration `v15-play-queue` + `createPlayQueueTable`).
- `iosApp/deadly/Core/Service/PlayQueueService.swift` — `@Observable @MainActor`,
  `QueuedShowItem`, enqueue/enqueueNext/move/remove/popNext/peek.
- `iosApp/deadly/App/AppContainer.swift` — `playQueueService` property + construction.
- `iosApp/deadly/Core/Model/Favorites.swift` (`FavoritesTab.queue`),
  `iosApp/deadly/Feature/Favorites/FavoritesScreen.swift` (`queueContent`,
  `playFromQueue`, "Add to Queue" in the context menu).

## Done (deeper half, commits 69cb6ba9 + 9161a853 + d2762477)
- **End-of-show auto-advance + cancelable 15s countdown** (or immediate mode),
  both platforms, gated by the end-of-show pref. Guards: only fires after real
  playback (no cold-start/restored-ENDED autoplay), advance skips a queued
  entry for the show that just ended, Connect-aware (only the active device
  advances, notifies Connect via sendLoad, immediate-advance in a session).
- **Interrupt "Queue A" snackbar** with resume snapshot on play-now displace.
- **Settings UI** — "When a Show Ends" mode + timing pickers, both platforms;
  iOS pref added to mirror Android's `AppPreferences.endOfShowMode`.
- **Add-to-queue entry points** — playlist menu, home context menus, search
  actions sheet, show detail menu.
- **"Your Queue" home rail** (small cards, under Today In History, Settings
  toggle to hide). Tap opens the show page without autoplay.
- **Head-only pop** — playing a show only consumes it if it's the queue head;
  `removeByShowId` pops a queued show when played from anywhere.
- Player "Up Next" button was added then **removed** (d2762477) — the player
  screen keeps the within-show track queue; the show queue lives on Home +
  Favorites.

## Not done (deliberately left out of this branch)
1. **Android drag-reorder** — `FavoritesViewModel.moveInQueue` + service
   `move()` exist; no drag UI in `FavoritesScreen` (iOS has EditButton reorder).
2. **Server sync + web queue** — see [`show-queue-sync.md`](show-queue-sync.md).

## How to resume (after a context clear)

1. `git checkout show-queue`; read `docs/adr/0010-show-queue.md` (the spec).
2. Feature is complete + device-verified. Next step = push + PR
   (title must be a conventional commit, e.g.
   `feat(mobile/player): show queue — auto-advance, interrupts, Your Queue rail (ADR-0010)`).
3. To rebuild:
   - Android: `cd androidApp && ./gradlew installDebug` (device "Pixel 6").
   - iOS: `KEYCHAIN_PASSWORD="Hack your world5" make ios-remote-install`
     (device "Damian's iPhone"; simulator alt: `make ios-remote-sim`).
   - To see iOS compiler errors (the make target hides them behind `tail -20`),
     ssh the remote and grep `error:` directly — REMOTE_HOST/REMOTE_PATH in Makefile.

## Design decisions settled in conversation (all captured in ADR-0010)
- Unified "always playing from the queue" (Apple-Music Playing-Next), NOT a
  dormant/active two-state queue. Playing anything = insert-at-head + pop.
- Whole shows only; tracks/playlists deferred.
- Persistent local table; "transient" = self-consuming, not in-memory.
- No per-show resume exists (single global `last_played_track`), so a re-queued
  interrupted show must carry its own resume snapshot — that's why the entry has
  `resumeTrackIndex`/`resumePositionMs` (unused until the interrupt snackbar lands).
- End-of-show is configurable (off / queue / queue-then-history) × (15s
  countdown / immediate), default queue-then-history + countdown.
- Interrupt = replace by default + non-blocking "Queue A" snackbar (no modal).
