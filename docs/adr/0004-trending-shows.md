# ADR-0004: Trending shows on the home screen

## Status

Proposed (2026-05-24)

## Context

We collect playback analytics on every track start (`playback_start`
events with `show_id` and `recording_id` in the props). At time of
writing the prod `analytics.db` holds ~6,800 such events across 51
days, growing — last week averaged ~280 plays/day. This data is rich
enough to answer "what are people listening to right now?" but is
currently invisible to users.

There is product appetite for a **Trending Now** surface on the home
screen. The motivation is **discovery**: today the home screen surfaces
Today-in-History (date-relevant shows from prior years) and a few
hand-curated entry points. There is no signal that exposes "what other
listeners are actually playing this week." The catalog is enormous
(thousands of shows) and most of it never surfaces in front of any
given user. Trending is meant to widen the funnel.

A naive design — "show the top N most-played shows in window X" —
runs into several issues that surfaced during data analysis:

1. **`playback_start` fires per track**, not per show. A user who
   plays one track and a user who plays a full 22-track second set
   look like 1 vs 22 plays of the same show. Raw play counts
   conflate engagement breadth with engagement depth.

2. **Without de-duplication, a single user can dominate.** Example
   from prod: show `1981-03-21-rainbow-theatre-london-england` shows
   33 plays in the last 7 days — from **one** install. A show with 9
   listeners averaging 3 plays each is more meaningfully "trending."

3. **Today-in-History drives a daily content cycle.** Different shows
   surface naturally on different dates because TIH is one of the main
   things driving people into the player. This is a feature, not a
   problem — it means raw counts in short windows already differ
   meaningfully from all-time counts without any special "velocity"
   math.

4. **Short windows have low absolute volume.** A 24-hour window
   currently sees ~280 plays total, spread across dozens of shows.
   The top show in a day might have only 2-6 distinct listeners.
   Any minimum-listener filter would empty the list.

5. **Velocity-style rankings ("trending vs baseline") add complexity
   without obvious payoff** at current scale. With TIH naturally
   rotating the catalog, raw counts already differentiate windows.

The product goal is **catalog exposure**, not consensus ranking. The
user has explicitly stated a preference for filling a 10-slot list
with low-confidence picks over showing 2-3 high-confidence picks. A
show with one listener is still "trending" in the sense that
matters — someone is listening to it and we have nothing more
authoritative to surface.

## Decision

### Endpoint

A single `GET /api/trending` endpoint returns four time windows in
one response so client tab switches are instant:

```json
{
  "generated_at": "2026-05-24T18:00:00Z",
  "windows": {
    "now":   [ { "show_id": "...", "sessions": 6, "plays": 14 }, ... 10 ],
    "week":  [ ... 10 ],
    "month": [ ... 10 ],
    "all":   [ ... 10 ]
  }
}
```

Payload is small (~5KB). Cached in Redis under `trending:v1` with a
10-minute TTL.

### Window semantics

- **`now`** — rolling last 24 hours (not calendar day). Smoother
  than midnight-bounded windows; matches the "Now" label.
- **`week`** — rolling last 7 days.
- **`month`** — rolling last 30 days.
- **`all`** — all-time, since the analytics table's first row.

### Ranking primitive: show-sessions

The unit ranked is a **show-session**: one distinct
`(install_id, session_id, show_id)` tuple. One person sitting down
to play a show = 1 session, regardless of whether they played 1
track or 22. This dedupes the per-track inflation cleanly.

```sql
sessions  = COUNT(DISTINCT iid || '|' || sid)  -- primary ranking signal
plays     = COUNT(*)                            -- raw track plays (exposed for transparency)
```

We deliberately **do not** expose distinct-listeners in the rolled-up
windows. See *Distinct-listener counting* below.

Within each window:
```
ORDER BY sessions DESC, plays DESC
LIMIT 10
```

No minimum-listener filter, no minimum-session filter. A show with one
session still gets a slot if there are not 10 better candidates.

### Overlap across windows: allow it

Cornell 5/8/77 can legitimately appear in `now`, `week`, `month`,
**and** `all` simultaneously. We do not exclude shows from shorter
windows that appear in longer ones. Rationale: the user wants
**"what people are actually playing"** to be the source of truth, not
a curated "spread the love" filter. If Cornell really is trending
today, it belongs on the Today tab. If users want fresh discovery,
that's exactly what the Week / Month / All-Time tabs give them when
Cornell falls off the short windows.

### Storage: per-show daily rollup

A new table aggregates show plays per calendar day:

```sql
CREATE TABLE show_plays_daily (
  day TEXT NOT NULL,           -- 'YYYY-MM-DD' UTC
  show_id TEXT NOT NULL,
  sessions INTEGER NOT NULL,   -- COUNT(DISTINCT iid || '|' || sid) within day
  plays INTEGER NOT NULL,      -- COUNT(*) within day
  PRIMARY KEY (day, show_id)
);
CREATE INDEX idx_show_plays_day ON show_plays_daily(day);
```

The week / month / all windows are computed by summing across this
rollup — cheap (~thousands of rows at most).

The **`now` (24h)** window does **not** use the rollup. It queries
`analytics_events` directly with a `ts > now() - 24h` predicate. This
gives true rolling-24h semantics, sidesteps the day-boundary
mismatch with the rollup, and is cheap at current volume (uses
`idx_analytics_event_ts`). At ~6,800 events total it's effectively
free; the design is fine until volume grows ~100×.

### Rollup cadence: hourly

The rollup job runs **hourly**, not nightly. Two reasons:
- Keeps the week / month / all windows fresh enough that the home
  screen feels live, not 24-hour-stale.
- Cheap to do — incremental upsert of today's row only.

The existing `analytics_daily_rollup` job remains nightly; the new
per-show rollup is independent.

### Distinct-listener counting

The natural question — "how many distinct *people* listened to this
show this week?" — cannot be answered by summing daily rollup rows.
If Alice listens Mon, Tue, Wed she gets counted three times in
`SUM(daily.listeners)`. True distinct counts would require either
querying `analytics_events` directly for each window (heavier) or a
materialized per-iid roll.

We chose to **not expose distinct-listener counts in the response at
all**, for v1:

- The user has explicitly stated they care more about "times played"
  than "who listened." A single very engaged user is a legitimate
  trending signal, not noise to filter out.
- Show-sessions already gives us the right ranking signal without
  needing distinct-listener counts.
- Avoiding the field entirely is honest. We don't return a number
  that could be misread as "humans" when it's really "listener-days."

If a future product need requires true distinct listener counts (e.g.,
a "X people are listening to this" badge), we'll query
`analytics_events` directly for that specific use case, or add a
second rollup keyed by `(week, show_id, iid)`.

### Client integration

- The home screen gains a **"Trending Now"** carousel section.
- Four tabs: **Now / Week / Month / All-Time**, defaulting to **Now**
  (the tab that changes most, and the one with the highest discovery
  value).
- Each tab is a horizontal scroll of 10 show cards, matching the
  existing home carousel pattern.
- Refreshes on app open and on pull-to-refresh.

Carousel position on the home screen defaults to **above
Today-in-History**, but is built as a reorderable home module so
the user can move it (or hide it) from Settings. If the existing
home doesn't already have a module-ordering system, that work is
out of scope for the trending feature itself and v1 hard-codes the
position above TIH.

## Consequences

### Positive

- **Catalog exposure.** Up to 40 distinct shows surface across the
  four tabs (with full overlap allowed, fewer in practice). Today the
  home surfaces ~5-10. Massive widening of the funnel.
- **Listener data finally drives product.** Two months of analytics
  data has been informing nothing user-facing. This is the first
  surface that uses it.
- **No "favorites cabal" lock-in.** Even if Cornell dominates every
  tab, the Week and Month tabs will still expose date-rotating TIH
  picks, '82 Greek runs, Spring '90, etc., because those genuinely
  appear in the data alongside the perennial favorites.
- **Cheap to run.** One Redis-cached endpoint, one hourly rollup job,
  one small table. The `now` window scans `analytics_events` directly
  but that table is small and indexed.
- **Self-improving signal as user base grows.** Today the `now`
  window has limited signal (~2-6 listeners per top show). At 10×
  volume the same query gives noticeably stronger rankings without
  any code change.

### Negative

- **`now` tab can be lumpy at current scale.** With ~280 plays/day
  spread across many shows, the top show on `now` might have only a
  handful of sessions. This is by design (we'd rather show 10 lightly
  trending shows than 2 strongly trending ones), but a user looking
  at the data analytically will notice the small numbers. We don't
  expose session counts in the UI for v1, so this is invisible to
  most users.
- **A single power user can move the `now` rankings.** With low
  absolute volume, one person playing through a full show can put it
  near the top of the Today tab. We accept this — the product owner
  has explicitly opted into this tradeoff, and it tends to surface
  *more* catalog, not less.
- **`week`/`month` aggregation drifts slightly from "true."**
  Computing those windows by summing daily rollup rows means we lose
  any sub-day distinctness. In practice this only matters for the
  distinct-listener count, which we deliberately don't expose. The
  session count is correct because a session ID is scoped to a single
  app lifetime, which is almost always within one day.
