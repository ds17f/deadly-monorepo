# Resuming the hermetic test framework work

> Drop-in context document for picking up where the DEAD-347 branch
> left off. Delete this file when the branch lands.

## Where we are

Branch `DEAD-347` has 12 commits beyond `origin/main` plus uncommitted
WIP from a long evening-of-2026-05-15 session that reframed the
direction. **DEAD-349 is not done in the original "captured fixtures"
sense and probably won't be — the new shape is hand-authored synthetic
fixtures.** See below.

Status of the epic sub-tickets:

| Done / In Review | Backlog |
|---|---|
| DEAD-347 container scaffold | DEAD-349 fixtures — **reframed**, see below |
| DEAD-348 capture pipeline | DEAD-352 data isolation (probably close, see ticket) |
| DEAD-350 iOS dev setting + URLProtocol | DEAD-355 CI smoke run |
| DEAD-351 Android dev setting + OkHttp interceptor | DEAD-356 HTTPS via Caddy |
| DEAD-353 Android host-check guard | DEAD-357 beta-ready polish |
| DEAD-354 canonical scenarios + synthetic data.zip | (new) interceptor redirect-bypass fix |
| DEAD-359 coverage gaps |  |

## Tonight's session — what happened

Long capture session, lots of false starts, some real wins. The
headline conclusion: **the captured-fixture path is the wrong shape
for this framework.** Synthetic-universe is the right shape. See "the
reframe" below.

### Real outcomes worth keeping (uncommitted, on disk now)

1. **Capture pipeline proven end-to-end.** Phone (real Pixel, wireless
   adb) → mitmcap on host:8889 → `.flow` file → `flow_to_wiremock.py`
   → 65 mappings serving via WireMock → app routes through
   HermeticInterceptor → 26 hits, 0 unmatched on a cold launch
   verification round. The plumbing works.

2. **Two real bugs found and fixed:**

   - `hermetic/docker-compose.yml`: removed `--global-response-templating`.
     The flag applies Handlebars to every response body, including binary
     mp3/jpg bodies, which contain `{{` byte sequences that trip the
     templater with a 500. Fixture-level opt-in (`"transformers":
     ["response-template"]` per mapping) is the correct shape; we don't
     need it yet.

   - `hermetic/scripts/flow_to_wiremock.py`: added `"location"` to
     `RESPONSE_HEADER_ALLOWLIST`. Without it, 3xx mappings come out
     missing their `Location` header — breaking any redirect chain.
     (Mostly a moot fix now since synthetic universe shouldn't have
     redirects, but the script is still useful for ad-hoc capture so
     keep the fix.)

