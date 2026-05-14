# DEAD-335 follow-ups

Notes left after the playback-fix branch (PR #40) was opened. The main work
is shipped; these are deferred items + context so a future session doesn't
have to reload the entire conversation.

## State at hand-off

- Branch: `dead-335-ios-playback-observability`
- PR: https://github.com/ds17f/deadly-monorepo/pull/40
- Linear: DEAD-335 has a status comment with the same summary as the PR.
- All three commits land cleanly. Working tree clean.
- Mitmproxy fault injector lives at `tools/network-fault-proxy/` with a
  README. iPhone needs Manual proxy → LAN-IP:8888 and the mitm cert
  trusted to use it. **iOS dev-app verification fails with the proxy on**
  — workaround: disable iPhone proxy → install + verify → re-enable.

## Verified end-to-end against real network failures

(See PR description for the full list — all the bits below are real
field-verified behavior, not just compile-clean.)

- Stale-gen race → catches, queue stays correct.
- Range-request failure mid-track → retries silently, auto-advance
  suppressed, resumes at the right spot, original volume restored.
- Multiple sequential failure cascades → volume + position survive.
- Cold launch with the exact saved recording.
- Manual user retry after surfaced error → position + volume restored.
- Silent buffering stall → 15s watchdog escapes and runs retry path.
- User seek backward during failure → resumes at the seek target.

## Open follow-ups (in rough priority order)

### 1. Verbatim repro of symptom #3 ("song ends, next fails to start")

The Reddit report described tracks that ended naturally and then the next
one wouldn't start. We *think* this is covered by the same retry hardening
that fixed the auto-advance bug (#2), but we never produced a verbatim
repro.

To try: pick a long track, scrub to ~10s from the end with `touch
/tmp/kill_archive` armed. Let it transition naturally — at the EOF,
AudioStreaming will try to load the next track. With the kill in place
the range request fails. Expected behavior with current code: retry path
fires for the next track, eventually either recovers or surfaces the
"Can't reach Archive.org" banner. NOT expected: skipping ahead, getting
stuck silently.

If a separate bug exists in the EOF → next-track path, that's a new
investigation.

### 2. Background-during-retry smoke test

What happens if you lock the phone while retries are running? Theoretical
analysis is clean — the progress-update freeze during `isRetrying`
prevents the periodic `LastPlayedTrackStore` save from capturing a 0
position, so resume should still work after a kill-and-relaunch. But not
explicitly tested.

### 3. Demote the stale-gen guard log

`processResolveCompletion` logs at warning level when it drops a stale
completion. That's the right level now while we're observing whether the
race fires in the wild, but if we never see it in production logs over
the next few releases, drop to `.notice` to reduce noise. (Or leave it —
warning is fine and rare.)

### 4. Spurious `auto-advance suppressed` logs after error

When the engine is in the surfaced-error state and AudioStreaming
continues internally thrashing through tracks, the auto-advance
suppression guard fires repeatedly, each one logging
`[PB] suppressing auto-advance (final error surfaced): prev=N attempted=M`.
Functionally correct but noisy. We could deduplicate or only log the
first one per error cycle.

### 5. Inject-network-error button reset-to-0 quirk

The developer-menu "Inject Network Error" button does fire the retry path
correctly, but because it goes through the retry mechanism which calls
`player.play(url:)` (which restarts the URL from 0), and the failure is
synthetic so AudioStreaming never actually errors, the retry **succeeds**
and audio resumes from 0:00 — not where the synthetic error fired.

That's a quirk of the synthetic path: the engine doesn't have a position
to resume from because the audio was actually fine. Fine for the UI test
purpose, but worth noting in the button's docstring so future-us doesn't
get confused.

### 6. PAC-file experiment for the iOS verification problem (PARKED)

We tried serving a proxy.pac file so iOS would route only `*.archive.org`
through the proxy and everything else DIRECT (which would let Apple's
app verification go direct and never fail). It did not work — verification
still failed. Files reverted. The "Unable to verify app" workaround
remains: disable iPhone proxy → install + verify → re-enable. Annoying
but rare (only on fresh installs).

If someone wants to retry, the proxy.pac approach was on the right
conceptual track; possibly the issue is that the PAC server needs to be
HTTPS, or that iOS caches a previous "Manual" config decision. Out of
scope for DEAD-335.

### 7. Makefile keychain `-p` puts password in `ps` briefly

Tradeoff was explicitly accepted (personal dev laptop, <100ms window).
If someone wants to harden: use a temp file with restrictive permissions
and feed `security` a filename via an extension or shell pipe trick.
Probably not worth the complexity.

## Key architectural decisions worth remembering

- **The synthetic `.playing` from `didStartPlaying` was removed.** Real
  `.playing` arrives only via `audioPlayerStateChanged` after AudioStreaming
  actually has buffered audio flowing. The seek dance depends on this; if
  someone re-adds the synthetic signal, the dance "settles" too early and
  audio jumps to 0 on every restore.

- **`onError` callback now takes a `TimeInterval?` second parameter** with
  the resume position. Reading `progress.currentTime` from `StreamPlayer`
  at error time is unreliable because `player.stop()` may have wiped it.
  The engine snapshots position before stopping and passes it through.

- **The retry mechanism in the engine calls `player.stop()` synchronously
  before scheduling.** This is load-bearing — without it, AudioStreaming's
  internal queue auto-advances to the next track between the error and
  our retry firing, which silently moves `queue.currentIndex` forward.
  The `hasSurfacedFinalError` gate + auto-advance suppression in
  `didStartPlaying` are belt-and-suspenders for the case where stop is
  async or insufficient.

- **`hasStartedAnyTrack` is reset on final error** so the next
  `startCurrent` does a fresh `player.play(url:)` instead of `player.resume()`
  — resume is a no-op on a stopped player.

- **`isRetrying` gates progress updates** specifically so the periodic
  state save doesn't capture a 0 mid-retry. If anyone "fixes" this by
  un-gating, the slider will flash to 0 between retries AND the saved
  restore position will get corrupted.
