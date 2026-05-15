# ADR-0002: Structured trace logging

## Status

Proposed (2026-05-14)

## Context

Logs today are prose. A typical playback log line:

```
[PB] loadQueue gen=2 count=23 startIdx=0 autoPlay=true first=gd81-05-15d1t01.mp3 last=gd81-05-15d3t04.mp3
[PB] stateChanged paused → bufferring
[PB] didStartPlaying matched prev=0 → actual=0 wasAutoAdvance=false
```

The information is there. The structure is not. Each line is a freeform
string whose schema lives only in the developer's head. Following the
fate of `gen=2` through 60 seconds of resolve, a stop, a retry, and a
resume requires reading the log top-to-bottom and recognizing the
relevant lines by eye. Tests can't assert on the sequence — they'd
have to regex-match strings. Bug reports require an expert reader.

The DEAD-335 and DEAD-344 investigations both depended heavily on this
log narrative. We've gotten lucky that the prose has been
*approximately* consistent. But:

- **Ordering bugs are invisible to state-based tests.** The DEAD-344
  bug wasn't that the engine reached a wrong final state — it was that
  the sequence of events between submission and resolve included audio
  playback from the previous queue. State-based assertions can't catch
  that without ordering data.
- **Users can already file bug reports** via the `BugReportView` added
  in DEAD-335, but the logs they ship are the same prose. Reproducing
  the bug from the report is forensics work.
- **The Phase rewrite (DEAD-344) introduces formal state transitions.**
  Each transition is a discrete event worth logging structurally.
  Doing so naïvely produces more prose; doing so well produces a
  trace.

The opportunity: define the log format up front, before the rewrite
lands, so every new event is born structured.

## Decision

We define a **structured trace logging standard** for the playback
subsystem (and progressively the rest of the app):

1. **Fixed event vocabulary.** A small, namespaced set of operation
   names — `queue.load`, `queue.resolve.start`, `queue.resolve.done`,
   `phase`, `player.play`, `player.stop`, `player.seek`, `retry.start`,
   `retry.attempt`, `retry.success`, `retry.giveup`, `error.unexpected`,
   `error.surfaced`, `seek.start`, `seek.land`, `progress.tick`, etc.
   The list lives in one Swift file as constants. Adding a new event
   is one line in one place.

2. **Key-value fields, not interpolated prose.** Each event carries
   structured fields: `gen=2`, `idx=0/23`, `url=…`, `reason=new-queue`,
   `elapsedMs=60381`, `from=playing`, `to=buffering`. Field keys are
   stable across events that share them — `gen` always means
   load-generation, `idx` always means current/total queue index.

3. **Single `PlaybackTrace` API.** Centralizes formatting. Writes to
   three sinks:
   - OSLog channel (preserves today's `os_log` integration and Console
     visibility).
   - In-memory ring buffer (last ~10 minutes at full verbosity).
     Accessible to tests and to the bug report bundler.
   - Optional file-based persistence (off by default, on when user
     enables debug logging in Developer settings).

4. **Verbosity levels.** Three: `notice` (production default, surfaces
   significant transitions and errors), `info` (all transitions and
   guards), `debug` (everything including per-tick progress). Set via
   a Developer setting; persisted.

5. **Test helpers consume the trace.** Tests can assert on event
   sequences:

   ```swift
   let trace = container.logTracer.events
   trace.assertSequence([
       .event("queue.load", gen: 2),
       .event("player.stop", reason: "new-queue"),
       .event("queue.resolve.done", gen: 2),
       .event("phase", to: "playing"),
   ])
   ```

   This catches ordering bugs that state-only assertions miss.

6. **Bug report bundler exports the ring buffer** in the same format
   the tests consume. A user trace and a test trace look identical —
   one can be converted into the other mechanically.

7. **Migration is gradual.** Existing `[PB]` prose lines stay until
   their surrounding code is touched. New events are born structured.
   The Phase rewrite (DEAD-344) migrates the playback engine in one
   pass; the rest of the app follows opportunistically.

## Consequences

### Positive

- **Tests can assert event ordering.** A whole class of race-condition
  bugs becomes detectable in automated tests.
- **Bug reports are reproducible.** A user trace is a fixture config
  away from being a failing test.
- **Logs are greppable and parseable.** `grep "phase.transition"`
  returns all state changes. `grep "gen=2"` returns everything that
  happened to that generation. `awk` can compute timings.