- **No velocity / momentum signal in v1.** A show that suddenly spikes
  from 0 plays to 5 plays today won't rank above an evergreen show
  with 50 plays/day. Velocity would surface novelty better. Deferred
  until we see whether it's actually missed in practice.
- **Hourly rollup adds a recurring job to the infra.** Small in
  absolute cost but it's another thing that can fail silently and
  cause the home screen to look stale. Mitigation: rollup job emits
  its own analytics event on success; an absence is detectable.

## Alternatives considered

**Rank by raw play count.** Simplest possible. Rejected because
`playback_start` fires per track, so a 22-track listen-through
dominates 22 single-track samples. The signal is engagement-weighted
in a way that doesn't match user intuition for "trending."

**Rank by distinct listeners only.** Filters out the "one obsessive
listener" case cleanly. Rejected because the product owner explicitly
prefers exposing more catalog — a show with one engaged listener is
still a legitimate trending signal in a discovery context. Also
inflates the rollup-double-counting problem.

**Velocity (window count vs baseline).** Rank by `plays_in_window /
expected_plays_from_baseline` so newly-spiking shows rise above
perennials. Considered seriously. Rejected for v1 because (a) TIH
already provides natural rotation that differentiates short windows
from long ones, (b) low-volume windows blow up the math (1 / 0
problem) and need Bayesian smoothing to be sane, and (c) the user
goal is exposure breadth, not novelty detection. Reconsider if
windows start looking too similar in practice.

**Anti-overlap across windows.** Exclude from shorter windows any
show that appears in a longer window's top-N. Considered seriously
and proposed in conversation. Rejected because the user wants the
trending list to reflect actual listening, not a curated "spread the
content" filter. Cornell genuinely trending today should appear on
Today.

**Minimum-listener filter (e.g., ≥3 distinct listeners).** Would
cleanly remove the "one person played it" entries from the `now`
tab. Rejected because it would empty the Today tab at current
volume — the product owner prefers a full list of weak signals over
an empty list of strong ones.

**Per-window endpoint (`GET /api/trending?window=now`).** RESTful
and feels right. Rejected in favor of returning all four windows in
one response so tab switches don't require network round-trips. The
payload is ~5KB; the latency savings are worth it.

**Compute on every request (no rollup).** At current volume this
would work. Rejected because the design needs to survive a 10-100×
volume increase without rework. The hourly rollup is cheap to add
now and prevents a future migration.

**Reuse `analytics_daily_rollup`.** The existing table aggregates by
`(day, event, platform, app_version)` — no `show_id` dimension. We'd
have to extend it or join into props JSON on every query. A separate
per-show rollup table is cleaner and keeps the existing rollup's
schema stable.

**Track a dedicated `show_open` or `show_play_started` event distinct
from per-track `playback_start`.** Would give us a cleaner "show
session" primitive without the dedup step. Considered. Rejected for
v1 because (a) `playback_start` + iid/sid dedup already approximates
this, (b) adding a new client event means waiting for app version
adoption before the signal is reliable, and (c) the data we have
goes back 51 days and we want trending working immediately, not in
60 days when a new app version reaches saturation. Worth revisiting
as a longer-term cleanup.

## Open questions

- **UI session-count visibility.** Do we show "X people listening
  this week" next to each show? Probably not for v1 — keeps the
  surface clean and avoids the listener-day-vs-people distinction
  leaking to users. Revisit after launch.
- **Pagination beyond 10.** v1 returns 10 per window. If users
  reach the end and want more, we'd extend to a paginated
  `/api/trending/{window}` endpoint. Decision deferred until we see
  engagement.
- **Personalization.** Global-only for v1, by explicit decision.
  Personalized trending ("shows similar to what you've been playing")
  is a different feature and a different conversation.

## Post-implementation updates (2026-05-25)

The endpoint and mobile carousels shipped on iOS and Android. A few
things changed or got learned during build-out.

### Rollup backfill bug

The hourly rollup was originally written to upsert rows only for the
*current* UTC day, on the assumption that historical days never change.
That assumption held until we noticed `week` and `month` windows
under-counting after the job missed a tick (deploy, restart, etc.) —
the missed day stayed empty forever because nothing re-visited it.

Fix: the rollup now backfills **any day in the analytics window that
has no row in `show_plays_daily`**, not just empty days. Cheap because
the lookup is a left join against the day-keyed index. See commit
`6cb5edc4`.

This shifts the operational model from "every hour is incremental" to
"every hour is self-healing." Worth keeping in mind if we ever add a
second show-keyed rollup — same property should apply.

