# ADR-0019: Transparent iOS recovery from failed Archive CDN URLs

## Status

Proposed (2026-08-22).

This ADR covers the iOS streaming path in `SwiftAudioStreamEx`. It does not
change Android or web playback.

Related: [ADR-0010](0010-playback-auto-advance-and-show-queue.md) defines
natural track/show completion, and the package's
[playback logging contract](../../iosApp/Packages/SwiftAudioStreamEx/LOGGING.md)
defines what is retained in submitted bug reports.

## Context

Archive track URLs have two identities in the iOS player:

- The **canonical URL** identifies the recording and file on archive.org.
- The **resolved URL** is the redirect target on an Archive CDN host and is the
  URL handed to AudioStreaming.

AudioStreaming 1.4.4 does not reliably play through the canonical redirect, so
`AudioStreamEngine.loadQueue` resolves every canonical URL before playback and
keeps the resulting CDN URLs in memory for the lifetime of the loaded queue.
The on-disk Archive metadata cache contains track metadata, not these resolved
URLs.

Field reports in `bug-reports/no-archive` show `serverError` failures within
seconds of successful resolution. The evidence does not support URL age as the
initial cause. It does show that a resolved CDN endpoint can stop serving a
track while the logical queue remains valid. Retrying the same CDN URL therefore
does not repair the underlying condition.

The current retry path already stops AudioStreaming. This is important because
`AudioPlayer.stop()` destroys its internal forward queue. Each retry currently
rebuilds that queue, but it rebuilds it from the same `queue.resolved` values.
The stopped interval is therefore the natural boundary at which to replace the
transport URLs without replacing the user-visible queue.

### Verified AudioStreaming constraint

AudioStreaming does **not** require the complete track list before playback:

- It exposes `queue(url:)`, `queue(urls:)`, and `queue(url:after:)`.
- Deadly already calls `play(current)` first and submits the remaining tracks
  from `didStartPlaying`.
- `play(url:)` clears AudioStreaming's queue. Consequently no URL may be queued
  between `play(url:)` and the matching `didStartPlaying` callback.

The unfinished `DEAD-344/ios-playback-state` investigation independently
verified the same constraint. Its broader lazy-loading/state-machine refactor
is not required for this repair.

### Why changing `queue.resolved` in place is unsafe

The logical track list, the engine's resolved URL array, AudioStreaming's entry
identifiers, pending forward URLs, callback matching, and the visible current
index must agree. Replacing arbitrary URLs while AudioStreaming is running can
make callbacks identify the wrong entry or make the internal queue diverge from
Deadly's queue.

Recovery must therefore be a controlled stop-and-rebuild operation. Resolved
URLs may change only inside a new recovery generation, and AudioStreaming may
receive the rebuilt URLs only in original track order.

## Decision

### 1. Distinguish CDN server failures from generic network failures

The existing generic network retry and the new CDN URL recovery represent
different events and remain independently testable. They share a dispatcher,
but they do not execute the same recovery behavior:

```swift
handleNetworkFailure(
    kind: PlaybackNetworkFailureKind,
    source: NetworkFailureSource
)
```

`PlaybackNetworkFailureKind` has two cases:

- `.connectivity` covers ordinary network errors and the buffering-stall
  watchdog. It retains the existing bounded retry of the current resolved URL.
- `.cdnServer` covers AudioStreaming's `serverError`, which AudioStreaming 1.4.4
  emits for an HTTP response of 300 or greater. It starts the canonical refresh
  and queue reconstruction defined by this ADR.

`NetworkFailureSource` independently records whether the trigger came from the
AudioStreaming delegate, buffering watchdog, or a developer control. Kind
selects behavior; source provides traceability.

The pinned dependency exposes these failure names through its error
description rather than a stable public case discriminator. The adapter
therefore centralizes the existing string classification: `serverError` maps to
`.cdnServer`; other `networkError` values map to `.connectivity`. If a future
dependency version changes those descriptions, classification tests must fail
before the dependency is updated.

On the first `.cdnServer` failure in a burst, the engine:

1. Captures the logical current index, playback position, play/pause intent,
   canonical track URLs, volume, load generation, and failed resolved host.
