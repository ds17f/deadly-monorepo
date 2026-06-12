# ADR-0012: Show index as a boot-loaded in-memory JSON map (and when to move it to SQLite)

## Status

Accepted (2026-06-12). Documents a decision already shipped in
`feat(web/player): surface recording selection across show page and player`
(commit `78849112`, PR #66). This ADR records *why the JSON-map shape was kept*
when per-recording display data was added to it, and — more importantly — sets
the **explicit trigger** at which we should stop and migrate to SQLite.

## Context

The API holds no show catalog of its own. `api/src/showCatalog.ts` loads a
compact index from `show-index.json` **once at boot** into an in-memory `Map`,
built offline by `api/scripts/build-show-index.mjs` (`make api-show-index`). It
enriches user-data responses (recents/favorites display fields) and backs web
auto-advance's `getNextShow` (the browser has no catalog, so it asks the API for
the next show chronologically — ADR-0010).

PR #66 added a per-recording array to each show entry so the new web recording
picker works for Connect-hydrated / refreshed sessions (where the client has no
`activeShow.recordings`). The API serves a show's recordings by id from the same
in-memory map. **This roughly 5×'d the index on disk and in resident memory
(~0.6 MB → ~3.1 MB).**

That growth is the thing this ADR exists to flag. The index now carries
**display data per recording**, not just per show — a dimension that grows with
both the recording count *and* the number of fields each recording carries. The
file is also the **only** show-metadata store living as a loose JSON blob next to
the real SQLite DBs (`users.db`, analytics, notifications) in the mounted data
dir — it is the odd one out, and `better-sqlite3` is already a dependency and
already loaded in-process.

So the question was raised directly: **is JSON-in-memory strictly better than
just adding a SQLite table?** It is not strictly better — it is a deliberate
trade that is correct *today* and has a clear expiry.

## Decision

**Keep the boot-loaded in-memory JSON map for now.** Do not migrate to SQLite in
PR #66. The data is read-only, regenerated wholesale by the build script, never
mutated at runtime, and read on hot paths by key (`catalog.get(showId)`) or by a
cached chronological key-walk (`getNextShow`) — exactly the shape an in-memory
map serves well and cheaply, with no query or serialization overhead per read.

**But this is debt with a named trigger, not a settled end state.** Migrate the
show index to a SQLite table (queried by `showId`, keeping at most a small
in-memory structure for `getNextShow`'s chronological walk) when **any** of these
fire:

1. **More per-recording fields get added.** This is the load-bearing trigger.
   The index grew 5× the *first* time recordings gained display data; the second
   field-set (track listings, per-recording reviews, waveforms, lineage text,
   download sizes, …) is where "load the entire catalog into heap to serve one
   show" stops being defensible. **If you are reaching for this file to add a
   recording field, that is the signal to do the SQLite migration instead.**
2. **Resident memory starts to matter** on the API host (it does not today;
   ~3 MB on a single instance is noise).
3. **The API needs to scale horizontally.** The in-memory map is per-instance;
   so is the Connect connection state (ADR-0011 §Consequences). A shared store
   becomes necessary at the same time for both.

The breadcrumb to this ADR lives in `api/src/showCatalog.ts` (the consumer) and
`api/scripts/build-show-index.mjs` (the producer), at the points where adding a
recording field would be tempting.

## Consequences

- **We accept ~3 MB of resident heap and a full-file JSON reparse at boot/regen**
  in exchange for zero-overhead keyed reads and the smallest possible diff for the
  recording-picker feature. Fine at current scale on a single instance.
- **The index is the one show-metadata store not in SQLite.** We keep an
  inconsistency (loose JSON beside the DBs) deliberately and temporarily, with the
  migration trigger written down so the inconsistency does not quietly ossify.
- **The next person to add a recording field inherits a decision, not a blank
  slate.** The code breadcrumbs route them here; this ADR tells them the field
  they are about to add is most likely the one that should trigger the migration
  rather than another bump to the blob.
- **`getNextShow`'s chronological walk is the one operation that genuinely wants
  the whole keyspace in memory.** A SQLite migration should preserve a cheap path
  for it (an `ORDER BY show_id` index, or retain a small sorted-id array in
  memory) rather than naively round-trip per advance.

## Alternatives considered

- **Migrate to a SQLite table now, in PR #66.** Rejected for *that PR*: #66 is a
  web feature, and the storage swap is an API refactor touching `loadShowCatalog`
  / `getShowMeta` / `getNextShow` and the build script's output contract. It
  deserves its own change and review, not a rider on a UI feature. This ADR is the
  standing decision to do exactly that migration when a trigger above fires.
- **Stream/lazy-load recordings from the per-recording JSON files at request
  time** (skip baking them into the index). Rejected: trades the memory cost for
  per-request disk reads and filesystem coupling in the API, and still is not the
  consolidated, indexed store the real fix wants. SQLite is the better
  destination than either the fat blob or loose file reads.
- **Keep growing the JSON blob indefinitely.** Rejected as the default path — it
  is precisely the "quietly accrete display fields until it hurts" failure this
  ADR is written to interrupt. The map is fine; *unbounded field growth on it* is
  the part we are pre-committing to stop.
