# Show backlog + collections (plan)

Roadmap: §1 in [`ROADMAP.md`](ROADMAP.md). Auto-advance design of record:
[`docs/adr/0010-…`](../docs/adr/0010-playback-auto-advance-and-show-queue.md)
(the *advance mechanism* in that ADR is shipped; this doc supersedes its
*queue/sync* half — see "Design notes" below).

## The model (one paragraph)

There is **one structure**: an *ordered list of shows + a per-user pointer*.
Everything is an instance of it. The **backlog** is just *my default, always-mutable
list*. A **collection** is another list — possibly authored by me, possibly
**published** by someone else. The only real distinction is **mine vs published**:
mine = full edit rights (reorder/clear/add/move pointer); published = the *list*
is read-only to me, but I still own **my pointer** into it (where I am, when I last
played it). "Pop the backlog," "rewind," "clear" are all just pointer operations —
**the model never destroys data**; the backlog *view* simply hides the already-played
head and offers a Clear button. Playback advances to the next show by **mode**
(`none | backlog | chronological | collection`) behind the existing auto-advance
coordinator. Sync ships the **event** (add / pop / move), not the whole list —
the proven Favorites path — so two devices never clobber each other's list.

## Two parts — build Part 1, design Part 2 alongside

### Part 1 — the backlog (build now)

1. **Local store + domain model.** Ordered list of show refs + pointer. Mine,
   mutable. (Salvageable scaffolding exists on the abandoned `show-queue` branch —
   `play_queue` table/DAO/entity, `QueuedShow` model — re-introduce deliberately,
   not wholesale. Mind the schema-downgrade gotcha noted under "Gotcha" below.)
2. **Entry points.** "Add to backlog" → appends to the bottom. From show, player
   `⋯`, playlist. (ADR-0014 also parks a `Queue` control to return to the player
   inline cluster when this ships.)
3. **Advance setting becomes an enum.** Today auto-advance is a bool; make it
   `none | backlog | chronological`. The coordinator already exists — it just asks
   "which mode?" and resolves the next show. (`collection` mode is Part 2.)
   - `backlog` → play + remove the head (advance the pointer; view hides played).
   - `chronological` → next show by date (today's behavior; keep it explicit).
   - `none` → stop at end of show.
4. **Sync the event, not the list.** Per-action push (add / pop / move) over the
   userdata layer, exactly like Favorites. First-pull on sign-in/foreground.
   *No whole-list LWW snapshot* — the auto-popping backlog makes snapshot sync a
   clobber hazard; events are idempotent and tiny.
5. **"Up Next" screen.** One list component: now-playing marker + the list. For the
   backlog the marker sits at the top (nothing above it). **Don't bake "marker is
   always row 0" into the component** — Part 2 needs the marker mid-list.

### Part 2 — collections (design now, build later)

1. **Play a collection in order** = follow a **pointer** into it; do **not** load it
   into the backlog (keeps the collection intact, lets you leave/return/move the
   pointer, and "when I last played it" falls out of storing the pointer).
2. **Advance gains a 4th mode:** `collection` = follow the pointer.
3. **Up Next screen reused** — same component, marker now sits *mid-list* (stuff
   above = already played, below = upcoming); tap a row to move the pointer.
4. **Mine vs published** is the one flag: published collections are read-only lists
   (mine pointer only); editorial collections (the data-pipeline `collections.json`,
   parked behind `COLLECTIONS_ENABLED`) are the read-only case too.
5. **Author my own** collection; **publish** it (→ §5 social on-ramp).
6. **Import** someone's collection = **copy their list into a new mine-owned one**
   (duplicate-with-new-owner; their edits never touch mine). Keep it trivial — it's
   the same structure. Defer.

## Design notes (why this, vs ADR-0010's queue half)

ADR-0010 specced the queue as durable user data synced via **whole-list LWW
snapshot**, and advance as "queue head, else next-by-date." Re-examined and
revised here:
- **Snapshot → per-action events.** The backlog auto-pops during playback, so a
  whole-list snapshot races the active device's pops against another device's adds
  (silent clobber + resurfaced played show). Per-action events (the Favorites
  pattern) are idempotent and dodge it.
- **"Else next-by-date" → an explicit `chronological` mode, and the general case is
  "follow the current context."** Once collections exist, "what plays next when the
  list runs out" is *the current context*, not always chronological. Chronological
  is just the degenerate context (the whole catalog ordered by date). This keeps a
  curated collection from silently spilling into uncurated territory at drain.
- **Backlog and collections unified** as *list + pointer*; the only fork is
  *mine vs published*. The thing that auto-mutates (backlog) is the one we don't
  snapshot; the things we sync/publish are edited deliberately. The dangerous combo
  (auto-mutating + LWW-synced) never arises.

ADR is not yet updated — fine to change our minds; revise ADR-0010's queue/sync
sections (or a small amendment) once Part 1 is proven.

## Already shipped (don't re-plan) — auto-advance

The auto-advance *mechanism* from ADR-0010 is built and device-verified on iOS,
Android, web (#63): positive `onShowCompleted` event, park primitive, independent
coordinator, cross-device countdown over Connect, full-screen "Up Next" takeover,
per-device "when a show ends" opt-out. This plan reuses that coordinator unchanged —
the backlog/collection are just additional "next-show providers" behind it.

## Gotcha: wipe stale DB on devices that ran the abandoned branch

The old `show-queue` branch bumped schema (Android Room **v26** + `MIGRATION_25_26`;
iOS GRDB `v15-play-queue`). A new branch off `main` is at a **lower** schema — a
device still holding the v26 DB crashes on launch (Room can't downgrade). Clear app
data before installing: `adb shell pm clear com.grateful.deadly.debug` (Android) /
delete+reinstall the iOS app.
