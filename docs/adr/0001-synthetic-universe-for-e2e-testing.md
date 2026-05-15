# ADR-0001: Containerized service virtualization for end-to-end testing

## Status

Proposed (2026-05-14)

## Context

The bugs we've spent the most time chasing — DEAD-335 (stale-gen race,
auto-advance, retry recovery), DEAD-344 (new-show selection plays
previous show's audio) — are integration bugs at the seam of services,
the playback engine, and the network. They don't reproduce in isolated
unit tests because the failure depends on the interaction of multiple
real components reacting to real timing.

Verification today is hand-driven: install on a phone, arm mitmproxy
with a fault-injection script, replay the scenario, watch logs.
Effective but unscalable. The DEAD-335 verification matrix took hours
of manual work per pass and isn't repeatable in CI.

Our existing test suite covers parsers, DAOs, and service-level logic
in isolation. It does not exercise the playback engine, the audio
pipeline, the redirect resolution path, or any cross-service flow.
Recent evidence: CI on PR #40 reported "2 failures out of 327 tests"
with no information about which tests failed and no `.xcresult` artifact
uploaded.

Mock-based testing has been considered and rejected for this domain.
Mocks of `AudioPlayer` and `URLSession` would let us write fast tests,
but they would not exercise the actual library integration, the real
AudioStreaming queue semantics, the real AVAudioSession lifecycle, or
the real CoreAudio output. The bugs we care about live precisely in
those seams.

The app is a monorepo: iOS, Android, and (web) consume the same external
services. A per-platform in-process mock backend per language means
three implementations to keep in sync, three places to add new fault
modes, three sets of fixtures. The cross-platform consistency problem
has a clean answer if we lift the mock into a shared deployable.

The industry term for this pattern is **service virtualization**.
Google has written about "hermetic servers." Uber, Lyft, Spotify, and
others run shared mock backends consumed by all client platforms over
loopback or LAN, with fixtures checked into the repo and capture
pipelines from real traffic. We are reinventing nothing here.

Prior art evaluated:

- **WireMock** (Java, Apache-2.0). Mature, actively maintained. Official
  Docker image (`wiremock/wiremock`). REST admin API for stubs, recording,
  per-stub fault injection (delays, chunked dribble, mid-stream connection
  reset, malformed chunks). Multi-language by virtue of being plain HTTP.
- **Hoverfly** (Go, Apache-2.0). Purpose-built for "service virtualization";
  explicit `capture` / `simulate` / `modify` modes. Smaller footprint than
  WireMock. Fault model less rich.
- **MockServer** (Java, Apache-2.0). Feature-comparable to WireMock.
- **Mountebank** (Node, MIT). Multi-protocol (HTTP, TCP, SMTP). Smaller
  ecosystem.
- **mitmproxy `--server-replay`**. Can act as a replay server for captured
  flows. Best-in-class for *capture*; weaker for programmatic per-test
  state mutation. Right tool for one step, not the whole job.
- **VCR / Polly.js**. Language-specific in-process record-replay. Defeats
  the one-shared-backend goal. Skip.
- **TestContainers**. Standard pattern for managing Docker containers in
  tests. First-class on Java/Go/Python/Node/Rust. Weak on iOS — workable
  via shell-out or CI-side container, not in-process.

The user's instinct that "this idea must exist already" is correct. The
stack we want already exists and is battle-tested.

## Decision

We adopt a **containerized service virtualization** approach using
existing open-source tools, not a custom-built fixture server:

1. **WireMock as the fake backend.** Run as `wiremock/wiremock` Docker
   container, configured with mappings + binary fixture files mounted
   from the repo. Exposes its REST admin API on `/__admin` for per-test
   programmatic configuration of stubs, delays, and fault injection.
   One image, one runtime, all platforms consume it.

2. **mitmproxy as the capture tool.** We extend the existing
   `tools/network-fault-proxy/` Python apparatus to support a `capture`
   mode that proxies real archive.org traffic, sanitizes headers, and
   converts captured flows to WireMock mappings. This gives us
   fixtures **derived from real production traffic**, not hand-authored
   approximations. The capture step is a one-off per scenario; the
   resulting mappings are committed to the repo.

3. **Shared fixtures committed to the monorepo.** Under
   `test/fixtures/`:
   ```
   test/fixtures/
     mappings/              # WireMock stub mappings (recorded + curated)
       archive-show-A.json
       releases-data.json
       ...
     __files/               # Binary bodies (audio, images, data.zip)
       gd1972-05-16d1t01.mp3
       ...
     captures/              # Raw mitmproxy flows, retained for re-conversion
     synthetic/             # Hand-authored mappings for non-real test data
   ```
   These fixtures are platform-neutral — iOS, Android, and web all
   consume them via the same WireMock container. The synthetic
   `data.zip` lives here too.

4. **Capture workflow** (one-off, per real scenario we want to fixture):
   - Engineer points their dev device at the mitmproxy capture endpoint.
   - Plays a real show against real archive.org through the proxy.
   - mitmproxy records flows to a `.flow` file.
   - A repo script (`make capture-convert`) sanitizes and converts the
     flows to WireMock mappings + binary bodies.
   - Engineer commits the resulting mappings.

5. **Test workflow** (every test run):
   - Test infrastructure starts the WireMock container with the
     committed mappings loaded.
   - Tests configure scenario-specific behavior via WireMock's REST
     admin API (e.g., add a 5s delay to a specific stub, return 503
     for the next N requests).
   - App is pointed at the container via base-URL substitution in
     `AppContainer.bootForTests(archiveBaseURL:)`. Production code
     paths are unchanged; only URL configuration differs.
   - Tests drive through the real service layer (not the UI, not the
     engine directly). A test calls
     `container.playlistService.loadShow(id:autoPlay:)` — the same call
     the SwiftUI button's action invokes.
   - Test asserts on observable state and on log traces (per ADR-0002).
   - Test resets WireMock state via admin API in `tearDown`.

6. **Hard guarantee: no real network access from tests.** Three layers:
   ATS in the iOS test target's `Info.plist` only whitelists the
   container host; the test container asserts every constructed
   `URLRequest` targets it; CI test steps run with outbound network
   disabled where feasible.

7. **One real-traffic integration plan** runs nightly, not per-PR,
   with informational (non-blocking) status. Its sole purpose is to
   detect when WireMock's recorded responses diverge from current
   archive.org behavior. When it fails, we re-capture.

8. **Container lifecycle management is platform-agnostic.** For
   Android and web tests, TestContainers handles it natively. For iOS,
   we use a CI-side container exposed on a known port, with a shell
   helper for local dev (`make playback-fixture-up` / `down`).
   Docker Compose definition at the monorepo root coordinates dev,
   CI, and manual on-device testing against the same image.

## Consequences

### Positive

- **Don't reinvent the wheel.** WireMock is mature, well-documented,
  and used by many serious mobile teams. The exotic 5% we need on top
  is a small Python script (the mitmproxy → WireMock converter), not
  a framework.
- **Cross-platform consistency, mechanically enforced.** All three
  platforms hit the same container. Zero drift possible — the fake
  backend's behavior is a single artifact.
- **Fixtures derived from real traffic.** Recording sets a high bar
  for realism: headers, edge cases, redirect chains — all match
  production because they're sampled from production.
- **Captures are diff-able.** Re-capture old scenarios when archive.org
  changes; PR shows exactly what shifted. Easier to detect upstream
  behavioral changes than with hand-authored fixtures.
- **Deterministic and fast.** Tests don't fail because someone's CDN
  edge is slow. Smoke plan under a minute. Full plan ~10 minutes.
- **Bug reports become reproducible.** User trace points at a specific
  failure mode. Engineer encodes that mode as admin-API calls in a new
  test. Test fails. Fix lands. Test stays in regression suite.
- **Manual on-device testing improves too.** Same container can be
  used by an engineer with a real phone in hand to reproduce a scenario
  deterministically. The current mitmproxy + cert + LAN config dance
  goes away; the container speaks plain HTTP on localhost or a known
  LAN address.
- **Offline development.** Run the container, point your dev build at
  it (env var or Developer setting), build features against
  deterministic data on a plane.
- **Forces URL construction to stay routable.** Any new external
  dependency that bypasses the container's base URL gets caught by
  the test-time network-isolation assertion. New network surfaces
  can't sneak in silently.

### Negative

- **Container lifecycle in CI and local dev.** Tests need to know the
  container's up. Solved via Make targets, GitHub Actions `services:`
  blocks, and Docker Compose health checks. Standard mobile-team
  plumbing, but it's new plumbing.
- **iOS TestContainers gap.** Swift has no first-class TestContainers
  library. Workaround: container runs outside the test process (CI
  service or local Compose), exposed on a known port, simulator hits
  it directly. Slightly less hermetic than in-process, but acceptable.
  Worth a spike to confirm simulator → host networking works cleanly.
- **TLS is awkward.** We point the app at `http://container-host:port`,
  not `https://archive.org`. Different URL means we cannot test "the
  app correctly trusts archive.org's TLS chain" — but that's not a
  bug class we've ever had. Skip TLS in the fake backend.
- **Capture sanitization needs care.** Recorded responses may include
  user-identifying headers, session cookies, or content licensed by
  archive.org that we shouldn't redistribute. The conversion script
  needs a strict header allowlist and a clear policy on what audio we
  bundle. Synthetic audio fixtures remain the default for new tests.
- **Fixture drift.** When archive.org changes, mappings diverge from
  reality. Mitigation: the nightly integration plan exists specifically
  to detect this; re-capture and commit when it fails.
- **Doesn't catch view-layer bugs.** A broken SwiftUI / Compose / React
  binding passes our service-layer tests. Mitigation: a separate UI
  test layer when needed, addressed by a future ADR.
- **JVM weight.** WireMock's container is ~200MB. Not free, but boots
  in <5s on modern hardware. Acceptable.

## Alternatives considered

**Build our own in-process fixture server in each platform's test
target.** Initial proposal in this ADR's first draft. Rejected after
prior-art research: WireMock + mitmproxy gives us 95% of what we need
out of the box. Building our own would duplicate features that already
exist in production-grade form.

**Hoverfly instead of WireMock.** Close second. Purpose-built for
capture/simulate, smaller footprint, cleaner mode switching. Rejected
in favor of WireMock because: richer fault model (mid-stream connection
reset, chunked dribble, malformed chunks — these directly match the
DEAD-335 stall/cascade work), larger ecosystem, more documentation, and
existing Android/JVM team familiarity. Hoverfly remains a reasonable
swap-in if WireMock proves too heavy.

**mitmproxy alone, as both capture and serve.** mitmproxy's
`--server-replay` mode can serve recorded flows directly. Works for
basic playback but doesn't support per-test programmatic state mutation
cleanly — every test would have to restart the proxy or reload flows.
mitmproxy stays in the picture for capture; serving is WireMock's job.

**Pure unit tests with mocked `AudioPlayer` / `URLSession`.** Faster
to write per test, but mocks don't exercise the AudioStreaming library's
internal state machine, the real AVAudioSession lifecycle, the real
URL pool behavior, or the real CoreAudio output. The bugs we've been
chasing live exactly in the parts mocks would skip. Rejected.

**XCUITest tap-driven tests as the primary E2E layer.** Drives the
real UI on the simulator. Most realistic. Rejected as the primary
layer because: slow (~30s+ per test), flaky (animation timing, view
hierarchy timing), hard to inspect internal state for ordering
assertions. We may add a small XCUITest layer for view-rendering
specifics; it isn't the foundation.

**VCR / Polly-style in-process record-replay libraries.** Each
platform's test process embeds its own record-replay library and
loads its own fixtures. Cross-platform consistency requires keeping
three implementations and three fixture formats in sync. Rejected
in favor of a single shared container backend.

**Hit real archive.org from tests.** Rejected on flakiness and speed
grounds. Kept as the nightly integration plan with informational status.

## References

- `PLANS/DEAD-344.md` — Testing strategy section.
- DEAD-335 and DEAD-344 — the bug classes that drove this decision.
- `tools/network-fault-proxy/` — existing mitmproxy injector; extended
  to support capture mode by this work.
- WireMock: https://wiremock.org
- mitmproxy: https://mitmproxy.org
- Hoverfly (alternative considered): https://hoverfly.io
- Google's "Hermetic Servers" pattern (industry context): describes
  the broader principle of running shared deterministic mock backends
  for test environments.
- ADR-0002 — structured trace logging; the format consumed by test
  assertions and bug report bundling.
- ADR-0003 — cross-platform shared assets; codifies how this
  container and its fixtures are shared across iOS, Android, and web.
