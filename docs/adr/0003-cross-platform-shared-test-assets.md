# ADR-0003: Cross-platform shared test assets

## Status

Proposed (2026-05-14)

## Context

ADR-0001 commits us to a containerized fake backend (WireMock +
mitmproxy capture pipeline) for end-to-end testing. ADR-0002 commits
us to a structured trace logging format for playback observability.

Both decisions were written iOS-first because the bug class that
motivated them (DEAD-335, DEAD-344) lives in the iOS playback engine.
But the app is a monorepo: iOS, Android, and web all consume the same
external services (archive.org, GitHub Releases for `data.zip`, the
API server), all play back audio, and all face the same class of
network-flakiness and ordering bugs.

If each platform implements its own mock backend, its own fixture
format, and its own log vocabulary, we end up with three approximately-
parallel test universes that drift apart over time. Bug reports from
an Android user wouldn't look like bug reports from an iOS user.
Cross-platform regressions (server-side data changes, archive.org
behavior shifts) wouldn't be detectable by any one platform's tests.
Engineers would switch mental models every time they crossed a
platform boundary.

The monorepo structure exists in part to avoid this. We should use it.

## Decision

The fake backend container, its fixture data, and the trace log format
are **shared assets at the monorepo root**, consumed identically by
iOS, Android, and web test suites. Platform-specific code is limited
to the test runner that drives the app under test and the log sink
that emits trace events; the protocol, vocabulary, and fixture data
are shared.

Specifically:

### 1. Shared fixture container

A single WireMock container image, defined in `hermetic/docker-compose.yml`
under the dedicated `hermetic/` root (separate from the production
`docker-compose.yml` that hosts UI/API/Caddy/Redis). Image is the
upstream `wiremock/wiremock`; we add no custom code to the container
itself — only mappings and binary fixtures loaded at startup.

All test suites consume this container:

- **iOS** points `archiveBaseURL` at the container, drives via XCTest.
- **Android** points `BuildConfig.ARCHIVE_BASE_URL` at the container,
  drives via instrumented JUnit. Emulator-to-host networking uses
  `10.0.2.2` instead of `127.0.0.1`; otherwise identical.
- **Web** runs the container alongside the dev server, configures
  `fetch` base URL to route archive URLs there.

The control surface — WireMock's `/__admin` REST API — is identical
across platforms. A test in Swift that POSTs to `/__admin/mappings`
looks the same as a test in Kotlin or TypeScript doing the same.

### 2. Shared fixture data

Under `hermetic/fixtures/`:

```
hermetic/fixtures/
  mappings/              # WireMock stub mappings (recorded + curated)
  __files/               # Binary bodies (audio, images, data.zip)
  captures/              # Raw mitmproxy flows
  synthetic/             # Hand-authored mappings for non-real test data
  data.zip               # Synthetic data fixture
```

These files are platform-neutral by construction:

- MP3 audio fixtures decode identically on iOS, Android, and web.
- WireMock mappings are JSON; not language-bound.
- The synthetic `data.zip` is the canonical schema, which all
  platforms' data layers consume.
- mitmproxy flows are a stable format; the conversion script lives in
  `tools/network-fault-proxy/` and produces WireMock mappings any
  platform can use.

When a new fixture or scenario is added, it's added once, in one
place, available to every platform's test suite immediately.

### 3. Shared trace log vocabulary

ADR-0002 defines a fixed event vocabulary for playback (`queue.load`,
`phase`, `player.play`, etc.) and conventions for field names (`gen`,
`idx`, `url`, `reason`).

This vocabulary lives at `docs/playback-events.md` (or similar) as the
canonical reference. Each platform implements the trace API in its
native logging primitive (OSLog on iOS, Timber on Android, console +
sink on web) but emits events with **identical names and identical
field keys**.

Consequences:

- A user bug report from any platform produces a trace that any
  engineer can read without context-switching.
- The same test-assertion DSL (`trace.assertSequence(...)`) works
  against any platform's emitted log.
