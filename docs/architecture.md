# thedeadly.app Architecture

## Overview

The Deadly is a Grateful Dead live concert streaming/archiving app. The mobile apps (iOS/Android) are the primary platform, using a local-first architecture. thedeadly.app is the website and API that complements the mobile experience.

The website serves three purposes:
1. Rich statically-generated show pages for SEO and share links
2. Spotify Connect-style playback target (play audio on web, control from phone)
3. API backbone for auth, user data sync, and Connect across all platforms

## Monorepo Structure

```
api/          — Fastify API (auth, user data, Connect WebSocket)
ui/           — Next.js website (SSG show pages, player, Connect target)
web/          — Current static share site (untouched, GitHub Pages at share.thedeadly.app)
iosApp/       — iOS app (SwiftUI)
androidApp/   — Android app (Kotlin)
docs/         — Architecture and design documentation
```

## Technology Decisions

| Component | Choice | Rationale |
|-----------|--------|-----------|
| Frontend | Next.js (React, TypeScript), SSG | Static generation of ~30K show pages. Zero compute for page views. Great SEO. |
| API | Fastify, TypeScript | Fast, built-in schema validation, good TypeScript support, plugin architecture |
| Database | SQLite via better-sqlite3 (no ORM) | Two files: `users.db` (read/write user data) + `shows.db` (read-only dataset). Simple, fast, file-based. Easy backups. |
| Real-time | Redis | Pub/sub for Connect message routing between devices |
| Auth | Auth.js | Email magic links + Google/Apple OAuth. Self-hosted, free, well-maintained. JWT sessions. |
| Reverse proxy | Caddy | Auto-SSL via Let's Encrypt, serves static files, simple config |
| Orchestration | Docker Compose | Same config for local dev and production |
| Hosting | DigitalOcean (interim) → Oracle Cloud free tier ARM (long-term) | DO: $200 free credit/60 days. Oracle: free forever (4 OCPU, 24GB RAM). |

## Architecture Diagram

```
Build time (CI/CD — GitHub Actions):
  Dataset JSON → shows.db → Next.js SSG → ~30K static HTML pages → deploy

Runtime (Docker Compose):
  Caddy (reverse proxy, auto-SSL in prod)
  ├── beta.thedeadly.app/*     → static HTML files (show pages)
  ├── beta.thedeadly.app/api/* → Fastify API
  └── beta.thedeadly.app/ws/*  → WebSocket (Connect)

  Fastify API:
  ├── Auth (Auth.js, magic links + Google/Apple OAuth)
  ├── User data sync (favorites, reviews, settings, etc.) → users.db
  ├── WebSocket server (Connect device coordination)
  └── Dataset package download endpoint (for mobile apps)

  Redis: Connect session pub/sub

  Audio: streamed directly from Archive.org (never proxied through our server)
```

## Data Architecture

### Show Data (read-only)

The show dataset (~20-30K shows, recordings, setlists, venues) is:
- Curated and published as JSON in GitHub releases
- Imported into `shows.db` (SQLite) for Next.js SSG builds
- Downloaded by mobile apps for local-first offline use
- Never modified at runtime — rebuilt when the dataset updates

### User Data (`users.db`)

All user-generated data stored per-user and synced across devices:

| Table | Fields | Notes |
|-------|--------|-------|
| **accounts** | id, email, name, provider, createdAt | Auth.js managed |
| **favorite_shows** | showId, addedAt, isPinned, notes, preferredRecordingId, downloadedRecordingId, downloadedFormat, recordingQuality, playingQuality, customRating, lastAccessedAt, tags | Rich per-show user data |
| **favorite_songs** | showId, trackTitle, trackNumber, recordingId | Individual track favorites |
| **show_reviews** | showId, notes, overallRating, recordingQuality, playingQuality, reviewedRecordingId, createdAt, updatedAt | User's own reviews |
| **show_player_tags** | showId, playerName, instruments, isStandout, notes | Musician annotations |
| **recording_preferences** | showId, recordingId | Preferred recording per show |
| **recent_shows** | showId, lastPlayedTimestamp, firstPlayedTimestamp, totalPlayCount | Play history |
| **playback_position** | showId, recordingId, trackIndex, positionMs, updatedAt | Resume across devices |
| **settings** | includeShowsWithoutRecordings, favoritesDisplayMode, forceOnline, sourceBadgeStyle, shareAttachImage, eqEnabled, eqPreset, eqBandLevels | App preferences |

**Not synced:** download queue/progress (ephemeral), collections (read-only from dataset).

### Sync Protocol

The API sync format is based on the existing **V3 JSON backup schema** that mobile apps already use for local backup/restore. This means:
- Mobile apps already know how to serialize/deserialize this data
- The backup/restore feature continues to work locally independent of the API
- Sync is essentially "push your V3 export to the server" and "pull the latest from the server"