2. Mutes and stops AudioStreaming immediately.
3. Enters a new recovery generation and reports `.buffering` / `isRetrying` to
   the existing UI.
4. Clears pending AudioStreaming URLs and treats every resolved URL in the
   loaded queue as requiring refresh.

Stopping prevents AudioStreaming from advancing to a pre-buffered successor
while recovery is underway. The public `StreamPlayer.tracks`, recording
metadata, queue index, Connect identity, and now-playing metadata are not
reloaded or replaced.

### 2. Refresh the whole logical queue from canonical URLs

Recovery resolves every track again from its canonical URL using an ephemeral,
non-caching URL session. A candidate is usable only after a small ranged GET
(`Range: bytes=0-1`) follows redirects and returns HTTP 200 or 206. The resolver
captures the final response URL and cancels body transfer after the first two
bytes, so a server that ignores `Range` cannot make the app download an entire
track during validation. A successful candidate may resolve to the same host as
before: the failure may have been transient, and health—not hostname novelty—is
the acceptance rule.

Resolution has a per-request timeout and uses the existing bounded retry
deadline. Failure to resolve or validate a URL never silently substitutes an
unvalidated stale CDN URL.

Refresh work is prioritized as follows:

1. Current track.
2. Immediate next track, when one exists.
3. Remaining forward tracks.
4. Tracks before the current index, for future skip-back behavior.

Resolution concurrency is capped at four requests. The two foreground slots
are submitted first; background work cannot occupy them before current and next
have started. Results are stored by logical index. Completion order never
determines playback order.

### 3. Use a two-track foreground recovery window

The engine waits for validated URLs for the current track and its immediate
successor before restarting playback. If the current track is last, only the
current URL is required.

This “front porch” prevents a recovered current track that is near its end from
finishing before the next track has been queued. It preserves the existing
gapless intent without waiting for the slowest URL in the show.

Once the foreground URLs are ready, the engine atomically installs their new
mappings for the active recovery generation, calls `play(current)`, and keeps
the remainder in a generation-scoped staging buffer.

If the captured intent was paused, URL recovery still runs but the engine does
not call `play(current)` on its own. It returns to a prepared paused state. The
next user Play starts the refreshed current URL, and its `didStartPlaying`
callback drains the staged forward queue normally. Developer injection while
paused therefore cannot turn into unintended audible playback.

### 4. Append background results only after `didStartPlaying`, in order

The `didStartPlaying` callback is accepted only when both the recovery
generation and expected current URL match. At that point the no-queue window has
closed and the engine queues the immediate successor plus every contiguous
resolved successor already available.

As later resolutions complete, the engine appends only the newly contiguous
prefix. For example, if tracks 5 and 6 resolve before track 4, neither is queued
until track 4 resolves; then 4, 5, and 6 are appended in one ordered operation.
This avoids both reordering and gaps in AudioStreaming's forward queue.

Tracks before the current index are refreshed in the engine's mapping but are
not appended to AudioStreaming. A later skip backward starts a fresh
`play(url:)` operation and rebuilds the forward queue by the existing rule.

### 5. Resume silently at the captured position

The existing recovery audio contract remains in force:

- Audio is muted before restarting at 0:00.
- Once the expected current track reaches `.playing`, the engine seeks to the
  captured position.
- Volume is restored only after the seek completes.
- Progress updates from 0:00 are suppressed while the recovery seek is pending.

Recovery does not infer that a track near its end should advance. Natural track
completion remains AudioStreaming's responsibility after playback resumes.

If AudioStreaming reaches the end of the currently submitted prefix while the
logical show still has an unresolved successor, the engine must not emit
`onQueueComplete`. It records the completed logical track once, promotes the
successor to the foreground target, and remains buffering until that successor
is validated and started at 0:00. This is a recovery gap, not the end of the
show.

### 6. Make recovery generation-scoped and cancelable

Recovery owns a monotonically increasing identifier separate from the ordinary
queue-load generation. Every resolver completion, delayed retry, player
callback, and seek completion captures both identifiers and is ignored unless
they still match.

