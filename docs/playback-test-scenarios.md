# Playback test scenarios

> Canonical list of behaviors both iOS and Android implement against
> the hermetic test framework. The list is platform-neutral by design
> — it describes the *intent* and *observable outcome* of each
> scenario; the test code lives in each platform's test target.
>
> When a new bug class is found, add a scenario here and both
> platforms add a test. When the list grows past ~25 scenarios,
> reorganize by area rather than by ID.
>
> Referenced by **DEAD-354** (this doc) and **DEAD-355** (CI smoke).
> The hermetic server (WireMock) serves fixtures from
> `hermetic/fixtures/` — captured real traffic for content-heavy
> scenarios, the synthetic `data.zip` (6 canonical shows) for
> catalog-driven scenarios that want fast deterministic loads.

## Scenario format

```text
### N. <short name>

Why this exists: <bug class or product requirement>
Fixtures needed: <which files in hermetic/fixtures/>
Test driver does: <action via service layer, not UI>
Asserts: <observable result + any trace-log sequence>
```

Each scenario should be implementable through the service layer
(`PlaylistService`, `ShowRepository`, etc.) — not through UI taps. UI
tests are a separate concern.

---

## Cold launch & catalog

### 1. Cold launch with cached data.zip loads catalog

Why this exists: foundational — without this, nothing else works.
Fixtures needed: `synthetic/data.zip` (the 6-show test catalog).
Test driver does: install the app fresh, point at hermetic, wait for
data import to complete.
Asserts: catalog reports ≥ 6 shows (the synthetic count); cold start
to "catalog loaded" under 5s.

### 2. Cold launch triggers data.zip upgrade on version bump

Why this exists: covers the GitHub releases path; if the upgrade
flow breaks, users get stuck on whatever they installed with.
Fixtures needed: WireMock serves a `releases/tags/data-v<N+1>.0.0`
mapping returning a manifest pointing at a newer data.zip.
Test driver does: install with version N, restart app, wait for
upgrade.
Asserts: app downloads the new data.zip; catalog version
in `data_version` table updates to N+1.

---

## Playback — happy path

### 3. Play first track → audio starts within 5s

Why this exists: the most basic claim the app makes.
Fixtures needed: archive.org metadata + a playable MP3 for one
recording (the captured fixtures from DEAD-349).
Test driver does: `playlistService.loadShow(<showId>, autoPlay: true)`.
Asserts: `phase` transitions `idle → preparing → playing` within
5000ms; engine reports non-zero playback position after another 2s.

### 4. Track auto-advances at end

Why this exists: gapless transition between tracks is core UX.
Fixtures needed: two playable MP3s.
Test driver does: load a recording with ≥2 tracks; seek to 1s
before the end of track 1; let it play.
Asserts: queue `currentIndex` advances 0 → 1; no `phase = idle`
transition between them; audio output continuous.

### 5. Pause then resume

Why this exists: smoke test for the playback control surface.
Test driver does: load + play; after 2s of playback, pause; after
1s of pause, resume.
Asserts: `phase` goes `playing → paused → playing`; playback
position when resumed is within 100ms of when paused.

---

## Playback — error paths (motivating bugs)

### 6. New-show selection silences previous audio

Why this exists: **DEAD-344** — original symptom was "select new
show while previous is buffering → wrong audio plays from previous
queue for up to 61s."
Fixtures needed: two recordings; the test fixture for the SECOND
recording's first track has an artificial delay (WireMock stub
fault: `--delay 10s`).
Test driver does: `loadShow(A, autoPlay: true)`; wait for `playing`;
immediately call `loadShow(B, autoPlay: true)` (B's resolve is
slow).
Asserts: between submission of B and B's first audio frame, NO
audio is observably playing from A; UI/state never reports A as
the current track once B is submitted.

### 7. Mid-track failure auto-retries and resumes

Why this exists: **DEAD-335** — the retry pipeline + auto-advance
suppression. The bug was that AudioStreaming's gapless pre-queue
fired on a retry error and silently advanced to the next track.
Fixtures needed: a recording with ≥2 tracks; WireMock fault:
return 503 for the first track once, then 200 with audio bytes.
Test driver does: play a recording; wait for the first 503; let
the retry happen.
Asserts: trace sequence includes `retry.start → retry.success`;
the queue's `currentIndex` does NOT advance to 1 during the retry
window; final audio output is from track 0, not track 1.

### 8. Resolve timeout >30s fails cleanly

Why this exists: **DEAD-344** spinoff — eager `resolveAllRedirects`
used to block 61+ seconds on a single slow HEAD. New behavior:
per-URL 5s timeout; lazy resolution; deadline-driven failure on
the whole queue after a configured ceiling.
Fixtures needed: WireMock stub fault: indefinitely-hung response
for a redirect URL.
Test driver does: load a recording whose first track triggers the
hung redirect; wait for the deadline.
Asserts: `phase` reaches `failed` with a `resolve-timeout` reason
within 35s of `loadShow` submission; UI surfaces a retry-able
error state, not a hang.

---

## Restoration

### 9. Cold launch with saved playback state restores

Why this exists: **DEAD-336** — Android Auto autoplay/resume work
hinges on this. Persistence-layer regressions break "resume where
I left off."
Fixtures needed: a recording with multi-track audio.
Test driver does: play a recording, advance to track 2, scrub to
1:30, force-kill the app; cold-launch with hermetic on.
Asserts: on launch, `currentTrack` is track 2 of the same
recording, `position` is within 1s of 1:30. NO audio plays during
restoration. Tap play → audio resumes from 1:30 within 500ms.

---

## Navigation & data round-trip

### 10. Open show detail → recording metadata loads

Why this exists: smoke test for the show-detail flow that exercises
archive.org metadata fetches.
Fixtures needed: archive metadata for one recording.
Test driver does: `showRepository.getShowById(<id>)`; then
`archiveClient.fetchTracks(recordingId)`.
Asserts: returned recording has ≥1 track; track 1 has a non-empty
title; the archive metadata HTTP request showed up in WireMock's
journal at `/archive.org/metadata/...`.

### 11. Favorite a show → appears in library

Why this exists: data round-trip through the local DB; favorites
were ID-keyed and never mode-dependent, but a regression here
would be silent and bad.
Test driver does: favorite show X; query the library favorites.
Asserts: X is in the library favorites list immediately, and
again after a process restart.

### 12. Search returns expected results for a known query

Why this exists: smoke test for FTS / search index integrity. If
the data.zip didn't import correctly, search returns nothing.
Fixtures needed: synthetic `data.zip` (the 6-show catalog gives us
known matchable strings like "Cornell" or "Fillmore West").
Test driver does: `searchService.search("Cornell")`.
Asserts: at least one result; the 1977-05-08 show is in the
results.

---

## Adding scenarios

When you add a scenario:

1. Append it under the most appropriate section (or add a section
   if needed).
2. Use the format above — emphasis on *why* (so the scenario doesn't
   look like noise to future readers) and *asserts* that are
   observable, not "the user sees the right thing."
3. Implement it in both `androidApp/.../test/` and
   `iosApp/deadlyTests/` (or `deadlyUITests/` if it genuinely
   requires UI).
4. If the scenario needs new fixtures (mappings, audio, etc.),
   either capture them via `make capture-start` + commit, or
   hand-author a `synthetic/...` mapping.
5. If the scenario is for a bug we just fixed, reference the
   Linear ticket in "Why this exists."
