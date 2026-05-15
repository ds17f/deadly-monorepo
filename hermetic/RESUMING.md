# Resuming the hermetic test framework work

> Drop-in context document for picking up where the DEAD-347 branch
> left off. Delete this file when the branch lands.

## Where we are

Branch `DEAD-347` has 11 commits beyond `origin/main`, all building
cleanly. Both platforms verified end-to-end (Android emulator, iOS
simulator) with real evidence — not just compile. **7 of 11 epic
sub-tickets shipped**:

| Done / In Review | Backlog |
|---|---|
| DEAD-347 container scaffold | DEAD-349 first captured fixture set ← blocking everything below |
| DEAD-348 capture pipeline | DEAD-352 data isolation (probably won't need work; see ticket comment) |
| DEAD-350 iOS dev setting + URLProtocol | DEAD-354 canonical scenarios + synthetic `data.zip` |
| DEAD-351 Android dev setting + OkHttp interceptor | DEAD-355 CI smoke run |
| DEAD-353 Android host-check guard | DEAD-356 HTTPS via Caddy |
| DEAD-359 coverage gaps (ExoPlayer, Auth, Coil, iOS bg downloads) | DEAD-357 beta-ready polish |

Read `docs/docs/developer/hermetic-testing.md` for the full coverage
matrix.

## The pickup plan

1. **DEAD-349** is the next thing and it needs *you* at a screen.
   Until it's done, WireMock serves 404 to everything and the
   infrastructure is unused. ~5 minutes of tapping.

2. After DEAD-349 lands real fixtures: **DEAD-354** (synthetic
   `data.zip` + canonical scenarios doc — pure desk work) then
   **DEAD-355** (CI smoke run) follow naturally.

3. **DEAD-356** (HTTPS / Caddy / `*.home.silberg.cloud` cert) and
   **DEAD-357** (beta polish) come last; they're not on the critical
   path.

4. **Push and PR** the branch at some point — 11 commits is plenty
   for a review. Probably after DEAD-349 + DEAD-354 land.

## DEAD-349 capture session — exact steps

You handle taps. Claude handles everything else.

### Before you tap (Claude does)

```bash
# 1. Hermetic container down (so mitmcap isn't competing with anything
#    on local ports, and the captured traffic goes to REAL upstreams)
make hermetic-down

# 2. Start mitmcap on port 8889 (port 8888 is used by the existing
#    network-fault-proxy). Captures to hermetic/fixtures/captures/<ts>.flow.
systemd-run --user --unit=mitmcap --setenv=PATH="$PATH" --setenv=HOME="$HOME" \
  -- /home/damian/.local/share/mise/installs/uv/0.11.14/uv-x86_64-unknown-linux-musl/uvx --from mitmproxy mitmdump \
  --listen-port 8889 \
  --allow-hosts '\.archive\.org|api\.github\.com|raw\.githubusercontent\.com|cdn\.jerrygarcia\.com|api\.genius\.com|.*\.wikipedia\.org|thedeadly\.app' \
  -w hermetic/fixtures/captures/$(date +%Y%m%d-%H%M%S).flow

# 3. Wait for you to launch the emulator yourself (next section).

# 4. Once `adb devices` shows the emulator:
adb install -r androidApp/app/build/outputs/apk/debug/app-debug.apk
adb shell pm clear com.grateful.deadly.debug
adb push ~/.mitmproxy/mitmproxy-ca-cert.cer /sdcard/Download/mitmproxy-ca-cert.cer
```

### What you do at the emulator

1. **Launch the emulator yourself, with a window.** The headless
   emulator under `systemd-run --user` from this session kept
   core-dumping under swangle rendering. Run it from a terminal where
   the window will appear:

   ```bash
   ~/Android/Sdk/emulator/emulator -avd Pixel_API_36 \
     -http-proxy http://localhost:8889 -gpu host
   ```

   `-gpu host` uses your real GPU (reliable). `-http-proxy
   http://localhost:8889` points it at the mitmcap. Don't pass
   `-no-snapshot-load` — let it fast-boot from snapshot.

2. **Install the mitmproxy cert as a user CA** (the debug build
   trusts user CAs because we set that up in commit `78976b7e`):

   - Settings → Security & privacy → More security & privacy →
     Encryption & credentials → Install a certificate → **CA
     certificate** → "Install anyway" → Downloads →
     `mitmproxy-ca-cert.cer`. Confirm.

3. **Launch the app and drive a scenario.** Something like:
   - Watch the home screen load (captures `data.zip` release check +
     show-list metadata)
   - Search for or pick a show
   - Open the show's detail page (captures recording metadata)
   - Tap play, let it play for ~10 seconds (captures audio Range
     requests against archive.org)
   - Browse home / library / recent (more metadata + image fetches)
   - Optionally favorite something

   Aim for ~2 minutes of representative interaction. The wider the
   scenarios, the more diverse the captured fixtures.

4. **Tell Claude when you're done.**

### After you tap (Claude does)

```bash
# 1. Stop mitmcap so the .flow file is finalized.
systemctl --user stop mitmcap.service

# 2. Convert the captured flows into WireMock mappings.
make capture-convert FLOW=hermetic/fixtures/captures/<ts>.flow

# 3. Sanity-check the output — what mappings + body files were
#    created? Anything sensitive (auth tokens) in headers? Anything
#    that needs hand-trimming?

# 4. Commit hermetic/fixtures/ contents.

# 5. Verify end-to-end: kill the emulator, restart hermetic container,
#    flip hermetic mode on in the app's prefs, relaunch — confirm
#    the captured show plays through entirely from WireMock.
```

## DEAD-354 — canonical scenarios + synthetic `data.zip`

Pure docs/data, no tapping. Two artifacts:

1. **`docs/playback-test-scenarios.md`** — a canonical list of
   scenarios both platforms should be able to drive against the
   hermetic server. Initial 8–12 scenarios, each with intent + exit
   criteria. (e.g. "new-show selection silences previous audio",
   "mid-track failure auto-retries", "cold launch restoration".)

2. **`hermetic/fixtures/synthetic/data.zip`** — a hand-crafted minimal
   `data.zip` for tests that don't want real archive.org content.
   Schema needs to match what the real `data.zip` produces. Both
   platforms' data layers must be able to load it.

Worth doing solo. The scenarios doc is the input DEAD-355's CI smoke
run consumes.

## Other open items, in priority order

- **DEAD-355** CI smoke: GitHub Actions `services:` block that boots
  WireMock with the captured fixtures + runs one happy-path scenario
  per platform. Failure blocks PR merge. Needs DEAD-349 fixtures
  committed first.

- **DEAD-356** HTTPS via Caddy: stand up `hermetic.home.silberg.cloud`
  with Caddy fronting WireMock using the existing GoDaddy wildcard
  cert. Lets us point release builds at hermetic without ATS
  exceptions. The cert itself is already verified (publicly-trusted
  CA, valid until Feb 2027).

- **DEAD-357** Beta polish: public hosting at `test.thedeadly.app`,
  in-app "hermetic mode active" indicator, tester runbook. Latest in
  the chain.

- **DEAD-352** Data isolation: most of its original motivation was
  resolved by commit `d9f4522f` (moving hermetic rewrite into the
  StreamPlayer engine boundary). Whether to close or rescope is a
  pending decision — see the comment on the ticket.

## What's living between sessions

- **Open emulator** (if any) is yours; the Claude session's
  `systemd-run` emulator dies when you stop it or the system reboots.
- **mitmcap** likewise — kept under `systemctl --user`; if it's still
  active from a prior session, just stop it and start fresh.
- **`hermetic-wiremock` container** is fine to leave up or down.
  `make hermetic-up` / `down` are idempotent.

## Quick-reference commands

```bash
# Bring up / take down the hermetic WireMock container
make hermetic-up
make hermetic-down

# Convert a captured .flow file
make capture-convert FLOW=hermetic/fixtures/captures/<file>.flow

# See what WireMock is serving / has received
curl http://localhost:8090/__admin/health
curl http://localhost:8090/__admin/mappings
curl "http://localhost:8090/__admin/requests?limit=20"

# Reset WireMock's request journal between scenarios
curl -X DELETE http://localhost:8090/__admin/requests

# Build the Android debug APK
cd androidApp && ./gradlew assembleDebug
# Or via build agent for iOS:
# (delegate to the build subagent — Mac is the only place iOS builds)
```