3. **Real architectural bug discovered, NOT fixed:** OkHttp's
   auto-redirect happens *below* the application-interceptor layer.
   When github.com returns a 302 → release-assets.githubusercontent.com,
   neither `HermeticInterceptor` (URL rewrite) nor `HostCheckInterceptor`
   (DEAD-353's "last line of defense") re-runs for the redirect target.
   The data.zip in tonight's verification *escaped hermetic mode* and
   was downloaded from the real internet. The HostCheckInterceptor
   guard is not actually airtight.

   Mitigation analysis (see end-of-session conversation):
   - Just `addNetworkInterceptor(...)` isn't enough — by the time
     network interceptors run, the TCP connection is already open to
     the wrong host. URL rewrites need to happen at application layer.
   - Cleanest fix: a `RedirectFollowingInterceptor` at the top of the
     chain that disables OkHttp's auto-redirect
     (`.followRedirects(false).followSslRedirects(false)`) and
     manually re-issues 3xx Location targets via `chain.proceed(...)`,
     which re-enters the rest of the chain (HermeticInterceptor +
     HostCheckInterceptor) for each redirect target. Production
     behavior unchanged because the interceptors are mode-aware no-ops
     when hermetic mode is off.
   - For synthetic-universe fixtures we control every URL, so no
     redirects exist and this bug doesn't bite. Still worth fixing for
     defense in depth before any real-traffic capture path is shipped.

4. **Synthetic `data.zip` trimmed from 6 shows → 1 show.** The original
   DEAD-354 list (1965 / 1969 / 1972 / 1977 / 1990 / 1993) was built
   for scenario breadth that doesn't have tests yet — overbuilding.
   `hermetic/scripts/build_synthetic_datazip.py` now has 1 entry
   (`1972-05-26-the-strand-lyceum-london-england`, 4 recordings,
   4.7 KB output). **About to be discarded entirely** — see the
   reframe.

### On-disk WIP that should be cleaned up

- `hermetic/fixtures/mappings/*.json` — 65 captured mappings (gone via reframe)
- `hermetic/fixtures/__files/*` — ~49 MB captured body data (gone via reframe; keep `.gitkeep`)
- `hermetic/fixtures/captures/20260515-175608.flow` — **KEEP** as schema-reference flow file. Don't commit it (large), but don't delete either — it's the source of truth for "what does archive.org's metadata JSON actually look like" when authoring synthetic fixtures.

## The reframe

The captured-fixture path replays real archive.org responses. Problems:

- Real responses have redirect chains, signed URLs, time-bounded JWTs.
- Real responses change when archive.org changes (no test/prod parity
  control).
- Fixtures from real captures are huge (49 MB tonight) for content
  that has nothing to do with our test scenarios.
- Recording-content-as-test-content couples us to whatever shows
  happened to be on the home screen when we drove the capture.

The synthetic-universe path:

- Hand-author a tiny, totally fake show on a date the Dead never
  played. **1979-05-17** is chosen (no real show; nothing to collide
  with; obvious "this is fake" marker).
- Every URL is under our control. No redirects, no signed assets, no
  third-party drift.
- Audio is generated locally (ffmpeg sine waves, ~10 KB each), not
  captured.
- Start with 1 recording / 2 tracks; grow only when a specific test
  scenario justifies more content.

This is also what the user articulated as the actual goal:

> we make a synthetic data.zip; it has one or two shows; they're fake;
> the app loads them; we can play, next, previous. all of the data
> becomes a self-contained universe we serve through our hermetic
> server.

## The 1979-05-17 design

**Show ID**: `1979-05-17-<fake-venue-slug>` — e.g.
`1979-05-17-the-imaginarium-nowhere-usa`. Date the Dead didn't play;
obviously synthetic venue.

**Recording (v1, just 1):**
- `gd1979-05-17.sbd.synth.0001.sbeok.shnf` (1 recording today; future
  scenarios may add multiple recordings of varying source types to
  exercise the "which-recording-type-overlay" UI — but we don't yet
  understand how source types are encoded in the ID grammar, so
  punted to a future session).

**Tracks (2):**
- `gd79-05-17d1t01.mp3` — synthetic 220 Hz sine wave, ~5 sec
- `gd79-05-17d1t02.mp3` — synthetic 440 Hz sine wave, ~5 sec
- Different frequencies so on a real phone you can hear "track 2
  started" without looking at the screen.

**WireMock mappings** (5–6 total, all hand-authored):
- `GET /github.com/ds17f/deadly-monorepo/releases/download/data-v2.3.0/data.zip` → 200, synthetic `data.zip` body
- `GET /api.github.com/repos/ds17f/deadly-monorepo/releases/tags/data-v2.3.0` → 200, release-tag JSON (template from tonight's capture)
- `GET /archive.org/metadata/gd1979-05-17.sbd.synth.0001.sbeok.shnf` → 200, hand-authored metadata JSON (template from tonight's capture)
- `GET /archive.org/services/img/gd1979-05-17.sbd.synth.0001.sbeok.shnf` → 200, tiny placeholder JPG
- `GET /archive.org/download/gd1979-05-17.sbd.synth.0001.sbeok.shnf/gd79-05-17d1t01.mp3` → 200, sine wave
- `GET /archive.org/download/gd1979-05-17.sbd.synth.0001.sbeok.shnf/gd79-05-17d1t02.mp3` → 200, sine wave
- Possibly placeholder `/cdn.jerrygarcia.com/...` mappings for lineup images if the app eagerly fetches them

## Codify knowledge as we author

A meta-ask from the user: **don't let next session re-discover what
this session figured out.** Codify schemas and conventions in the repo.

Two artifacts to create alongside the fixture work:

1. **`hermetic/SCHEMA.md`** — durable reference doc. One section per
   file format that needs to be hand-authored:
   - `data.zip` layout (manifest, collections, shows/, recordings/) —
     field-by-field with minimal examples
   - `archive.org/metadata/<id>` response shape — anchored to the
     captured `.flow` file as source-of-truth examples
   - Recording ID grammar (`gdYYYY-MM-DD.<source>.<taper>.<itemid>.<flags>`)
     including the still-unknown source-type encoding (mark as TODO)
   - Track filename grammar (`gd<yy>-<mm>-<dd>d<disc>t<track>.mp3`)
   - WireMock URL prefix convention (`/<host>/<path>`) — what
     `HermeticInterceptor` produces
   - Known gotchas: the templating bug, the redirect interceptor leak

2. **`hermetic/fixtures/synthetic/src/`** — hand-authored JSON files,
   version-controlled. A trivial `build_synthetic_datazip_from_src.py`
   that just zips the directory into `data.zip`. Editing fixtures
   means editing JSON files; diffs are readable; no script logic to
   absorb schema changes.

Deferred (only if it earns its keep): parametric generator
(`build_synth_show.py --date ... --venue ... --tracks ...`). Build
when 3+ synthetic shows + manual-edit toil starts hurting.

## Sequence for next session

0. **Clean up tonight's WIP:** `rm hermetic/fixtures/mappings/*.json
   hermetic/fixtures/__files/*` (keep `.gitkeep`). Confirm
   `hermetic/fixtures/captures/20260515-175608.flow` stays on disk as
   the schema-reference flow.

1. **Author `hermetic/SCHEMA.md`** by cross-referencing the real
   `dead-metadata/data.zip` (sibling to monorepo) against the captured
   archive.org responses in the `.flow` file. This is the doc that
   pays back time on every future session.

2. **Hand-author `hermetic/fixtures/synthetic/src/`:**
   - `manifest.json` (whatever data.zip's loader expects at top level)
   - `shows/1979-05-17-the-imaginarium-nowhere-usa.json`
   - `recordings/gd1979-05-17.sbd.synth.0001.sbeok.shnf.json`
   - (other top-level files the data.zip schema requires)

3. **Write `hermetic/scripts/build_synthetic_datazip_from_src.py`** —
   just zips `synthetic/src/` into `synthetic/data.zip`. Replaces
   today's `build_synthetic_datazip.py` (which trims from real source
   — wrong tool for hand-authored synthetic content). Keep the old
   script around if it has uses, or retire it.

4. **Generate audio**: two ffmpeg sine wave mp3s
   (220 Hz / 440 Hz, ~5 sec, ~10 KB each) into
   `hermetic/fixtures/__files/gd79-05-17d1t01.mp3` and `...d1t02.mp3`.

5. **Hand-author the 5–6 WireMock mappings** in
   `hermetic/fixtures/mappings/`. Each is a tiny JSON file pointing at
   the body files / inlining small JSON responses.

6. **Verify hermetically on the phone:**
   - `make hermetic-up` (port 8888 because firewall is already open
     there — `sudo firewall-cmd --add-port=8888/tcp` if needed)
   - `adb shell pm clear com.grateful.deadly.debug`
   - Manually enable hermetic mode in app Settings + set base URL to
     `http://<laptop-LAN-IP>:8888`
   - Confirm: cold launch → show list shows the 1979-05-17 show →
     tap into show → tap track 1 → hear 220 Hz → auto-advance → hear
     440 Hz → previous-track → back to 220 Hz. Done.

7. **Each step writes to `SCHEMA.md`** as new info surfaces. Doc grows
   from the work.

## Bugs to file as separate tickets

- **OkHttp interceptor redirect-bypass leak** — see "Real architectural
  bug" above. HostCheckInterceptor isn't airtight; needs
  `RedirectFollowingInterceptor` pattern. Synthetic universe doesn't
  trigger it but defense-in-depth + any future real-traffic capture
  scenario needs the fix.

- **`flow_to_wiremock.py` doesn't preserve redirect chains** — the
  Location-header fix landed but the converter still emits separate
  3xx and 2xx mappings instead of collapsing them. Not worth fixing
  until / unless we go back to capture-based fixtures.

## Quick-reference commands (unchanged from prior version)

```bash
# Bring up / take down the hermetic WireMock container
make hermetic-up                          # port 8090 default
HERMETIC_PORT=8888 make hermetic-up       # port 8888 (firewall-friendly tonight)
make hermetic-down

# Convert a captured .flow file (still useful for ad-hoc captures
# even though it's not the production fixture path)
make capture-convert FLOW=hermetic/fixtures/captures/<file>.flow

# See what WireMock is serving / has received
curl http://localhost:8888/__admin/health
curl http://localhost:8888/__admin/mappings
curl "http://localhost:8888/__admin/requests?limit=20"

# Reset WireMock's request journal between scenarios
curl -X DELETE http://localhost:8888/__admin/requests

# Real phone over wireless adb (worked tonight; emulator did not on
# this laptop's Lunar Lake GPU due to gfxstream Vulkan instability):
adb devices                               # confirms the wireless device
adb install -r androidApp/app/build/outputs/apk/debug/app-debug.apk
adb shell pm clear com.grateful.deadly.debug
adb shell "run-as com.grateful.deadly.debug cat shared_prefs/app_preferences.xml"
```