Key implementation files:
- iOS: `iosApp/deadly/Core/Service/FavoritesImportExportService.swift`
- Android: `androidApp/core/database/src/main/java/com/grateful/deadly/core/database/service/BackupImportExportService.kt`

## Connect (Spotify Connect-style)

### Concept

Users can transfer audio playback between devices and control it remotely:
1. Playing a show on phone → transfer to web browser → phone becomes remote control
2. Open app on any device → resume where you left off on the last device

### How It Works

- Each signed-in device maintains a WebSocket connection to the API
- Devices register themselves (type, name, capabilities)
- The API acts as a message broker between devices via Redis pub/sub
- Playback commands (play, pause, skip, seek) are small JSON messages
- Playback position is periodically persisted to the API for passive resume

### Playback Position

Serves two purposes:
1. **Passive resume** — API stores the latest position per user. Open the app on any device, continue where you left off.
2. **Active Connect** — Real-time sync via WebSocket. API position is the fallback if WebSocket disconnects.

Position is written to the API:
- Every 10-30 seconds during playback
- On pause, stop, or track change
- On app backgrounding/closing

## Authentication

- **Auth.js** (formerly NextAuth) handles all auth flows
- **Providers:** Email magic links (passwordless), Google OAuth, Apple OAuth
- **Sessions:** JWT-based
- **Optional accounts:** The app works without signing in. Accounts unlock cross-device sync and Connect.

## URL Structure

- Show pages: `/shows/{id}` — using database IDs, not dates (dates aren't unique)
- API: `/api/*` — all API endpoints
- WebSocket: `/ws/*` — Connect WebSocket connections

## Domain Strategy

| Domain | Purpose | Status |
|--------|---------|--------|
| `beta.thedeadly.app` | Development/staging | New — first deployment target |
| `share.thedeadly.app` | Current static share site | Preserved on GitHub Pages |
| `thedeadly.app` | Production (future) | Will replace share.thedeadly.app when ready |

### Deep Links

- `beta.thedeadly.app` gets its own `.well-known/apple-app-site-association` and `assetlinks.json`
- Debug/dev mobile builds register for beta domain deep links
- Production builds continue using `share.thedeadly.app` until migration

## Backup & Recovery

### What's Critical

Only `users.db` needs backup. `shows.db` is read-only and rebuilt from the dataset JSON source.

### Strategy

- **Local snapshots:** Cron job runs `sqlite3 users.db ".backup ..."` every few hours. Keep 7 days on disk.
- **Off-site:** Upload snapshots to Backblaze B2 (free 10GB tier, S3-compatible API)
- **Retention:** Hourly for 24h, daily for 30 days, weekly for 6 months
- **User-side redundancy:** Mobile apps keep local SQLite + JSON backup export capability

### Recovery

- Corrupted DB: Restore latest local snapshot (minutes)
- Lost server: Spin up new VPS, pull backup from B2, `docker compose up` (~30 min)
- Accidental deletion: Restore from point-in-time snapshot

## Hosting & Infrastructure

### Current Plan

1. **DigitalOcean** (interim): 4GB RAM / 2 vCPU droplet, ~$24/mo covered by $200 free credit for 60 days
2. **Oracle Cloud** (long-term): Free tier ARM instance — 4 OCPU, 24GB RAM, 200GB storage, forever free

### Migration Path

Everything runs in Docker Compose. Migration = spin up new server, `docker compose up`, copy SQLite file, update DNS. Zero code changes.

### Why This Scale Is Enough

- We never serve audio — Archive.org handles all streaming
- Show data is static — served as pre-built HTML files by Caddy (zero compute)
- API handles only user data (tiny payloads) and Connect WebSocket messages (~100 bytes each)
- SQLite handles concurrent reads trivially; write volume (user favorites, position updates) is low
- A $5 VPS could handle ~5K concurrent users; the Oracle free instance handles far more

## CI/CD

GitHub Actions pipeline:
1. Build `shows.db` from dataset JSON
2. Run Next.js SSG to generate static pages
3. Build API Docker image
4. Deploy to server via SSH
5. `docker compose up` on the server

## Future Considerations

- **Full web browsing:** Search, collections, today-in-history, browse by venue/song/era
- **Incremental dataset updates:** Diff/patch mechanism instead of full re-download
- **Additional Connect targets:** Alexa, TV apps
- **Community features:** Shared playlists, discussions
- **Offline web:** Service Worker + IndexedDB for PWA-like experience
- **Database migration:** If concurrent writes become a bottleneck (multiple API processes), migrate from SQLite to PostgreSQL