### UI iteration on the window selector

The ADR specified "Four tabs: Now / Week / Month / All-Time." We tried
that, an `AssistChip` pill that cycled, and a few other variants before
landing on the current design:

- **Title is the selector.** The carousel header itself reads
  "Trending now" / "Trending this week" / "Trending this month" /
  "Trending all time" and is tappable. A secondary "Show <next>" label
  on the right of the row hints at the cycle and is also tappable.
- **Why:** A four-tab strip burned vertical space and made the section
  feel heavier than its neighbors (Recently Played, Today in History).
  Inline cycling collapses the control into the heading itself.
- **Risk we accepted:** without explicit tab affordances, users may
  not immediately discover that the window is configurable. Mitigation:
  the "Show <next>" text on the right reads as an action; the same
  preference is also exposed as a segmented control in Settings →
  Home Screen.

### Carousel position is a user preference, not hardcoded

The ADR said "v1 hard-codes the position above TIH." We implemented a
`homeTrendingAboveToday` boolean instead, because once we had it
running it was obvious that someone who prefers date-context-first
(Today in History) over discovery-first (Trending) shouldn't have to
wait for a "reorderable home modules" feature to flip it.

Default flipped to **Trending below Today** mid-build, based on
preference for the date-centric Today section anchoring the screen.

### Per-section card sizing

The Trending carousel surfaces 10 items, vs Today (typically 1-3) and
Collections (a handful). At the default 160pt card width, Trending
visually dominated the screen — every other section was "scroll past
this." Solution: each carousel has its own card-size preference
(`small` / `large`). Trending defaults to small (100pt, date-only
caption); Today and Collections stay at 160pt. All three are
user-overridable in Settings → Home Screen.

This is a UI-only knob, not a data concern. Worth recording because
it surfaces a pattern: the right "compactness" of a carousel is a
function of how many things are in it and how unique each one is.
Recently-played-by-this-user (always-relevant) and trending-shows
(many, mostly-unknown-to-this-user) shouldn't get the same real
estate per item.

### Long-press preview parity

Recently Played had a long-press detail popover from day one. Today
and Trending did not. Added bottom-sheet (Android) and contextMenu
preview (iOS) handlers for parity — users who long-press one show
card now get the same detail surface on all three sections. Cheap
to do and the inconsistency was confusing.

### Settings grouping + reset

All six home preferences (window, above-Today, recent rows, three card
sizes) now live under a dedicated "Home Screen" section in Settings,
with a "Reset Home Screen to Defaults" action at the bottom. The
density of home knobs reached a tipping point during this round; one
big "Preferences" section was getting hard to scan.

## Algorithm change: logical-listen ranking (2026-05-25 cont.)

