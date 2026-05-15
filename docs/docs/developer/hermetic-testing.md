# Hermetic Test Framework

The hermetic test framework is a WireMock-based fake backend that lets
iOS, Android, and web builds run fully against committed fixtures —
no real archive.org traffic, no real API traffic. Tests, manual repro,
and offline development all share the same container.

The decision and trade-offs live in two ADRs:

- [`docs/adr/0001-synthetic-universe-for-e2e-testing.md`](https://github.com/ds17f/deadly-monorepo/blob/main/docs/adr/0001-synthetic-universe-for-e2e-testing.md) — why WireMock + mitmproxy capture
- [`docs/adr/0003-cross-platform-shared-test-assets.md`](https://github.com/ds17f/deadly-monorepo/blob/main/docs/adr/0003-cross-platform-shared-test-assets.md) — how iOS, Android, and web share it

Implementation is tracked under the parent epic **DEAD-346**, with
sub-tickets DEAD-347 through DEAD-357.

## Quick start

From the monorepo root:

```bash
make hermetic-up       # start WireMock (port 8090 by default)
make hermetic-logs     # tail logs
make hermetic-ps       # status
make hermetic-down     # stop
```

Smoke check the container:

```bash
curl http://localhost:8090/__admin/health
curl http://localhost:8090/__admin/mappings
```

Override the host port if 8090 is taken:

```bash
HERMETIC_PORT=9000 make hermetic-up
```

## Layout

```
hermetic/
  docker-compose.yml   # WireMock service (Caddy + TLS added in DEAD-356)
  README.md            # operational details
  fixtures/
    mappings/          # WireMock stub mappings (captured + curated)
    __files/           # binary bodies (audio, images, data.zip)
    captures/          # raw mitmproxy .flow files
    synthetic/         # hand-authored mappings for non-real test data
```

The mappings and `__files` directories are mounted read-only into the
container at `/home/wiremock/mappings` and `/home/wiremock/__files` —
the conventional layout WireMock auto-loads on startup.

## How the app points at it

Both platforms now have a "Hermetic mode" toggle + URL field in
Developer Settings (DEAD-350 on iOS, DEAD-351 on Android). The
underlying model is **one switch, all traffic** — when the toggle is
on, every outbound HTTP(S) request `https://<host>/<path>` is
rewritten to `http://<hermetic>/<host>/<path>`. The original host
becomes the first path segment so a single WireMock instance can
serve any number of upstreams:

```text
https://archive.org/metadata/foo  →  http://10.0.2.2:8090/archive.org/metadata/foo
https://api.github.com/repos/...  →  http://10.0.2.2:8090/api.github.com/repos/...
```

Steps to use:

- Open the app in a Debug build (release builds don't ship the toggle UI yet)
- Developer Settings → **Hermetic mode** → enable
- **Hermetic server URL** → `http://localhost:8090` (iOS simulator) /
  `http://10.0.2.2:8090` (Android emulator) / `http://<lan-ip>:8090`
  (real device)
- Tap "Reload against hermetic server"

### Platform implementations

- **Android** (`HermeticInterceptor` in `core/network/hermetic/`) — an
  OkHttp interceptor applied to a shared `@BaseOkHttp` `OkHttpClient`.
  All upstream network modules (archive, github, genius, wikipedia)
  inject the base client and call `.newBuilder()` to add their own
  quirks, so the interceptor wires through transparently.
- **iOS** (`HermeticURLProtocol` in `Core/Network/`) — a `URLProtocol`
  registered against `URLSession.shared` at app launch. AVPlayer
  bypasses URLProtocol (it uses CFNetwork directly), so audio URL
  construction sites call `AppPreferences.hermeticRewrite(_:)`
  explicitly.

## Coverage

What hermetic mode currently catches, what it doesn't, and where the
gaps live.

### Caught

| Surface | Android | iOS |
|---|---|---|
| archive.org metadata fetches | ✅ via `ArchiveModule` | ✅ via URLProtocol |
| GitHub releases (`data.zip`) | ✅ via `GitHubNetworkModule` | ✅ via URLProtocol |
| Genius lyrics | ✅ via `GeniusNetworkModule` | ✅ via URLProtocol |
| Wikipedia | ✅ via `WikipediaNetworkModule` | ✅ via URLProtocol |
| Audio playback (AVPlayer / ExoPlayer) | ✅ via `OkHttpDataSource` | ✅ `PlaylistServiceImpl` + `CarPlayTrackResolver` |
| Image loading | ✅ Coil `ImageLoader` with `@BaseOkHttp` | ✅ AsyncImage uses URLSession |
| Auth (the deadly API) | ✅ via injected `@BaseOkHttp` client | ✅ URLProtocol |
| Background URLSession (downloads) | n/a | ✅ explicit `hermeticRewrite()` at task creation |

### Remaining caveats

- **Reload button is a no-op** on both platforms. Clients re-read the
  URL on every request, so the next call routes correctly without any
  explicit reload — but caches keyed by recording ID may serve stale
  data across mirror swaps. Concrete cache invalidation lands with
  DEAD-352 (data isolation) / DEAD-355 (CI smoke).

## Host-check guard (DEAD-353)

A safety net: when hermetic mode is on, every outbound request must
target the hermetic host. If something slips past the normal rewrite
plumbing, the guard fails the request loudly.

**Android** — `HostCheckInterceptor` (`core/network/hermetic/`)
installed on the shared `@BaseOkHttp` `OkHttpClient` AFTER
`HermeticInterceptor`. When hermetic mode is on and a request's host
doesn't match the configured base, it throws `IOException` with the
offending URL.  Catches:

- A new module that builds its own `OkHttpClient.Builder()` instead of
  injecting `@BaseOkHttp`
- An idempotence-guard or parse-failure edge case in
  `HermeticInterceptor`

What it does **not** catch on Android: anything using a non-OkHttp
HTTP client (e.g., `HttpURLConnection`, `java.net.http.HttpClient`).
That's surfaced at test time via WireMock's request journal — any
expected request that doesn't show up is an escape.

**iOS** — no runtime counterpart. `HermeticURLProtocol`
unconditionally intercepts `URLSession.shared` traffic and the
`StreamPlayer` `urlTransformer` unconditionally rewrites every URL
handed to the audio engine. There's no logical layer between those
and the network where a guard could sit that the existing code paths
don't already cover. AVPlayer / CFNetwork code that bypasses both
would also bypass any imaginable in-process guard; that class of
escape is detected externally via WireMock's request journal during
test runs, same as the Android non-OkHttp case. If we ever ship
hermetic mode in release builds we can revisit this with ATS-level
allowlisting in the test target.

## Capture workflow

Fixtures are derived from real archive.org traffic. Pipeline:

1. **Capture**: `make capture-start` runs `mitmdump` on port 8888 and
   records to `hermetic/fixtures/captures/<timestamp>.flow`. Point a
   dev device's HTTP proxy at this machine and drive the scenario.
2. **Convert**: `make capture-convert FLOW=...` runs
   [`hermetic/scripts/flow_to_wiremock.py`](https://github.com/ds17f/deadly-monorepo/blob/main/hermetic/scripts/flow_to_wiremock.py),
   which sanitizes the capture (header allowlist, drop cookies/auth)
   and emits WireMock mappings + body files into
   `hermetic/fixtures/mappings/` and `hermetic/fixtures/__files/`.
3. **Commit**: the mappings, the bodies, and the raw `.flow` so the
   conversion can be re-run if the converter changes.

The unrelated `tools/network-fault-proxy/` (a fault-injection mitmproxy
addon for testing retry paths) stays where it is.

## What's not here

- The production Docker stack (UI / Caddy / API / Redis) is in the
  root `docker-compose.yml`. It is unrelated and stays separate.
- Test-assertion DSL on top of the container is a follow-up tied to
  ADR-0002 (structured trace logging) — separate epic.
