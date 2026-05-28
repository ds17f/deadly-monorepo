# ADR-0005: Fan Favorites rail (decade pools)

## Status

Accepted (2026-05-27)

## Context

ADR-0004 shipped the **Trending** rail, which surfaces "what people are
playing right now" via logical-listen counts. Trending answers the
listening question well, but it does not answer a different one the
user explicitly wanted on the home screen: **"what shows did people
keep?"** — i.e. retention signal, not consumption signal.

The data exists. Every `add_favorite` and `remove_favorite` action
fires a `feature_use` event with `target_type='show'` and the show id
in `target_id`. At time of writing the prod database holds **425
favorite-adds and 39 removes over 30 days from 97 distinct users**.
Roughly 84 distinct shows have at least one current favoriter.

A naive design — "show the top N shows by net favorites" — runs into
several issues that surfaced during the first attempt (commit
`67057322`, replaced by the design in this ADR):

1. **Distribution is flat.** With ~97 users across 84 favorited shows,
   most shows sit at 1–2 favoriters. The top of the global ranking is
   determined by 1–2 marginal favorites, not by a meaningfully stronger
   signal.

2. **The list felt static.** A favorites-ranked list refreshes only
   when someone adds a favorite, which at current volume is roughly
   every 1–2 hours. The same 5 shows sat at the top for the entire
   day, making the rail feel dead.

3. **The catalog has natural era texture.** The Dead played
   1965–1995 — five decades that fans pick favorites from for very
   different reasons (60s = early experiments, 70s = peak, 80s =
   second wind, 90s = late-era). A global ranking erases that texture;
   the top of the list is heavily 70s and 80s for the same reasons
   they're heavily represented in the catalog.

4. **There's product appetite for a "show me something new" gesture.**
   The user explicitly wanted a "Show more" affordance — "I want to
   roll the dice again on this rail." A pure-rank design has nothing
   to roll.

The product goal here is **discovery via retention signal**, not
consensus ranking. Different from Trending: we are not trying to find
"the" most-loved shows; we want to use the favorites signal as a
filter to surface shows that *someone* kept, and let the user keep
re-rolling within that filtered universe.

## Decision

### Endpoint shape

A single `GET /api/popular` returns four per-decade *pools*:

```json
{
  "generated_at": "2026-05-27T22:00:00Z",
  "decades": {
    "60s": [ { "show_id": "...", "favorites": 2, "listens": 7, "ratio": 0.29 }, ... ≤20 ],
    "70s": [ ... ≤20 ],
    "80s": [ ... ≤20 ],
    "90s": [ ... ≤20 ]
  }
}
```

The server returns **pools**, not a pre-picked display set. The client
picks its 4-show display set locally from these pools and re-rolls on
"Show more" without another network call.

The response is cached in Redis under `popular:v3:b{bucket}` with a
6-hour TTL (longer than one rotation window so old buckets age out
naturally). Cache key includes the rotation bucket index so a new
rotation gets a fresh key.

### Pool construction: stable 4h shuffle

For each decade D ∈ {60s, 70s, 80s, 90s}:

1. **Qualifying set** = all shows with net favorites ≥
   `POPULAR_MIN_FAVORITES` (default 1) whose `show_id` resolves to a
   year in that decade.
2. **Shuffle** with a deterministic seed derived from
   `floor(now_ms / (POPULAR_ROTATION_HOURS * 3600_000))` and the
   decade name. Same rotation window → same shuffle.
3. **Take the first `POPULAR_PER_DECADE`** (default 20) → that decade's
   pool.

The shuffle uses a small SplitMix-style PRNG so the result is fully
deterministic for a given (rotation window, decade) pair. A user
returning within the same 4h window sees the same pool; the rail
rotates on its own at the window boundary.

### Display-set selection: client side

The home rail always shows **4 shows**. Two modes:

- **`all`** (default): one show from each non-empty decade pool
  (≤ 4 total), date-sorted.
- **Specific decade** (60s / 70s / 80s / 90s): 4 shows from that
  pool, biased toward **year diversity within the decade** so the
  rail spans the era. Implemented as: shuffle the pool with a
  client seed, then greedily pick shows whose year hasn't been
  picked yet, filling any leftover slots from repeats.