A new queue load, show selection, stop, or newer recovery cancels the old
recovery. A skip during recovery also supersedes the old transport intent: if
the target and its successor are already validated they start normally;
otherwise a new recovery generation is centered on the requested target and
prioritizes those URLs. Completed validated mappings may be reused, but no
unvalidated old mapping is revived. Cancellation restores any recovery-owned
mute before handing control to the new user action. A late callback from the
stopped AudioStreaming instance cannot change the current index, queue URLs,
retry state, or volume.

The recovery state is conceptually:

```swift
struct CDNRecovery {
    let id: Int
    let loadGeneration: Int
    let currentIndex: Int
    let canonicalURLs: [URL]
    let resumePosition: TimeInterval
    let shouldResumePlayback: Bool
    let failedHost: String?

    var resolvedByIndex: [Int: URL]
    var nextForwardIndexToQueue: Int
    var didStartExpectedCurrent: Bool
    var attempts: Int
    var deadline: Date
}
```

This scoped state is added to the current engine rather than adopting the full
phase enum proposed by DEAD-344. A broader playback-state refactor can absorb it
later, after equivalent end-to-end coverage exists.

### 7. Preserve bounded retries and the existing user-facing failure

Generic `.connectivity` failures retain the current 1s, 2s, and 4s same-URL
backoff, bounded by the existing ten-second deadline. This handles transient
loss of connectivity without discarding otherwise valid CDN mappings.

`.cdnServer` failures use the same starting schedule and deadline, but each
attempt refreshes from canonical URLs; it does not replay a known failed CDN URL
without validation.

When the current/next foreground window cannot be made ready inside the budget,
the engine restores volume, stops, exits `isRetrying`, and surfaces the existing
Archive.org network error with the captured resume position. A user's manual
Retry starts a new recovery generation from canonical URLs.

Background failures after playback has resumed do not interrupt audible audio.
They remain pending and retry within the generation. If playback reaches a
missing successor before it becomes ready, that condition enters the same
network-recovery handler rather than skipping over the track.

### 8. Add deterministic CDN fault injection beside generic fault injection

Developer settings retains the existing **Inject Network Error** action and
adds a separate **Simulate CDN Failure** action in debug builds:

- **Inject Network Error** calls the existing
  `StreamPlayer.debugInjectNetworkError()` and enters the `.connectivity` path.
- **Simulate CDN Failure** calls
  `StreamPlayer.debugSimulateCDNFailure()` and enters the `.cdnServer` path. It
  is enabled when a stream queue has a current track and no CDN recovery is
  already active.

Tapping **Simulate CDN Failure** calls
`StreamPlayer.debugSimulateCDNFailure()`. The engine injects the event at the
same internal seam used by a real AudioStreaming `serverError`, while the real
network remains healthy. This intentionally produces a deterministic,
recoverable incident: it exercises capture, stop, canonical re-resolution,
two-track foreground preparation, ordered background rebuild, seek, unmute,
and UI state without depending on Archive to fail on demand.

The existing `debugInjectNetworkFailure()` implementation remains. Both debug
methods are thin triggers for their respective production paths, so there is no
third debug-only recovery implementation. The new CDN button and API are
compiled under `#if DEBUG`; production classification and recovery are not.

On tap, the app shows a short toast confirming the injected recovery ID. The
user can leave Settings, observe playback, exercise skip/seek behavior, and ship
the collected logs manually through the existing bug-report flow.

### 9. Persist a complete, privacy-safe recovery trace

Every recovery transition is logged at `.notice` or higher with the `[PB]` tag
so it survives into `BugReportView`. Each line includes `kind=connectivity|cdn`,
`source=player|watchdog|developer`, `loadGeneration`, and, for CDN recovery,
`recoveryId`.

The trace records:

- failure detection, current index/count, captured position, and failed host;
- stop completion and recovery start;
- each canonical resolution attempt by track index, requested host, final host,
  HTTP status, duration, and success/failure category;
- foreground current/next readiness;
- `play(current)` submission and expected `didStartPlaying` match/mismatch;
- every ordered queue flush as an index range;
- seek start/completion and volume restoration;
- background refresh completion counts;
- cancellation and its reason;
- final success with total duration, or exhaustion with attempts and elapsed
  time.

