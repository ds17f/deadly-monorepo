# Hermetic test framework

WireMock-based fake backend that lets iOS, Android, and web builds run
fully offline against committed fixtures derived from real archive.org
traffic. See `docs/adr/0001-synthetic-universe-for-e2e-testing.md` and
`docs/adr/0003-cross-platform-shared-test-assets.md` for the why.
Implementation work is tracked under DEAD-346.

## Layout

```
hermetic/
  docker-compose.yml   # WireMock service (Caddy + TLS added in DEAD-356)
  README.md            # this file
  scripts/             # capture → mapping conversion tooling
    flow_to_wiremock.py
    README.md
  fixtures/
    mappings/          # WireMock stub mappings (captured + curated)
    __files/           # binary bodies (audio, images, data.zip)
    captures/          # raw mitmproxy .flow files, kept for re-conversion
    synthetic/         # hand-authored mappings for non-real test data
```

Mappings and `__files` are mounted read-only into the container at
`/home/wiremock/mappings` and `/home/wiremock/__files` respectively —
the layout WireMock auto-loads on startup.

## Run

From the monorepo root:

```bash
make hermetic-up        # start the container
make hermetic-logs      # tail logs
make hermetic-ps        # show status
make hermetic-down      # stop + remove the container
```

By default WireMock is exposed on host port **8090** (the production
dev stack already uses 8080 for Caddy). Override with
`HERMETIC_PORT=9000 make hermetic-up` if needed.

## Smoke test

```bash
curl -s http://localhost:8090/__admin/health
# {"status":"healthy","version":"3.10.0",...}

curl -s http://localhost:8090/__admin/mappings
# {"mappings":[],"meta":{"total":0}}    (empty until DEAD-349 lands fixtures)
```

Anything not matched by a mapping returns 404 with a WireMock body.

## Admin API

WireMock's admin surface lives at `/__admin`. Tests configure
scenario-specific behavior here at runtime — adding stubs, injecting
delays, returning errors for the next N requests, resetting state. See
https://wiremock.org/docs/standalone/admin-api-reference/.

## Capture workflow

Fixtures are derived from real archive.org traffic. The pipeline:

```text
   real device → mitmproxy → archive.org
                    ↓
                .flow file
                    ↓
   flow_to_wiremock.py
                    ↓
   hermetic/fixtures/mappings/  + hermetic/fixtures/__files/
                    ↓
              WireMock serves
```

### 1. Capture

From the monorepo root:

```bash
make capture-start
```

This runs `mitmdump` on port 8888, restricted to archive.org hosts,
writing to `hermetic/fixtures/captures/<timestamp>.flow`. Point your
dev device's HTTP proxy at this machine's LAN IP on port 8888 (one-time
mitmproxy cert install required — see
`tools/network-fault-proxy/README.md`).

Drive the app through the scenarios you want to fixture. Stop with
Ctrl+C.

Tune the capture scope via env vars:

```bash
CAPTURE_HOSTS='\.archive\.org|\.thedeadly\.app' make capture-start
CAPTURE_PORT=9999 make capture-start
```

### 2. Convert

```bash
make capture-convert FLOW=hermetic/fixtures/captures/<file>.flow
```

This emits sanitized mappings and body files into `hermetic/fixtures/`.
See `hermetic/scripts/README.md` for what survives the sanitization
pass and what's dropped.

### 3. Commit

Review the generated mappings + bodies, drop anything irrelevant, then
commit the artifacts. The raw `.flow` file under `captures/` is also
committed so the conversion can be re-run if the converter changes.

## Coverage

The Android and iOS apps now have a Developer Settings "Hermetic mode"
toggle (DEAD-350 / DEAD-351). When on, outbound traffic is rewritten to
the configured hermetic server with the original host as the first path
segment.

See [`docs/docs/developer/hermetic-testing.md`](../docs/docs/developer/hermetic-testing.md#coverage)
for the full coverage matrix — what's caught (most of archive, GitHub,
Genius, Wikipedia, image loading, AVPlayer audio on iOS) and what
isn't (ExoPlayer audio + Auth + Coil on Android; background
URLSessions on iOS). Gaps are tracked as a single umbrella follow-up
under DEAD-346.

## SELinux note (Fedora/RHEL)

The compose file mounts fixture directories with the `:z` flag, which
performs an SELinux relabel-shared on the host directory so the
WireMock container can read it. Harmless on distros without SELinux.
If you run WireMock via raw `docker run`, remember to add `:ro,z` to
your `-v` flags.

## What goes here vs. elsewhere

- **`hermetic/fixtures/`** — the static fixture data. Committed.
- **`tools/network-fault-proxy/`** — the mitmproxy capture pipeline that
  *produces* mappings for `hermetic/fixtures/mappings/`. (Extended by
  DEAD-348.)
- **Root `docker-compose.yml`** — production-style UI/API/Caddy/Redis
  stack. **Not related to hermetic.** Kept separate on purpose.
