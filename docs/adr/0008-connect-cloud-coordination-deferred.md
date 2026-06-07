# ADR-0008: Connect — cloud-coordinated ("Spotify-style") control deferred

## Status

Accepted (2026-06-07). Extends [ADR-0006](0006-connect-v2.md) and
[ADR-0007](0007-connect-background-socket-lifecycle.md). Records a decision to
**not** build cloud-coordinated Connect now, and what would gate doing so.

## Context

ADR-0007 accepts a limit: a device that suspends (iOS, once audio stops) leaves
the session and can't show or be handed live state until it's foregrounded.
"Why can Spotify do this and we can't?" is the natural question. Spotify Connect
doesn't keep a socket open from a sleeping phone — it decomposes into:

1. **Durable, server-side session state** (their "connect-state" service via a
   pub/sub "dealer"): which devices, who's active, context/track/position — held
   in the cloud, so it survives any device dropping or suspending.
2. **Silent push (APNs/FCM)** to wake a suspended device to stop/hand off, so
   coordination never depends on that device's live connection.
3. **Background audio** for the device actually making sound — identical to us;
   the difference is purely in *coordination*, which they route through the cloud.

Ours is the opposite by deliberate choice: ADR-0006 made session state in-memory
and "recoverable from the still-connected active device," and ADR-0007 tied the
socket to playback/process. So a suspended device genuinely has nothing to fall
back on — hence the accepted limit.

## Decision

**Do not build cloud-coordinated Connect to close the suspended-device gap.**
Specifically:

- **Defer silent push (APNs/FCM) indefinitely.** It's a new cross-platform
  subsystem (tokens, certs, send paths, lifecycle) and iOS `content-available`
  push is best-effort — throttled/coalesced/dropped in Low Power Mode — so even
  fully built it would lag (e.g. double-audio on transfer to/from a slow-to-wake
  device). Revisit only if instant control of a *sleeping* device becomes a
  named, concrete requirement, and in the knowledge that iOS won't fully cooperate.
- **Gate durable server-side session state on the social/presence feature.** It's
  the contained, server-mostly half (the playback-position persistence in
  `api/src/db/userdata` already exists to build on) and it's the foundation the
  social "see/hear what a friend is playing" feature needs anyway — `userStates`
  already holds per-user live state (ADR-0006), so the social *read* path is a
  future REST endpoint + friends graph with **no wire change and no push**. Build
  durable state as part of social, where it pays for itself; don't build it just
  for the background-takeover cosmetic.

## Consequences

- The ADR-0007 suspended-device limit stands until social is built. The
  background-takeover case (take over on web while the phone is asleep → phone
  shows stale until foregrounded) remains accepted, not papered over.
- When social lands, durable state comes with it; the "instant handoff to a
  sleeping device" control path (needing push) remains separately deferrable.
- We keep the backend small now and avoid a weeks-long push subsystem with a
  hard iOS reliability ceiling for modest UX gain.

## Alternatives considered

- **Build all three pieces now (durable state + push + grace).** Rejected:
  weeks of work dominated by push infra, best-effort on iOS, for an uncommon flow
  already covered "well enough" by the keepalive + track-sync fixes.
- **A heavier client / background service with its own notification player.**
  Rejected in ADR-0007: doesn't rescue iOS (no indefinite background without
  active audio; silent-audio keepalive is an App Store / battery risk).
- **Durable state now, push later.** Viable, and the likely path — but only worth
  starting when social is actually on the roadmap, so it's gated on that rather
  than done speculatively.
