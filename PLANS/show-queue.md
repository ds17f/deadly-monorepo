# Show Queue (ADR-0010) — build status

Branch `show-queue`. Design spec: [`docs/adr/0010-show-queue.md`](../docs/adr/0010-show-queue.md).
Roadmap: §1 bullet 1 in [`ROADMAP.md`](ROADMAP.md).

## Deployed 2026-06-09 (both physical devices)
A working **visible show queue** is on iOS ("Damian's iPhone") and Android
("Pixel 6 - 16"). Build/install commands:
- iOS: `KEYCHAIN_PASSWORD="Hack your world5" make ios-remote-install`
- Android: `cd androidApp && ./gradlew installDebug`

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

## Not done (the deeper half — only queue + add + play-pop shipped)
1. **End-of-show auto-advance + cancelable countdown** — neither platform.
   Playing from the queue plays + pops, but a show *ending* does nothing yet.
   Gate by the end-of-show pref (off / queue / queue-then-history). Hooks:
   Android `MediaControllerRepository.playbackState == ENDED`; iOS StreamPlayer
   completion. **Android caveat:** `PlaylistService.playTrack` is a stub — the
   real play pipeline is in feature `PlaylistViewModel.playAll`; background
   advance needs a service-level "play show by id" extraction. iOS
   `playlistService.loadShow + playTrack` already plays (easier first target).
2. **Interrupt "Queue A" snackbar** (resume snapshot) — `enqueueNext` + resume
   columns exist but are unused.
3. **Player queue button** (open the queue from now-playing) — neither.
4. **Settings UI** for end-of-show behavior — Android pref exists, no UI; iOS has
   no pref yet.
5. **Android reorder** — service `move()` exists; no drag UI (iOS has it).
6. **More add-to-queue entry points** — search rows, player menu, collections,
   shared-link open.

## How to resume (after a context clear)

1. `git checkout show-queue` (9 commits, local only, nothing pushed; tree clean).
2. Read `docs/adr/0010-show-queue.md` first — it is the spec.
3. Both apps are already installed on the physical devices (see top). To rebuild:
   - Android: `cd androidApp && ./gradlew installDebug` (device "Pixel 6").
   - iOS: `KEYCHAIN_PASSWORD="Hack your world5" make ios-remote-install`
     (device "Damian's iPhone"; simulator alt: `make ios-remote-sim`).
   - To see iOS compiler errors (the make target hides them behind `tail -20`),
     ssh the remote and grep `error:` directly — REMOTE_HOST/REMOTE_PATH in Makefile.
4. Next task = **auto-advance** (item 1 below). Start with **iOS** — its
   `playlistService.loadShow + playTrack` already plays, and `PlaybackState.ended`
   exists (`Packages/SwiftAudioStreamEx/.../Models/PlaybackState.swift`); observe
   `streamPlayer.playbackState == .ended`, then `playQueueService.popNext()` and
   play it (gate by an end-of-show setting; add the iOS pref to mirror Android's
   `AppPreferences.endOfShowMode`). Android is harder: `PlaylistService.playTrack`
   is a stub — extract a service-level "play show by id" from feature
   `PlaylistViewModel.playAll`, then observe
   `MediaControllerRepository.playbackState == ENDED`.

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