- **Shared vocabulary across the stack.** Engine, StreamPlayer, and
  services emit traces using the same event names and field
  conventions. Reading a trace doesn't require switching mental
  models per subsystem.
- **Debug toggles are user-facing.** A user hitting a bug enables
  debug logging in Developer settings, reproduces, ships the trace.
  No engineer-on-call required for log capture.
- **The format outlives any specific subsystem.** Structured events
  work for analytics, for performance instrumentation, for any future
  area that needs cross-cutting traceability.

### Negative

- **Discipline required to keep vocabulary stable.** It's tempting to
  add `queue.load.fancy` when `queue.load reason=fancy` would do.
  Without review pressure the namespace grows; with review pressure
  it doesn't. Mitigation: vocabulary lives in one file; PR review
  considers additions deliberately.
- **Performance cost.** Structured logging is slightly more expensive
  than `os_log` interpolation, especially when fields include large
  strings. Mitigation: events at `debug` verbosity are gated; ring
  buffer enforces a memory cap.
- **Migration is gradual and partial.** Until all prose `[PB]` lines
  are migrated, traces have gaps. Acceptable cost; the playback
  subsystem migrates as part of DEAD-344 and is the highest-value
  area to do first.
- **Ring buffer is RAM.** ~10 minutes at full verbosity is a few MB.
  Acceptable on iOS. We don't ship a hours-long buffer to disk by
  default; that requires user opt-in.
- **Bug report payloads are larger.** A debug-verbosity trace is
  bigger than today's prose log. We compress before upload; net
  bandwidth is small.

## Alternatives considered

**Keep prose logs, add ad-hoc structure where needed.** What we have
now. Rejected because the bugs we keep hitting are ordering bugs, and
prose logs can't be asserted on by tests without regex fragility.

**Use OSLog signposts.** Apple's `OSSignposter` for performance
intervals. Excellent for timing analysis in Instruments; not designed
for narrative event streams or for cross-process consumption by
tests. Complementary, not a replacement.

**Use a third-party structured-logging library (swift-log, Logging,
Datadog SDK, etc.).** swift-log is the SSWG standard but is more about
backends than format. Heavyweight libraries (Datadog, Sentry breadcrumb
APIs) bring server-side ingestion machinery we don't need. Rejected in
favor of a small, app-owned API tailored to playback's needs. If we
later want server-side ingestion, the structured format makes it
trivial.

**Use Sentry breadcrumbs.** Sentry is a viable analytics surface but
the breadcrumb model is bound to its crash-report workflow. We want a
trace usable in tests and bug reports independent of Sentry. Reject
as the primary mechanism; could feed Sentry as one of several sinks.

**Define a logging schema in protobuf / JSON Schema.** Over-engineered
for our use. A Swift enum of operation-name constants plus a
dictionary of fields is enough.

## Cross-platform applicability

This ADR's decisions apply across iOS, Android, and web. The format,
event vocabulary, and field conventions are platform-neutral by
design; only the plumbing (which platform-native logger emits the
event, where the ring buffer is stored) differs.

The vocabulary lives as a canonical document at the monorepo root
(`docs/playback-events.md`) and is consumed identically by all
platforms. Per-platform implementations follow the same naming, the
same field keys, and the same verbosity levels. A trace exported
from Android and a trace exported from iOS look identical to a
reader.

ADR-0003 codifies this shared-asset arrangement.

This ADR's *implementation* is iOS-first because that's where DEAD-344
lands. Android and web implementations follow the same contract when
those platforms adopt the format.

## Open questions

- **Vocabulary first cut.** This ADR commits to the principle; the
  initial event list and field conventions land in the implementation
  ticket and the canonical doc. Expect 20–40 distinct events at
  first; trim if it grows.
- **Platform-specific events.** When a platform has an event that's
  genuinely platform-specific (e.g., a CarPlay-specific transition),
  it lives in the vocabulary with a platform prefix (`ios.carplay.*`)
  rather than polluting the cross-cutting namespace.

## References

- `PLANS/DEAD-344.md` — the Phase rewrite that triggers this
  observability work and consumes the new format.
- `iosApp/Packages/SwiftAudioStreamEx/Sources/SwiftAudioStreamEx/LogExport.swift`
  — existing log export, will be upgraded to bundle trace ring buffer.
- `iosApp/deadly/Feature/Settings/BugReportView.swift` — existing user
  bug-report UI, will surface a trace verbosity toggle.
- ADR-0001 — synthetic universe. The test harness consumes traces;
  bug reports produce traces in the same format.