Both modes are driven by a local `shuffleSeed` (an integer counter).
"Show more" bumps the seed → instant re-roll, no network round-trip.

### Decade preference lives in Settings

The user's decade choice is a persisted preference
(`homePopularDecade`), set via a segmented control in
Settings → Home Screen. There is **no in-header cycler** for the
decade — that role belongs to "Show more," which re-rolls the
*content* within the user-chosen decade rather than changing the
decade itself.

### Year extraction from `show_id`

Show IDs in prod are slug-form: `1977-05-11-st-paul-civic-center-...`.
The decade-bucketing code extracts the year by matching the first
4-digit run in the ID. This is intentionally tolerant — older test
fixtures use the `gd1977-...` form, and both work.

### Tunable knobs (env vars, no app release required)

| Variable | Default | What it controls |
|---|---|---|
| `POPULAR_MIN_FAVORITES` | 1 | Floor: minimum net favoriters to enter a decade pool. |
| `POPULAR_PER_DECADE` | 20 | Cap on the per-decade pool size returned. |
| `POPULAR_ROTATION_HOURS` | 4 | How long a pool is stable before it rotates. |

Floor stays at 1 in the early stage because the current data has most
shows at 1–2 favoriters; floor=2 produced a near-empty rail in
manual testing. Raise as the install base grows.

### What "net favorites" means

Per (install, show), keep only the install's most recent favorite
action. An add followed by a remove nets to zero (excluded). A
remove followed by a re-add nets to one (included). Count distinct
installs whose last action is `add_favorite` per show. This avoids
double-counting indecisive users and gives us a true
"currently favorited by N people" number.

### Ratio is kept in the response but not used for ranking