Logs omit response bodies, public/client IP addresses, authentication material,
and localized low-level network text. Stable `NSError` domain/code, Archive
hosts, public Archive filenames, indices, status codes, and timings are safe to
record. The HTTP diagnostics introduced for opaque `serverError` callbacks are
retained and correlated with the same recovery ID.

### Recovery sequence

```text
AudioStreaming             AudioStreamEngine          Archive/CDN
      |                           |                         |
      | serverError              |                         |
      |-------------------------->|                         |
      |                    capture index/position           |
      |<--------- stop -----------|                         |
      |                    begin recovery R                 |
      |                           |-- resolve current ------>|
      |                           |-- resolve next --------->|
      |                           |-- resolve remainder ---->|
      |                           |<-- validated current ----|
      |                           |<-- validated next -------|
      |<---- play(current) -------|                         |
      | didStartPlaying           |                         |
      |-------------------------->|                         |
      |<---- queue(next + ready contiguous successors) -----|
      |                    seek captured position            |
      |                    restore volume                    |
      |                           |<-- later results --------|
      |<---- queue(new contiguous range) -------------------|
      |                           |                         |
```

## Concurrency and ownership rules

All reads and writes of the logical queue, recovery context, pending URL staging
buffer, generations, retry bookkeeping, resume position, and saved volume occur
under the engine lock. Network operations never hold the lock. They return an
immutable result containing their captured identifiers and index, then perform
a short guarded state update.

Calls into AudioStreaming and outward callbacks are made after releasing the
lock. This avoids lock re-entry through synchronous delegate callbacks.

There is exactly one owner of the next forward index to submit. A helper such as
`drainContiguousForwardURLsLocked()` advances it and returns an ordered array for
submission after unlock. Individual resolver callbacks never call
`player.queue(url:)` directly.

## Implementation plan

### Phase 1 — Failure taxonomy, injection, and observable checkpoint

- Add the `.connectivity` / `.cdnServer` classification and log both kind and
  source at the shared dispatcher.
- Keep **Inject Network Error** and its existing same-URL retry behavior.
- Add **Simulate CDN Failure** and route it through the same `.cdnServer`
  classification seam used by a real AudioStreaming `serverError`.
- Add the CDN recovery ID and state-capture scaffold without changing queue URLs
  yet; during this phase it falls through to the current bounded retry after the
  new event is recorded.
- Retain the HTTP diagnostics and correlate them with the CDN recovery ID.

**Checkpoint:** build and install Phase 1 on the development iPhone, play a
stream, fire each button separately, and manually ship the logs. Do not begin
queue reconstruction until the trace proves that the generic button produces
`kind=connectivity`, the CDN button produces `kind=cdn`, and the real-error
adapter is wired to the same CDN seam.

### Phase 2 — Foreground CDN recovery

- Introduce a small production URL resolver using an ephemeral session, ranged
  validation, explicit timeout, and structured result.
- Complete `CDNRecovery` generation/cancellation state.
- Replace same-resolved-URL retry **only for `.cdnServer`** with canonical
  current/next refresh.
- Gate restart on both foreground results and preserve the existing muted resume
  seek and terminal error behavior.
- Add generation guards to every new asynchronous completion.

### Phase 3 — Whole-queue background reconstruction

- Resolve all remaining canonical URLs with the defined priority and concurrency
  cap.
- Add indexed background staging and contiguous ordered queue draining.
- Handle skip promotion, previous-track refresh, and exhaustion of a submitted
  prefix without false show completion.
- Verify that generic connectivity retries still leave the resolved queue alone.

### Phase 4 — Full verification and cleanup

- Add unit coverage for resolution acceptance, queue ordering, cancellation,
  timeout, retry exhaustion, and stale callbacks.
- Run package tests and the full iOS simulator suite.
- Verify the on-device matrix below before shipping.
- Remove only the superseded `.cdnServer` same-resolved-URL retry path. Retain
  the generic network injector and `.connectivity` retry path.

## Verification

### Automated tests

The recovery planner and resolver boundaries must be independently testable.
Tests cover:

- current and next resolve before playback restarts;
- no next-track requirement at the final queue index;
- background completions arriving out of order;
- a hole preventing later tracks from being queued;
- the hole resolving and flushing one ordered contiguous range;
- previous tracks refreshing without entering the forward player queue;
- canonical redirect returning the same now-healthy CDN host;
- non-2xx validation, timeout, and retry exhaustion;
- a second recovery invalidating first-generation completions;
- a new queue load, skip, or stop canceling recovery;
- a skip promoting an unresolved target and successor into a new foreground
  recovery window;
- late `didStartPlaying`, state, seek, and resolver callbacks being ignored;
- exhaustion of the submitted AudioStreaming prefix not emitting a false
  show-complete event while a logical successor remains;
- manual Retry starting a fresh canonical refresh;
- developer CDN injection and a real `serverError` producing the same transition
  sequence except for their logged source;
- generic developer injection remaining on the separate `.connectivity` retry
  path.

### Development iPhone matrix

For each scenario, tap **Simulate CDN Failure**, observe behavior, then manually
ship the collected logs through the existing **Send Bug Report** screen:

1. Middle of a track: resumes at the same audible position without a burst from
   0:00.
2. Within ten seconds of track end: advances into the next track without a new
   stall.
3. Paused track: recovery does not create unintended audible playback.
4. Seek while recovering: the latest user seek intent wins.
5. Skip next and skip previous while background refresh is active: logical
   ordering remains correct.
6. Select a different show during recovery: the old show never resumes.
7. Background and foreground the app during recovery: no duplicate playback or
   stuck mute.
8. Trigger repeated incidents in one listening session: each has an independent
   recovery ID and retry budget.
9. Let the show naturally advance after recovery: track-complete and
   show-complete behavior remains unchanged.

### Acceptance criteria

- A recoverable injected or real CDN failure resumes the same logical track at
  the captured position without user action.
- The immediate successor is ready before audible playback resumes.
- Every track receives a newly resolved, validated mapping during recovery.
- AudioStreaming receives forward URLs exactly once and in logical order.
- Queue metadata, current show identity, Connect state, and user-visible track
  ordering do not reset.
- Switching shows or issuing a newer transport action always wins over recovery.
- Exhausted recovery produces the existing user-facing error and remains
  manually retryable.
- A submitted playback bug report is sufficient to reconstruct the complete
  recovery timeline by recovery ID.

## Consequences

### Gained

- A CDN failure repairs the stale transport mapping instead of replaying it.
- The full in-memory queue is refreshed without delaying restart on every track.
- The next track is ready before near-end playback resumes.
- Deterministic on-device fault injection makes a rare field condition
  repeatable and produces directly shareable evidence.
- Generation and ordering rules make late asynchronous work harmless.

### Accepted costs

- Recovery adds a second generation-scoped lifecycle beside ordinary queue
  loading until a broader playback phase model is justified and tested.
- Each recovery makes additional small Archive requests for every track.
- Playback waits for both current and next validation, which can be slower than
  restarting current alone.
- A persistent Archive/CDN outage still reaches the existing terminal error;
  this design repairs stale routing but cannot manufacture a healthy server.

## Alternatives considered

### Retry the same resolved CDN URL

Rejected. This is the current behavior and cannot repair a bad CDN mapping. It
also allows the next pre-resolved track to fail for the same reason.

### Refresh only the current track

Rejected. A near-finished current track could advance immediately into another
stale URL, and subsequent tracks would retain the condition that triggered
recovery.

### Resolve the full queue before restarting

Rejected. It is safe but unnecessarily couples recovery time to the slowest
track. AudioStreaming supports incremental queue submission, and the current
plus next tracks are the only immediate playback dependencies.

### Replace URLs while AudioStreaming continues running

Rejected. AudioStreaming identifies entries by URL and maintains its own
internal queue. In-place substitution risks callback mismatches and divergence
between the two queues.

### Adopt the full DEAD-344 playback phase refactor first

Rejected for this repair. The refactor is broader, was deliberately paused
pending an end-to-end harness, and increases regression surface. The scoped
recovery lifecycle can later become a phase without changing this ADR's
external behavior.

### Simulate failure with a fake or unreachable URL

Rejected as the primary developer control. It tests a special debug network
path, can strand recovery until its timeout expires, and may not execute the
same production handler. Injecting at the real error-handler seam is
deterministic and exercises the entire repair while permitting successful
canonical refresh.
