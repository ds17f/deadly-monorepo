# network-fault-proxy

A tiny mitmproxy addon for deliberately failing archive.org audio requests
from a tethered iPhone, so the iOS playback retry / auto-advance-suppression
paths can be exercised against a *real* stream error (not a synthetic one).

## One-time setup

1. Install `uv` (provides `uvx`). Already done via `mise use -g uv@latest`.
2. Open Fedora firewall port 8080 if needed:
   ```bash
   sudo firewall-cmd --add-port=8080/tcp        # this session only
   ```
3. Find this machine's LAN IP:
   ```bash
   ip -4 addr show | grep "inet "
   ```
4. iPhone → Settings → Wi-Fi → (i) on your network → Configure Proxy → Manual:
   - Server: that LAN IP
   - Port: 8080
5. Start the proxy (see below), then on the iPhone open Safari to
   `http://mitm.it` → "Apple iOS" → download profile → Settings → General →
   VPN & Device Management → install → Settings → General → About →
   Certificate Trust Settings → enable trust for **mitmproxy**.

## Run

From the repo root:

```bash
uvx --from mitmproxy mitmdump \
  -s tools/network-fault-proxy/kill_archive.py \
  --listen-port 8888 \
  --allow-hosts '\.archive\.org'
```

`--allow-hosts` restricts TLS interception to archive.org. Everything else
(auth, analytics, GoogleSignIn, Apple services) passes through transparently
with the real cert chain, so cert-pinned SDKs don't trip the "untrusted
network" warning on every fresh install.

`mitmproxy` is the TUI. Use `mitmweb` for the browser UI or `mitmdump` for
headless logging.

## Drive a test

In a second terminal:

```bash
# Kill the next archive.org audio request that hits the proxy
touch /tmp/kill_archive

# Stop dropping
rm /tmp/kill_archive
```

The flag is checked per-response, so flip it during playback to simulate a
single mid-stream failure.

## What to look for in iOS logs

With the auto-advance fix on (current state of `main`-bound code):

```
unexpectedError type=networkError(...)
retry attempt=1/3 in 1.0s url=...
[PB] suppressing auto-advance during retry: prev=N attempted=N+1 ...   # if AudioStreaming tries to pop
retry firing url=<same URL> (will re-queue M pending)
... eventual recovery ...
```

You should NOT see `wasAutoAdvance=true` accepted with a real `onTrackComplete`
fire during the retry window. If you do, the fix isn't holding.

## Why this exists

The in-app "Inject Network Error" debug button calls the engine's error
handler directly, which is enough to verify the retry plumbing but does NOT
trigger AudioStreaming's internal gapless-pre-queue behavior (the original
DEAD-335 jump-to-next bug). Only a real stream error from the underlying
library exercises that path — hence this proxy.