The `ratio = favorites / max(listens, 1)` field is preserved in the
wire format because it's still the cleanest one-number "retention
intensity" signal. We don't rank by it anymore — pool membership is
binary (passes the floor or doesn't), and within-pool ordering comes
from the shuffle. The field is exposed for transparency and possible
future use (e.g., debug overlay).

## Consequences

### Positive

- **Discovery survives small-N data.** With ~84 favorited shows
  spread across four decades, sampling pools instead of ranking
  globally lets every decade contribute regardless of how popular
  the *most-favorited* show in another decade is.
- **The rail feels alive.** The 4h rotation gives a fresh set
  several times a day without any user input; "Show more" gives
  unlimited re-rolls. Both are deterministic locally so re-renders
  are stable.
- **Return-visit stability.** Within the 4h window, the pools are
  the same. A user who sees a show they liked yesterday and comes
  back today within the window can still find it. Pure random per
  request would have failed this test.
- **Settings-driven decade preference is honest.** The user picks
  the era they're in the mood for; the app doesn't try to be clever
  about it. Year-spread within a decade means the rail meaningfully
  covers the era rather than four shows from '77.
- **Cheap.** Server work is one favorite-pool query per request,
  cached for the rotation window. Client work is in-memory shuffle
  of ≤20 items. No new tables, no rollup job, no scheduled tasks.
- **Server-tunable.** Floor, pool size, rotation window are all env
  vars. We can tune the rail's behavior as the install base grows
  without shipping app updates.

### Negative

- **Floor=1 surfaces 1-user picks.** A show favorited by exactly
  one person can show up in someone else's home rail. At current
  scale this is the only way the rail can be non-empty for the 60s
  and 90s, so we accept it. Raise the floor via env var once the
  long tail thickens.
- **"All" is a shallow view.** Four cards — one per decade — is the
  whole flyover. Users wanting more variety have to either tap
  "Show more" repeatedly or pick a specific decade.
- **Each client re-implements the picking logic.** iOS and Android
  each have their own `displayShows(decade, seed)` implementation,
  with a seeded PRNG and the year-spread algorithm. Drift is a
  real risk if one side gets edited without the other. Mitigation:
  the algorithm is small and each side has its commit message and
  this ADR as the canonical reference; web (when it exists) gets
  a port from one of these.
- **The "all" set seen by the client may not match what any
  server-computed `all` would return.** We removed the server's
  `all` bucket explicitly to avoid this confusion — there is only
  one source of truth for "all," and it's the client.
- **"Show more" telemetry doesn't tell us which shows the user
  saw.** We track the tap as a `feature_use` event with the
  selected decade, but not the resulting display set. If we ever
  want to know "did users keep tapping until they hit something
  they liked," we'd need to instrument the displayed show IDs
  too. Deferred.
- **Slug-format show IDs assume year is the first 4-digit run.**
  This works for the current catalog but is a small implicit
  schema dependency. If show IDs ever stop carrying the year in
  the slug, the decade bucketing breaks silently (everything goes
  to no-decade).

## Alternatives considered

**Ratio-ranked top-5 (shipped briefly in commit `67057322`).**
Ranked by `favorites/listens` with floor=3, top-5 globally. Rejected
after a few days: the list felt static (no rotation), the floor was
too aggressive for current data, and the single ranking erased era
texture. Replaced by this design.

**Pure random per request.** No seeding, fresh shuffle each call.
Rejected because returning users had no way to find a show they'd
seen earlier — the rail churned constantly. The 4h stable shuffle
preserves return-visit recognition.

**Server-side re-roll via `?seed=N` query param.** Each "Show more"
tap fires a fetch. Rejected because (a) it makes the tap feel slow
(visible network roundtrip), (b) it requires the server to be
involved in what is fundamentally a UI animation, and (c) the
client already has all the data it needs once the pool is fetched.

**Floor ≥ 2.** Tried this as the initial default. Empties the 60s and
90s buckets entirely against current prod data — those decades have
fewer shows in the catalog and fewer favoriters overall. Bumped
back to 1; the env var stays so we can re-raise as volume grows.

**Per-decade pool sizes that vary.** E.g., 30 for 70s/80s, 10 for
60s/90s to match where the catalog density actually is. Considered.
Rejected for v1 because the client only ever displays 4 from each
pool; a larger pool just gives more re-roll variety, which is the
same value whether the decade is dense or sparse.

**Weighted-by-favorites sampling within a decade.** Pick shows from
the qualifying pool with probability proportional to favorites,
rather than uniform. Considered. Rejected because the user
explicitly wanted a *discovery* surface — uniform sampling gives the
long tail real visibility, which is the point.

**Server returns the "all" flyover.** Earlier iteration of this
design did. Removed because we had to compute it twice — once on
the server, then again on the client because the client owns the
re-roll. Two sources of truth for the same data felt wrong, and
deleting the server's `all` was simpler than keeping them in sync.

**In-header decade cycler ("Show 70s", "Show 80s", …).** Shipped
briefly in commit `5d626407`. Replaced because (a) the cycler
combined two different actions — change decade *and* re-roll — into
the same tap, which was confusing once "Show more" became its own
thing, and (b) the small chip target had questionable accessibility.
The Settings picker is the explicit decade choice; "Show more" is
the unlimited re-roll within that choice.

**Engagement weighting (e.g., favor shows the user spent time on).**
Out of scope for this rail — that's a personalization question, and
Fan Favorites is deliberately *global*. The personalized equivalent
would be a separate rail.

## Open questions

- **Floor when volume grows.** What's the right floor at 1,000 users?
  10,000? Need to revisit after the install base grows by 10×. The
  env var means we can tune in production without re-deploying app.
- **Track-favorites ("Best of <song>") rail.** Sibling design with
  the same retention-signal motivation but ranked by track, not show.
  Unit 3 in `PLANS/home-discovery-rails.md`, deferred until track
  favoriting telemetry is clean.
- **Cross-platform parity for the picking algorithm.** Manual right
  now. If we add a web client we should consider a shared algorithm
  spec or a tiny shared library; not warranted for two clients.

## References

- `PLANS/home-discovery-rails.md` — discovery rails plan, of which
  this is Unit 2.
- ADR-0004 — Trending shows, the sibling discovery rail.
- `api/src/db/analytics.ts` — `getPopularShows` implementation.
- `api/src/routes/popular.ts` — `/api/popular` route.
- `iosApp/deadly/Core/Service/PopularService.swift` — iOS pool model
  and `displayShows(for:seed:)` picking.
- `androidApp/core/api/home/.../PopularService.kt` — Android pool
  model and `displayShows(decade, seed)` picking.
- Commits: `67057322` (initial ratio-ranked), `5d626407` (in-header
  cycler), `19a9ea6b` (decade pools), `d8383e9b` (Show More).
