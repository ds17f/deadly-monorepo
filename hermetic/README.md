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

## What goes here vs. elsewhere

- **`hermetic/fixtures/`** — the static fixture data. Committed.
- **`tools/network-fault-proxy/`** — the mitmproxy capture pipeline that
  *produces* mappings for `hermetic/fixtures/mappings/`. (Extended by
  DEAD-348.)
- **Root `docker-compose.yml`** — production-style UI/API/Caddy/Redis
  stack. **Not related to hermetic.** Kept separate on purpose.
