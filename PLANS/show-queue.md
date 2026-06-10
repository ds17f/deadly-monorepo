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
