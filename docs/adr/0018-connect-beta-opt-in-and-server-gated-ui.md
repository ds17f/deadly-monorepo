# ADR-0018: Connect — user-facing beta opt-in + server-gated UI discovery

## Status

Proposed (2026-06-19). Builds directly on the global Connect kill switch shipped
in PR #86 (`app_settings.connect_enabled`, default OFF) and the Phase-3 client
changes in `e64b1f7c` (per-device toggle default OFF, UI hidden, toggle moved to
Developer, `protocolVersion` 2 understands 4005). This ADR **reverses the "hide
the UI / Developer-only toggle" half** of `e64b1f7c` while keeping everything
else: Connect stays default-OFF and server-gated, but becomes a discoverable,
user-facing **beta opt-in** instead of a hidden developer setting.

Relates to [ADR-0006](0006-connect-v2.md) (server is transport source of truth),
[ADR-0011](0011-connect-session-ownership-and-protocol-versioning.md) (protocol
versioning), and [ADR-0016](0016-connect-liveness-detection-and-connect-handshake.md)
(close-code handling). Does **not** un-defer
[ADR-0008](0008-connect-cloud-coordination-deferred.md): no new server state
beyond the existing `connect_enabled` flag.

## Context

We disabled Connect by default because of cross-device skipping/playback bugs.
`e64b1f7c` took the conservative route: hide every Connect icon and bury the
per-device toggle in the Developer screen. That stops the bug, but it also makes
Connect undiscoverable — nobody can opt into the beta, and we lose the signal of
willing testers.

We want a middle path: **keep it off by default and keep the server kill switch
as master, but let users discover and opt into the beta themselves**, with
honest "this is beta" messaging and a clear path to disable.

### The three states we need to express

| State | Decided by | Icons | Tap behavior |
|---|---|---|---|
| **Server OFF** | global `connect_enabled` (false) | shown but **greyed/disabled** | "Connect is currently unavailable" note |
| **Server ON, device OFF** (default) | per-device/per-install toggle (false) | shown, enabled (promo) | enable sheet: "Enable Connect (Beta)" + info + confirm + "turn it on on your other devices too" |
| **Server ON, device ON** | per-device/per-install toggle (true) | shown, full UI | device-picker UI + a "Turn off Connect" row → "Your other devices stay connected…" |

### The gap `e64b1f7c` left: 4005 cannot drive icon discovery

The 4005 "Connect disabled" close code only arrives **in response to a `register`
attempt**, and a client only attempts to register when its *local* toggle is
already ON. In the default state (device OFF) the client never connects, never
receives 4005, and therefore has **no way to know the server's flag state** — so
it cannot decide whether to show the icon as enabled (server ON) or greyed
(server OFF).

**4005 is a runtime enforcement signal, not a discovery mechanism.** It is exactly
right for "admin flips the switch off while I am actively connected" → kill the
session and flip the UI. It cannot answer "what should a *disconnected* device
render."

## Decision

### 1. A public read endpoint for the server flag (the missing discovery gate)

Add an unauthenticated read of the existing flag:

```
GET /api/connect/enabled  →  { "connectEnabled": boolean }
```

The admin GET `/api/admin/connect/settings` already returns this shape behind
`requireAdmin`; this is the same value with no auth requirement (the flag is not
secret — it only controls whether a feature is offered). Clients cache the boolean
as `serverConnectEnabled`.

**When clients fetch it:**

- **At startup**, so the Connect UI renders in the correct state without a flash.
  Use a **short timeout** (~2–3s) so a slow/unreachable API never blocks app
  launch.
- **Fallback to a foreground fetch** if the startup read timed out or failed —
  and as the ongoing refresh on app foreground.
- **Periodically, on the same focus/navigation-change trigger** the app already
  uses for userdata refresh (focus-refresh, not real-time polling), so an admin
  flipping the flag mid-session is reflected without a relaunch.

Until the first successful read resolves, fall back to the **last cached value**,
defaulting to `false` (greyed) on a truly fresh install — fail safe toward "not
offered" rather than showing an enable affordance that would immediately fail.

This boolean is the **primary icon-visibility/enabled gate**. 4005 remains the
**runtime enforcement path**: an active client that receives 4005 kills its
session and writes `serverConnectEnabled = false` (same local state the REST call
feeds), flipping the UI to the greyed state without needing a refetch.

Both signals write the same local state; they agree. REST exists only so a
*disconnected* device can learn it.

### 2. Per-device / per-install opt-in becomes user-facing (reverses `e64b1f7c`)

- Default stays **OFF**. The bug stays fixed for everyone who does nothing.
- The toggle moves out of Developer and into **two** user-facing homes:
  - the **Connect-icon bottom sheet** (primary, with the beta confirmation), and
  - **Settings → Playback & Audio** (mirror, for later toggling).
- First enable shows the **"Enable Connect (Beta)"** confirmation: what it does,
  that it is beta and may misbehave, and that the user must enable it on their
  **other devices too**. Subsequent toggles can be quiet.
- Disabling from the full UI shows: "Your other devices are still connected — turn
  Connect off there too if you want to fully stop syncing."

### 3. Server-OFF renders greyed, not hidden

When `serverConnectEnabled` is false the icon is shown **disabled/greyed**; tapping
surfaces a short "Connect is currently unavailable" message rather than the enable
sheet. Greyed-not-gone tells users the feature exists and is coming back, and
avoids layout shift when the flag flips.

### 4. Web gets the same treatment, as a per-install local setting

`e64b1f7c` made web rely solely on the server flag with no per-install opt-in.
This ADR gives web parity:

- Web reads `GET /api/connect/enabled` (same gate as mobile).
- The opt-in is a **per-web-install** setting stored locally (`localStorage`),
  because a user may have several browsers/installs and each is a distinct
  "device" in a Connect session. It is **not** a server/account setting.
- Surface it in a **Settings → Connect (Beta)** section in `/me` settings, plus the
  same enable affordance from the web Connect icon. Greyed when server is OFF.

## Consequences

- **Reverses** the hide-UI / Developer-only-toggle decision from `e64b1f7c`;
  keeps default-OFF, server-gating, and protocol-2 4005 handling.
- One new **public** endpoint; no new persisted server state, no un-deferral of
  ADR-0008.
- Web's "device" identity is now explicitly per-install (localStorage), which is
  the correct mental model for multiple browsers but means clearing site data
  resets the opt-in.
- Discovery is decoupled from connection: a device can show the correct icon
  state without ever opening a socket, which also avoids needless WS attempts
  from the default (OFF) state.
- Android's reconnect-churn fix from `e64b1f7c` (the `onClosing` override) is
  unaffected and still required for the 4005 runtime path.

## Open questions

- Exact copy for the beta confirmation and the "unavailable" note.
