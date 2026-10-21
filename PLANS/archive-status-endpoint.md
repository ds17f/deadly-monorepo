# Archive.org Status Endpoint Plan

Add an API endpoint that tells the clients if archive.org is up. The clients
use the result to give a more accurate message when the tracks do not load.

## Status (2026-10-21)

- Proposed, not started.
- This plan is an optional follow-up. The client fix on branch
  `fix-archive-unreachable` is complete without it.

## Context

A field bug report (Android 2.42.0, Samsung tablet) showed TCP connect
timeouts to archive.org. The device was online. The app showed an empty track
list.

The branch `fix-archive-unreachable` fixed the clients:

- iOS and Android show "Couldn't load tracks" and "Archive.org isn't
  responding. It may be down right now."
- The Retry button has a debounce. It ignores taps while a request is in
  progress. It has a 3 s cooldown after each failure.
- The error block stays in place during a retry, so the list does not jump.
- A "Check Archive.org status" link opens
  <https://www.isitdownrightnow.com/archive.org.html>.
- iOS does not show a 5xx response or an HTML error page as an empty track
  list now. iOS also does not cache an empty track list (24 h) now.

The client cannot tell the user if archive.org is down or if only the user's
network has a problem. This plan adds that data.

## Proposal

### 1. API endpoint

Add `GET /api/archive-status`. Put the route in a new file,
`api/src/routes/archiveStatus.ts`. Register it in `api/src/app.ts`, the same
as `trendingRoutes`.

- The server probes archive.org only on demand. It does not use a cron job.
- The probe is a HEAD or GET request to an archive.org metadata endpoint, with
  a 5 s timeout.
- The server keeps the result in memory for about 60 s. Thus a large outage
  does not cause one probe for each client request.
- Example response:
  `{ "status": "up" | "down" | "degraded", "checkedAt": "...", "latencyMs": 123 }`.

Why on demand, and not an interval job (for example, each 10 minutes):

- The result is more recent. The client can tell the user "it is back, try
  again".
- The server sends no requests to archive.org when no user has a problem.
- The API already has `setInterval` jobs (see `startTrendingSchedules` in
  `api/src/routes/trending.ts`). But a result that is up to 10 minutes old is
  too old for this use.

### 2. iOS and Android messages

The clients call the endpoint only from the track-load error state. The call
has a short timeout, so it does not delay the error UI.

| Server result     | Message                                                          | Status link |
| ----------------- | ---------------------------------------------------------------- | ----------- |
| `up`              | "Archive.org is up, but your network can't reach it."            | Optional    |
| `down`            | "Archive.org is down right now."                                 | Remove      |
| Call fails        | Keep the generic message.                                        | Keep        |

### 3. Web banner (optional)

The web app can show a banner when the status is `down` or `degraded`.

## Limits

- The network path from our server is not the same as the path from the
  user. Thus `up` is not a promise that the user can connect.
- If the client cannot connect to our server also, it uses the generic
  message and the link.
- Do not add new analytics events unless you register them in `EVENT_SCHEMAS`
  in `api/src/db/analytics.ts`. The server drops events that are not in the
  allowlist.

## Order

1. API endpoint.
2. iOS and Android messages.
3. Web banner (optional).

The repo uses squash merges. Thus the PR title must be a conventional commit,
for example `feat(all/archive-status): ...`.

## Open question

Is this work necessary? Outages are rare, and the client fix closes the main
gap. Make a decision after you see how frequently the error occurs.
