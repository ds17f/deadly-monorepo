# Recording Recommendations — crowd-sourced "best recording" overrides

For some shows the app's default recording is wrong. Today the *only* lever we
have is the algorithmic pick (`shows.bestRecordingId`, derived from Archive.org
ratings in `data/scripts/.../generate_recording_ratings.py` +
`update_cached_ratings.py`). When the crowd-rating is misleading, the default is
stuck wrong until the next full data release.

This feature closes that loop: let users **tell us** which recording should be
the default, collect those signals server-side, let an admin curate them into a
**committed overrides file** in `data/`, ship them — and, eventually, push a
**lightweight live patch** so corrections land without a full DB release.

Working branch: **`worktree-recording-recommendations`** (off `main`).
Plan-only — nothing implemented. This doc scopes the work and proposes a build
order; decide later whether/when to start.

Related roadmap items this builds on / sits beside:
- **§3 Source / recording picker** — the picker UI this rides on top of.
- **Prebuilt catalog DB / data versioning** ([`prebuilt-catalog-db.md`](prebuilt-catalog-db.md)) —
  the mechanism a shipped override travels through, and the version-pinning
  rules that make a live patch tractable.
- **Admin → user messaging** ([`in-app-messaging.md`](in-app-messaging.md)) — the
  admin surface + analytics-allowlist patterns the recommendation collection
  reuses.

## What already exists (the head start)

Most of the *plumbing* is already in place — this is less green-field than it
looks.

- **The picker UI exists on all three platforms.** "Choose Recording" is in the
  playlist three-dot menu (`PlaylistMenuSheet.kt` → `onChooseRecordingClick` →
  `PlaylistRecordingSelectionSheet.kt`; iOS `Feature/ShowDetail/RecordingPicker.swift`;
  web `ShowLinerNotes.tsx`). Switching recordings already works.
- **A per-user override already exists and already syncs.** Picking a non-default
  recording writes a `recording_preferences` row (`showId` PK → `recordingId`,
  `updatedAt`, `deletedAt`) on each platform — Android `RecordingPreferenceEntity`/
  `RecordingPreferenceDao`, iOS `RecordingPreferenceRecord`/`RecordingPreferenceDAO`.
  It round-trips to the server via the V3 sync backup (`SyncBackupV3` →
  `SyncRecordingPrefV3`; server table `recording_preferences` in
  `api/src/db/users.ts`). **So the server already holds every signed-in user's
  per-show override.**
- **The default lives in the catalog as `shows.bestRecordingId`** (carried by the
  prebuilt seed via `build_catalog_db.py`, key `bestRecordingId` ←
  `best_recording`). Overriding the default = changing this value (or having the
  client treat a local pref as higher-priority than it, which it already does).
- **Analytics + admin dashboard exist.** `track()` events (server-allowlisted in
  `EVENT_SCHEMAS`, `api/src/db/analytics.ts`), admin pages under
  `ui/src/app/admin/`, the admin notifications/stats pattern from #55/#57.

The **gap** is the part above the plumbing:
1. A *recommendation* signal that is **distinct from a private preference** — "this
   should be everyone's default," not just "I personally want this tape."
2. A **non-spammy prompt** to capture it at the right moment, plus an explicit
   button.
3. **Server collection + an admin curation view** of those recommendations.
4. A **committed overrides file in `data/`** + a script that folds it into the
   build, so a curated override ships in the catalog.
5. (Stretch) a **live incremental patch** so an override lands without a full DB
   release, respecting any local user override.

## Distinction: *preference* vs *recommendation*

These are deliberately separate signals and must not be conflated:

- **Preference** (exists today): private, per-user, "play me this tape for this
  show." Already synced. Aggregating preferences across users is a *useful weak
  signal* ("40% of listeners who picked a non-default for 1977-05-08 chose
  `…sbd.miller…`") but it is **noisy** — people pick recordings for download
  size, partial availability, or idiosyncratic reasons.
- **Recommendation** (new): explicit, intentional, "I am telling you this should
  be the default for *everyone*." Higher-trust, lower-volume. This is the signal
  the admin curates from.

Build the recommendation as its own event/record. Use aggregated preferences only
as a secondary column in the admin view to corroborate.

## UX — capturing the recommendation

Two entry points, both only meaningful when the user is **not on the default**
recording (i.e. they actively chose a different one):

1. **Explicit button.** In the playlist three-dot menu (`PlaylistMenuSheet`),
   near "Choose Recording": when the current recording ≠ `bestRecordingId`, show
   **"Recommend this as the best recording."** Tapping it sends the
   recommendation and confirms inline ("Thanks — we'll consider it"). If they're
   already on the default, hide it (or show it disabled with "This is the current
   default").

2. **Gentle post-switch prompt (non-spammy).** After the user switches to a
   non-default recording **and listens for a dwell threshold** (start at ~20–30s
   of actual playback — long enough to mean "I'm staying on this," tune later),
   surface a low-friction prompt: *"Enjoying this recording? Recommend it as the
   default for this show?"* with **Recommend / Not now / Don't ask again.**

   Anti-spam rules (the core of "without spamming"):
   - **Once per show, ever** — if they've recommended or dismissed for this show,
     never prompt again for it.
   - **Global cooldown / frequency cap** — at most one such prompt per N hours
     and/or per M switches, so a power-user A/B-ing tapes isn't nagged.
   - **"Don't ask again"** kills the auto-prompt entirely (the explicit button
     still works).
   - Only prompt on a *deliberate* switch that *stuck* (dwell threshold), not on
     every recording change.
   - Persist this state locally (small per-show + global throttle store, like the
     notification cache pattern). Optionally mirror to settings so it's
     cross-device, but local is fine for v1.

   *Open question:* prompt only when signed in, or allow anonymous
   recommendations keyed by install id? (See "Identity" below.)

This is cross-platform: iOS (`RecordingPicker`/playlist menu), Android
(`PlaylistMenuSheet`/selection sheet + a small throttle store), web
(`ShowLinerNotes`, explicit button at least; the dwell-prompt is mobile-first).

## Server — collecting recommendations

A new lightweight signal, separate from `recording_preferences`:

- **New table** `recording_recommendations` in `api/src/db/users.ts` `initSchema`:
  `(install_or_user_id, show_id, recording_id, created_at, source)` where
  `source ∈ {explicit_button, post_switch_prompt}`. One active row per
  (user, show) — re-recommending updates it; a withdraw nulls it.
- **New route** `POST /api/recommendations` (and admin `GET
  /api/admin/recommendations` aggregation), following the
  `api/src/routes/notifications.ts` shape. Public POST (no/low auth, like
  notification consume); admin GET behind the admin guard.
- **Or** (lighter first step) ride the analytics pipeline: a `recommend_recording`
  `track()` event registered in `EVENT_SCHEMAS` with `{show_id, recording_id}`
  props, aggregated per-(show,recording) via the existing
  `json_extract(props,…)` + `COUNT(DISTINCT iid)` pattern. Cheaper to ship, but
  events are fire-and-forget (no withdraw, weaker identity). **Recommendation:**
  start with the analytics event for the *prompt-fired/recommended* funnel, but
  persist the actual recommendation in a real table so it's authoritative and
  withdrawable.

Note: because preferences already sync server-side, the admin view can also
compute **"users currently overriding show X → recording Y"** from
`recording_preferences` with zero new client work — a strong corroborating
column.

## Admin — curating into overrides

New admin page `ui/src/app/admin/recordings/` (sibling to `admin/notifications`):

- List shows with recommendation activity, ranked by signal strength
  (explicit recommendation count, then corroborating preference-override count,
  vs current default).
- For each: current `bestRecordingId`, the recommended recording(s) with counts,
  quick links to compare on Archive.org.
- **"Accept" action** → appends/updates an entry in the committed overrides file
  (below). The admin curates; the crowd only nominates.

## Data — the committed overrides file + build integration

This is the `data/` piece the user described:

- **New committed source of truth** `data/stage00-created-data/recording-overrides.json`
  (or `.csv`) — small, human-reviewable, git-tracked, like the AI-reviews/
  collections that already live in `stage00`. Shape:
  `[{ "show_id", "recording_id", "reason"?, "added_by"?, "added_at" }]`.
- **A script** (e.g. `data/scripts/apply_recording_overrides.py`, or a step folded
  into the existing rating/best-recording generation) that, during generate,
  **overrides `best_recording` for any show in the file** before
  `build_catalog_db.py` reads it. So the curated default flows into
  `shows.bestRecordingId` in the seed exactly like any other catalog field —
  no schema change, ships through the normal `data-vX` release.
- Add a `make` target (e.g. `make data-apply-overrides`) per repo convention, and
  document it in `data/README.md`.
- Validation: every override's `recording_id` must exist and map to its
  `show_id` (reuse the recording↔show mapping logic from
  `build_catalog_db.py`); fail the build on a dangling/mismatched override.

At this checkpoint the loop is **closed but heavy**: a correction requires a full
`data-vX` release + (because apps pin an exact data tag — see
`prebuilt-catalog-db.md`) an app-constant bump to actually reach users.

## Stretch — live incremental patch (no full DB release)

The user's "push these out live" ask. Goal: a curated override reaches existing
installs **without** shipping a new DB or app build.

- **A tiny patch artifact**, independent of the heavy `catalog.db.zip`: e.g.
  `GET /api/recording-overrides?since=<cursor>` returning
  `[{ show_id, recording_id, updated_at }]`, served from the same curated data
  the build uses (publish the overrides file to the API, or have CI push it).
  Small JSON, cursor-based like the notifications delta.
- **Client background fetch at startup/foreground** (reuse the notification
  coordinator cadence — ProcessLifecycle `ON_START` / `willEnterForeground`).
- **Apply rule (the important part):** for each override, update the show's
  effective default **only if the user has no local `recording_preferences`
  override for that show** — the local user override always wins. Two viable
  storage strategies:
  - **(a) Patch `shows.bestRecordingId`** in the live DB for un-overridden shows.
    Simple read path (everything already reads `bestRecordingId`), but mutates
    catalog rows — must be **idempotent and reconciled** with the non-destructive
    refresh (ADR-0009): a later full seed import would re-assert the algorithmic
    default, so the patch must re-apply after import, or the patch store must be a
    separate layer.
  - **(b) Keep a separate `recording_overrides` table** (server-curated layer,
    distinct from user `recording_preferences`) and resolve the effective
    recording as: **user preference > server override > `bestRecordingId`.** This
    keeps catalog rows pristine and survives reseeds cleanly, at the cost of
    touching the recording-resolution code on all three platforms.

  **Recommendation: (b).** It composes with the existing preference layer, makes
  precedence explicit, and doesn't fight ADR-0009's reseed. (a) is tempting for
  its zero-read-path-change but creates a reconciliation hazard.

- The live patch and the committed-overrides-in-the-seed path use the **same
  curated data**: the seed carries the baseline; the live channel carries deltas
  added since the last data release. A show's override appears in the seed once
  the next `data-vX` ships, and the live channel can prune it then.

## Suggested build order (each independently shippable)

1. **Server recommendation collection** — table + `POST /api/recommendations`
   (+ analytics funnel event). Nothing user-visible; lets us start gathering
   signal immediately behind the explicit button.
2. **Explicit "Recommend this recording" button** (all 3 platforms) — gated on
   not-default. Smallest UX surface, immediately useful.
3. **Admin curation page** + reads of recommendations and corroborating
   preference aggregates.
4. **`data/` overrides file + build integration** — closes the loop through the
   normal data release (heavy path).
5. **Gentle post-switch prompt** (mobile) — the dwell-threshold + anti-spam
   throttle. Higher-volume signal once the rest is proven.
6. **Live incremental patch** (stretch) — server override layer + client
   background fetch + precedence resolution. The biggest piece; do last.

## Open questions

- **Identity for anonymous users.** Recommendations from signed-out users —
  keyed by install id, or signed-in only? (Affects abuse resistance and the
  withdraw path.)
- **Abuse / weighting.** One vote per install vs weighting by listening history;
  floor before a recommendation surfaces to the admin. The admin "Accept" gate
  means low risk, but ranking still needs a sane signal.
- **Dwell threshold + cooldown numbers.** Start ~20–30s dwell, one prompt per
  show ever, global cap TBD — instrument and tune.
- **Live patch storage: (a) vs (b)** — recommended (b); confirm before building.
- **Show vs recording mapping edge case.** The ~57 tapes shared by two shows
  (Deferred limitation) — an override is keyed by `(show_id, recording_id)`, so
  it's unaffected, but validation must allow a recording that maps to two shows.