Within hours of shipping the original endpoint the bias the ADR's
*Negative consequences* section warned about ("a single power user can
move the `now` rankings") turned out to be much bigger than predicted —
not because we underestimated power users, but because the *primitive*
was wrong, not the user base.

### What was wrong

The original ranking was `COUNT(DISTINCT install_id || '|' || session_id)`,
where `session_id` is one app lifetime. Each time the app got killed and
relaunched mid-show, the resumed playback fired a fresh `playback_start`
with a new `sid`. So one listener restarting the Greek 5 times in 30
minutes registered as 5 "sessions" — indistinguishable from 5 different
people each listening once.

The 1982-05-23 Greek showed 19 sessions in the week window. The new
primitive shows 10 listens from 7 distinct installs. Most of the
deflation is the same one or two people getting de-duplicated.

The Stanley Theatre 9/27/72 example is even cleaner: 35 plays from 1
install used to look like a strong signal under any "plays"-weighted
ranking. It's now 1 listen / 1 install — exactly as a human would
describe what happened.

### New primitive: logical listens

A **logical listen** is a maximal run of `playback_start` events from
one install on one show, where the run is broken by either:

1. A play of a *different* show in between, or
2. A gap of more than 6 hours.

Walking each install's events in time order and incrementing a counter
when a new listen starts gives the count. The 6h gap threshold means
"come back the next morning" counts as a second listen (which matches
intuition), but "lock phone, come back 30 min later" does not.

```sql
WITH plays AS (
  SELECT iid, json_extract(props, '$.show_id') AS show_id, ts,
         LAG(json_extract(props, '$.show_id')) OVER w AS prev_show,
         LAG(ts)                              OVER w AS prev_ts
  FROM analytics_events
  WHERE event = 'playback_start'
    AND json_extract(props, '$.show_id') IS NOT NULL
  WINDOW w AS (PARTITION BY iid ORDER BY ts)
),
listens AS (
  SELECT iid, show_id, ts,
         CASE WHEN prev_show IS NULL
                OR prev_show != show_id
                OR (ts - prev_ts) > 21600000  -- 6h
              THEN 1 ELSE 0 END AS is_new
  FROM plays
)
SELECT show_id,
       SUM(is_new)         AS listens,
       COUNT(*)            AS plays,
       COUNT(DISTINCT iid) AS installs
FROM listens
WHERE ts > :window_start
GROUP BY show_id
HAVING SUM(is_new) > 0
ORDER BY listens DESC, plays DESC
LIMIT :n;
```

The LAG scan deliberately runs *over the full event history*, not
filtered to the window — so listen-start detection is correct even when
a listen straddles the window boundary. The window filter applies to
which listen-start events count toward this window, not which events
participate in the LAG.

### Storage: window-keyed rollup, not day-keyed

The previous `show_plays_daily` table summed per-day session counts
across windows. That worked when sessions were the primitive, because
sessions are scoped to a single day anyway (a session ID rarely
survives across the midnight UTC boundary). It does **not** work for
logical listens: an install playing Cornell at 11pm and again at 1am
crosses a day boundary inside one listen.

The fix: a new table keyed by `(window, show_id)`, fully rebuilt each
hour from raw events:

```sql
CREATE TABLE show_listens_rollup (
  window TEXT NOT NULL,         -- 'now' | 'week' | 'month' | 'all'
  show_id TEXT NOT NULL,
  listens INTEGER NOT NULL,
  plays INTEGER NOT NULL,
  installs INTEGER NOT NULL,
  PRIMARY KEY (window, show_id)
);
```

The rebuild is a single transaction: `DELETE` all rows, then 4 INSERTs
(one per window) using the query above. We cache the top 50 per window;
the endpoint serves top 10. Storing the extra headroom lets us change
the served limit without re-running the rollup.

The old `show_plays_daily` table is dropped on startup. There's no
migration concern — every value in it was derivable from
`analytics_events`, which is the source of truth.

### Cost shape

The rollup is the only thing that scans `analytics_events`. The
endpoint reads a small precomputed table behind a 10-minute Redis
cache, so the request path is constant-time regardless of analytics
volume. At ~7,000 events total today the rollup takes under 20 ms; at
10× growth, ~200 ms in a background hourly tick. Nothing in the
critical path scales with analytics-events count.

The original ADR's "self-improving signal as user base grows"
prediction was right in spirit — the rankings *do* get cleaner with
more users — but the algorithm bias was masking that benefit. The
new primitive makes the asymptote land where it should: more users
means more distinct installs hitting the rollup, which is the signal
we actually want.

### Why we kept `plays` and added `installs`

`plays` (raw `playback_start` count) is kept in the response and used
only as a tiebreaker, not the primary signal. It's still useful
operationally — it tells you, when ranking is tight, which show people
went deep on.

`installs` (distinct iids in the window) is new in the response. It's
the most human-readable number — "how many different people played
this show" — and matches what most people imagine when they ask "what
are trending shows." We deliberately rank by `listens` rather than
`installs`, though, because someone playing the same show on two
different days *is* a stronger signal than someone playing it once,
and the logical-listen count captures that without inflating from
session restarts the way the old primitive did.

### What we deliberately did *not* do

- **Engagement weighting (e.g. count by `listened_ms`).** Tempting,
  especially now that we know `playback_end` carries `listened_ms`
  and a `reason` field. Held off because (a) the ranking change here
  already addresses the user-visible complaint, (b) the duration data
  is a separate axis from the dedup question, and (c) "this person
  actually listened to most of it" is a fundamentally different
  signal than "this show came up in someone's listening." Worth
  revisiting if the new ranking still feels off.
- **Engagement gate (require ≥N tracks before counting).** Would
  drop the "Scarlet > Fire only" case, which is real signal. The
  logical-listen primitive already treats one-track and 22-track
  plays as 1 listen each, which is the right symmetry: depth
  doesn't get rewarded *or* penalized.
- **Velocity / momentum ranking.** Still deferred from v1. The
  current windowing (now / week / month / all) already differentiates
  short-window novelty from long-window perennials; adding a "spike
  vs baseline" score would mostly help when a single show suddenly
  appears, which doesn't happen often in a catalog of known shows
  with stable taste.

## References

- `api-data/analytics.db` — production analytics database (pulled via
  `make db-pull-analytics`).
- `Makefile` — `db-pull-analytics` target for fetching prod data.
- Existing schema: `analytics_events`, `analytics_daily_rollup`.
- Event: `playback_start` with props `{ show_id, recording_id, track_index }`.
