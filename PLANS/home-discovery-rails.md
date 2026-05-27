# Home discovery rails — Popular, Best, CarPlay/AA

Working plan for the Trending follow-up units. Trending itself shipped in
PR #44 (merged) plus follow-up fix `743daba6` on main. This doc captures
what's left and the constraints we've already locked in, so a future
session can pick up cold.

## Where we are

### Shipped (already on main)
- `acde63ef` (PR #43) — trending ranks by logical listens (not session dedup).
- `c8b840c9` (PR #44) — TIGDH filter on `now` window + opt-in toggle, plus:
  - `feature_use` audit; added missing `set_favorites_display_mode` tracking.
  - Listening Now soft 90s boundary (later corrected).
  - Growth chart window picker (7/30/90/365), Y-axis, mobile tap-to-select.
  - Web header player: close button, narrow-viewport wrap.
- `743daba6` — Listening Now **dual-window** boundary:
  - `playback_start` within 45 min OR `playback_end` within 2 min → live.
  - Covers mid-long-jam (Dark Star = 30+ min) and between-tracks gap.

### Shipped (on `feat/popular-rail`, not yet on main)
- Unit 2 — "Fan Favorites" rail (see [ADR-0005](../docs/adr/0005-fan-favorites-rail.md)
  for the design rationale). Final shape diverged materially from the
  original plan below — what shipped:
  - `/api/popular` returns four per-decade *pools* (60s/70s/80s/90s),
    each ≤20 shows, drawn via a 4h-rotated deterministic shuffle.
  - Client picks the 4-show display set locally:
    - `all` pref → 1 show per non-empty decade.
    - Specific decade pref → 4 shows from that pool with year-spread.
  - "Show more" link bumps a local seed → instant re-roll, no fetch.
  - Decade choice lives in Settings → Home Screen (segmented picker).
  - Env-tunable: `POPULAR_MIN_FAVORITES` (floor, default 1),
    `POPULAR_PER_DECADE` (pool size, default 20),
    `POPULAR_ROTATION_HOURS` (default 4).
- Commits: `67057322` (initial ratio-ranked, replaced),
  `5d626407` (mobile rail + in-header cycler, replaced),
  `fa59ea67` (floor/limit tuning, replaced),
  `19a9ea6b` (decade pools — current), `d8383e9b` (Show More — current).

### Not yet released
- Trending toggle ships in the mobile app on next iOS/Android release. Until
  then, existing app users hit the server with no query param → see filtered
  trending (the new default), which is what we want.
- Fan Favorites rail ships in the next iOS/Android release.

## Upcoming units

### Unit 2 — "Fan Favorites" rail (show favorites) — SHIPPED

Final design is documented in [ADR-0005](../docs/adr/0005-fan-favorites-rail.md).

The original plan in this doc proposed a ratio-ranked top-5 globally,
with a floor of ~3 favoriters. That shipped briefly in `67057322` and
was replaced — see ADR-0005 *Alternatives considered* for the full
reasoning. **Lessons worth carrying into Units 3/4:**

- **Distribution is flatter than expected** at the current install base.
  ~97 users across ~84 favorited shows means most shows sit at 1–2
  favoriters; any minimum-volume floor ≥ 2 produces an empty rail.
  Plan Units 3/4 (track favorites) assuming a similar shape until
  proven otherwise — the per-track distribution will almost certainly
  be even sparser.
- **Pure ranking made the rail feel dead.** Refreshes happen only when
  someone favorites a show, which at current volume is every 1–2
  hours. The 4h-rotated shuffle + client re-roll combination is what
  made the rail feel alive without sacrificing return-visit stability.
  Same trick is reusable for Unit 3.
- **The catalog has era texture worth preserving.** Decade-bucketed
  pools surface 60s and 90s shows that would never crack a global
  ranking. Track favorites will have analogous song-era texture
  (early Pigpen-era setlist staples vs late Brent-era openers vs
  Vince-era setlists) — worth designing for from the start.
- **Server-tunable knobs save you.** `POPULAR_MIN_FAVORITES`,
  `POPULAR_PER_DECADE`, `POPULAR_ROTATION_HOURS` as env vars meant
  we could fix the floor-too-tight problem in prod without an app
  release. Build the equivalent into Unit 3 from day one.
- **Show ID format gotcha.** Prod uses slug-form
  (`1977-05-11-st-paul-...`), older fixtures use `gd1977-...`. The
  decade extractor matches the first 4-digit run regardless. If
  Unit 3 keys on track IDs (`<show-slug>/<track-index>`), the same
  liberal parsing applies.

### Unit 3 — "Best..." rail (track favorites)

**Source of truth.** Same `feature_use` event, but `target_type='track'`
and `target_id` is a track path (`gd1988-06-30.../19`). Need to verify the
schema cleanly distinguishes show-favorite from track-favorite — saw one
mixed example in the analytics data, so an early task is auditing the
client-side emission to make sure `target_type` is consistently set.

**UI shape.** Collapse by *song title* (not by performance). Row reads
"Sugar Magnolia · 17 favorites" with a tap → expand to top performances of
that song. This is the Grateful Dead question ("*which* Sugar Magnolia?")
rather than the analytics one ("which moments?"). Flat per-performance
list is the drill-down, not the default.

**Track-title resolution.** Track paths don't carry song titles directly —
need to resolve `gd1988-06-30.sbd.miller.../19` → "Sugar Magnolia" via the
local track catalog. Should be a straight DB join; verify the catalog has
clean canonical titles (likely yes, but a normalization pass may be
needed — `Sugar Mag` vs `Sugar Magnolia` etc.).

**Open question.** Same song favorited from multiple shows — UI treatment
of duplicate titles needs a decision. Probably: rank within-song by count,
show the top performance with a "(N more)" link.

### Unit 4 — CarPlay / Android Auto integration

**Scope.** Surface Trending + Popular + Best in CarPlay (iOS) and Android
Auto media browser hierarchies. Three new top-level categories.

**Why separate.** Different code paths (MPPlayableContentManager on iOS,
MediaBrowserService on Android), different testing (drive somewhere or use
AA Desktop Head Unit), and the *data* for Units 2/3 needs to ship first.

**Dependencies.** Unit 2 + Unit 3 must be on main with stable data shapes.
Trending CarPlay/AA path doesn't exist yet either — building this means
designing the browsable-content hierarchy from scratch.

## Constraints & decisions already locked in

- **Logical listens is the canonical "popularity" signal** (ADR-0004 + #43).
  Use it as the denominator for ratios, not raw `playback_start`.
- **Anniversary (MM-DD ±1) is the right filter granularity** for the `now`
  window. Verified against prod data — only ~6% of all-time top-50 would
  be excluded on a peak day; never strips a full top-10.
- **Settings tracking pattern:** `analyticsService.track("feature_use", { feature,
  category: "preference", value })`. Match this for all new toggles.
- **Cross-platform parity:** iOS gets `.onChange` observer, Android gets
  StateFlow `drop(1).collect`. Wire refetch through HomeViewModel (Android)
  / HomeScreen (iOS) — TrendingServiceImpl init observer alone was unreliable.
- **Scroll reset on data change:** Android rails need
  `LaunchedEffect(firstId, …) { listState.scrollToItem(0) }`. iOS already
  did this via `ScrollViewReader`.

## Data signals worth remembering

(snapshot from 2026-05-27 prod pull — re-verify when picking back up)

- **Trending volume:** ~200–700 `playback_start` events/day, 24h top-10
  ~10–70 plays per show.
- **Favoriting volume:** 425 adds / 39 removes over 30d, 97 distinct users.
  Healthy add/remove ratio, but distribution is flat — top shows sit at
  1–2 users each. Low volume isn't the bug; the *signal* (people kept it)
  is what makes the surface worthwhile.
- **Show-id format in prod:** slug form
  `1977-05-11-st-paul-civic-center-...` (year is the first 4-digit run).
  Older test fixtures use `gd1977-...`. Liberal parsing handles both.
- **Decade distribution of favorited shows (snapshot):** mostly 70s
  and 80s, with a meaningful 60s tail and a smaller 90s presence.
  Decade-bucketed pools surface all four; a global ranking would
  strip the 60s/90s entirely.
- **Track favorites exist** but mixed in with show favorites in `target_id`
  — needs schema verification before Unit 3.

## Deferred (parked, not dropped)

### Drag-list home reorder

Replace the current `homeTrendingAboveToday` boolean with an ordered
list of section IDs (recent / trending / today / fan-favorites /
collections / …). A Settings sub-screen lets the user drag to reorder.

- iOS: trivial (`List` + `.onMove`, ~40 lines).
- Android: medium — no native equivalent. Either pull in
  `sh.calvin.reorderable` or hand-roll with `detectDragGesturesAfterLongPress`.
  ~150 lines hand-rolled.
- Migration: derive initial list from existing pref.

Held until after Unit 2 ships so we have multiple rails to actually
reorder. The Trending-above-Today toggle stays in place until then.

Now unblocked since Unit 2 is on the branch — main has Trending + Today
+ Featured Collections + Recently Played, and Fan Favorites lands next
release. Worth picking up before Unit 3 so the new track-favorites rail
slots into the ordered list rather than getting another bespoke toggle.

## Index footgun (worth knowing)

`analytics_events` has a unique index on `(iid, sid, event, ts)` to
neutralize client retries. The index keys on primitive columns only —
`props` is *not* part of the key. Two same-ms events from the same
install/session with different `props` collide and one is silently
dropped by `INSERT OR IGNORE`. Surfaced while writing the popular
tests (alice favoriting two shows at the same `t` lost one favorite).

Real-world risk is low (clients emit serially with ms granularity).
Becomes a real bug if we ever build a bulk-action UI ("favorite all",
batch import). Mitigations when needed: stagger timestamps client-side
or widen the index to include `props` (or a hash of it).

## Things explicitly out of scope (don't drift)

- Don't fold Popular into Trending. They answer different questions
  ("what's hot" vs "what holds up") and mixing dilutes both.
- Don't add a settings toggle for things we can default well. The TIGDH
  toggle exists because the user explicitly wanted user choice; smaller
  prefs (card sizes, etc.) get defaults.
- Don't track dev-only toggles (forceOnline, hermetic mode). User-facing
  prefs only.
