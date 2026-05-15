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

The Developer Settings "Hermetic server URL" field lands in
**DEAD-350** (iOS) and **DEAD-351** (Android). Until then, base-URL
override is wired manually per platform. After those tickets:

- Open the app in a Debug build
- Developer Settings → Hermetic server URL → `http://localhost:8090`
  (simulator) / `http://10.0.2.2:8090` (Android emulator) /
  `http://<lan-ip>:8090` (real device)
- Tap reload — the app re-bootstraps against the container

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