- Cross-platform bugs correlate trivially — `grep "queue.resolve.done"`
  across all three platforms' logs.
- New events are added once, in the canonical vocabulary doc, and
  implemented per-platform.

### 4. Shared scenario specifications

The list of test scenarios — "new show selection silences previous
audio", "mid-track failure auto-retries", "cold launch restoration",
etc. — lives at `docs/playback-test-scenarios.md` as a canonical
list. Each platform implements each scenario locally; the canonical
list ensures every platform tests the same intents and exit criteria.

When a new bug class is discovered, the scenario is added to the
canonical list. All platforms then implement it. This prevents one
platform's coverage from silently lagging another's.

### 5. Platform-specific code is thin

Each platform's test suite contains only:

- A test runner that boots the app with the container's base URL
  substituted in.
- Helpers for driving the app via the platform's service layer
  (`container.playlistService.loadShow(...)` and equivalents).
- A platform-native trace log sink that emits events conforming to
  the shared vocabulary.
- The platform's implementation of each canonical scenario.

Everything else — fixtures, container, vocabulary, scenario list —
is shared.

## Consequences

### Positive

- **One source of truth** for fixtures, container behavior, log
  format, and scenario coverage. No drift between platforms.
- **Mechanical cross-platform regression detection.** When archive.org
  changes, every platform's tests fail in the same way. When the data
  schema changes, every platform's tests fail. We catch
  cross-platform breakage early.
- **Lower cost to add a new platform.** A future watchOS or macOS
  client doesn't have to build its own test universe; it adopts the
  shared assets and writes only the thin platform-specific harness.
- **Lower cost to onboard engineers.** Reading a trace, writing a
  fixture, adding a scenario — these skills transfer across all
  platforms.
- **Honest division of labor.** The shared assets capture what's
  genuinely cross-cutting (fixtures, vocabulary). The per-platform
  code captures what's genuinely platform-specific (test runner, log
  plumbing). No false sharing, no missed sharing.

### Negative

- **Coordination cost on changes to shared assets.** A change to the
  vocabulary or the fixture format affects all platforms. Mitigation:
  changes go through PR review like any other; the affected
  platforms' tests fail loudly if a breaking change isn't propagated.
- **Lowest-common-denominator risk.** If one platform needs a
  vocabulary extension specific to its environment (e.g., a
  CarPlay-specific event), there's a temptation to over-share. We
  accept this risk: platform-specific events are allowed in the
  vocabulary, scoped with a prefix (`ios.carplay.*`), but the core
  cross-cutting events stay shared.
- **Container lifecycle handled differently per platform.**
  TestContainers on Android and web; CI service / Compose helper on
  iOS. The container itself is shared; just the bring-up plumbing
  diverges.

## Alternatives considered

**Three independent test universes, one per platform.** What we'd
default to if we did nothing. Rejected because the cost of drift —
silent coverage gaps, bug reports that look different, engineers
context-switching — outweighs the coordination cost of sharing.

**Share only the fixtures, not the vocabulary or scenario list.**
Cheaper to implement, but the bug-report and cross-platform-grep
value of a shared vocabulary is too high to give up. And without a
canonical scenario list, coverage drifts anyway.

**Share everything, including the platform-specific harness.** Some
teams attempt this with cross-platform test frameworks (KMM for tests,
Appium, etc.). Rejected because the harness needs deep access to the
platform's service layer and async primitives, which a cross-platform
abstraction would hide or distort. The platform-specific harness
layer is small and worth keeping native.

## References

- ADR-0001 — containerized service virtualization. This ADR
  describes how the container and fixtures from 0001 are shared.
- ADR-0002 — structured trace logging. This ADR describes how the
  vocabulary from 0002 is shared.
- `PLANS/DEAD-344.md` — the immediate motivation (iOS playback) that
  triggered all three ADRs.
- Monorepo layout under `hermetic/`, `tools/network-fault-proxy/`,
  `docs/playback-events.md`, `docs/playback-test-scenarios.md`.
