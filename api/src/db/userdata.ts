import type Database from "better-sqlite3";
import { getUsersDb } from "./users.js";

// ── V3 Backup Types ─────────────────────────────────────────────────

export interface BackupV3 {
  version: 3;
  exportedAt: number;
  app: string;
  favorites: {
    shows: FavoriteShowV3[];
    tracks: FavoriteTrackV3[];
  };
  reviews: ReviewV3[];
  recordingPreferences: RecordingPrefV3[];
  settings: SettingsV3 | null;
  recentShows?: RecentShowV3[];
  playbackPosition?: PlaybackPositionV3 | null;
  notificationState?: NotificationStateV3[];
  backlog?: BacklogItemV3[];
}

// Every record carries `updatedAt` (the LWW comparator). Records that
// can be individually deleted also carry `deletedAt` — populated only
// on tombstones; absent/null on live rows. Singletons (settings,
// playback position) don't tombstone — overwrite is the delete.
//
// Track IDs travel with the user (favorite_songs has an autoincrement
// id) so clients can match local rows to server tombstones; otherwise
// matching would happen by (showId, trackTitle, recordingId).

export interface FavoriteShowV3 {
  showId: string;
  addedAt: number;
  isPinned: boolean;
  lastAccessedAt?: number | null;
  tags?: string[] | null;
  notes?: string | null;
  preferredRecordingId?: string | null;
  downloadedRecordingId?: string | null;
  downloadedFormat?: string | null;
  recordingQuality?: number | null;
  playingQuality?: number | null;
  customRating?: number | null;
  updatedAt: number;
  deletedAt?: number | null;
}

// The Show Queue (backlog) — an ordered list of show ids the user wants to
// play. Synced per-action (add/pop/move/remove) like favorites, NOT as a
// whole-list snapshot (the queue auto-pops during playback). `position`
// orders the live rows; `deletedAt` tombstones a popped/removed show so the
// removal propagates instead of resurrecting from another device.
export interface BacklogItemV3 {
  showId: string;
  position: number;
  addedAt: number;
  updatedAt: number;
  deletedAt?: number | null;
}

export interface FavoriteTrackV3 {
  id?: number;
  showId: string;
  trackTitle: string;
  trackNumber?: number | null;
  recordingId?: string | null;
  updatedAt: number;
  deletedAt?: number | null;
}

export interface ReviewV3 {
  showId: string;
  notes?: string | null;
  overallRating?: number | null;
  recordingQuality?: number | null;
  playingQuality?: number | null;
  reviewedRecordingId?: string | null;
  playerTags?: PlayerTagV3[] | null;
  updatedAt: number;
  deletedAt?: number | null;
}

export interface PlayerTagV3 {
  playerName: string;
  instruments?: string | null;
  isStandout: boolean;
  notes?: string | null;
}

export interface RecordingPrefV3 {
  showId: string;
  recordingId: string;
  updatedAt: number;
  deletedAt?: number | null;
}

export interface SettingsV3 {
  includeShowsWithoutRecordings?: boolean | null;
  favoritesDisplayMode?: string | null;
  forceOnline?: boolean | null;
  sourceBadgeStyle?: string | null;
  shareAttachImage?: boolean | null;
  eqEnabled?: boolean | null;
  eqPreset?: string | null;
  eqBandLevels?: string | null;
  updatedAt: number;
}

export interface RecentShowV3 {
  showId: string;
  lastPlayedAt: number;
  firstPlayedAt: number;
  totalPlayCount: number;
  deletedAt?: number | null;
}

export interface PlaybackPositionV3 {
  showId: string;
  recordingId: string;
  trackIndex: number;
  positionMs: number;
  date?: string;
  venue?: string;
  location?: string;
  updatedAt: number;
}

// Per-user notification read/dismiss overlay (ADR-0015). seen_at/dismissed_at
// are monotonic, so this never tombstones — a row simply gains non-null
// timestamps. Merge is a conflict-free union (earliest non-null wins).
export interface NotificationStateV3 {
  notificationId: number;
  seenAt?: number | null;
  dismissedAt?: number | null;
  updatedAt: number;
}

// ── Favorite Shows ──────────────────────────────────────────────────

const FAV_SHOW_COLS = `show_id, added_at, is_pinned, notes, preferred_recording_id,
  downloaded_recording_id, downloaded_format, recording_quality,
  playing_quality, custom_rating, last_accessed_at, tags,
  updated_at, deleted_at`;

/** Live favorites only — used by UI list endpoints. */
export function getFavoriteShows(userId: string): FavoriteShowV3[] {
  const db = getUsersDb();
  const rows = db.prepare(
    `SELECT ${FAV_SHOW_COLS}
     FROM favorite_shows
     WHERE user_id = ? AND deleted_at IS NULL
     ORDER BY added_at DESC`
  ).all(userId) as Record<string, unknown>[];

  return rows.map(rowToFavoriteShow);
}

/** All favorites including tombstones — used by /api/user/sync. */
function getFavoriteShowsWithTombstones(userId: string): FavoriteShowV3[] {
  const db = getUsersDb();
  const rows = db.prepare(
    `SELECT ${FAV_SHOW_COLS}
     FROM favorite_shows WHERE user_id = ?
     ORDER BY added_at DESC`
  ).all(userId) as Record<string, unknown>[];

  return rows.map(rowToFavoriteShow);
}

export function upsertFavoriteShow(userId: string, show: FavoriteShowV3): void {
  const db = getUsersDb();
  const now = Math.floor(Date.now() / 1000);
  // Upsert clears any tombstone — re-favoriting after delete is a resurrection.
  db.prepare(
    `INSERT INTO favorite_shows (user_id, show_id, added_at, is_pinned, notes,
      preferred_recording_id, downloaded_recording_id, downloaded_format,
      recording_quality, playing_quality, custom_rating, last_accessed_at, tags,
      updated_at, deleted_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
     ON CONFLICT(user_id, show_id) DO UPDATE SET
       is_pinned = excluded.is_pinned,
       notes = excluded.notes,
       preferred_recording_id = excluded.preferred_recording_id,
       downloaded_recording_id = excluded.downloaded_recording_id,
       downloaded_format = excluded.downloaded_format,
       recording_quality = excluded.recording_quality,
       playing_quality = excluded.playing_quality,
       custom_rating = excluded.custom_rating,
       last_accessed_at = excluded.last_accessed_at,
       tags = excluded.tags,
       updated_at = excluded.updated_at,
       deleted_at = NULL`
  ).run(
    userId, show.showId, show.addedAt ?? now,
    show.isPinned ? 1 : 0, show.notes ?? null,
    show.preferredRecordingId ?? null, show.downloadedRecordingId ?? null,
    show.downloadedFormat ?? null, show.recordingQuality ?? null,
    show.playingQuality ?? null, show.customRating ?? null,
    show.lastAccessedAt ?? null,
    show.tags ? JSON.stringify(show.tags) : null,
    show.updatedAt ?? now,
  );
}

/** Soft-delete: row stays as a tombstone so other devices learn about it. */
export function deleteFavoriteShow(userId: string, showId: string): boolean {
  const db = getUsersDb();
  const now = Math.floor(Date.now() / 1000);
  const result = db.prepare(
    `UPDATE favorite_shows
        SET deleted_at = ?, updated_at = ?
      WHERE user_id = ? AND show_id = ? AND deleted_at IS NULL`
  ).run(now, now, userId, showId);
  return result.changes > 0;
}

// ── Backlog (Show Queue) ────────────────────────────────────────────

const BACKLOG_COLS = `show_id, position, added_at, updated_at, deleted_at`;

function rowToBacklogItem(r: Record<string, unknown>): BacklogItemV3 {
  return {
    showId: r.show_id as string,
    position: r.position as number,
    addedAt: r.added_at as number,
    updatedAt: r.updated_at as number,
    deletedAt: (r.deleted_at as number | null) ?? null,
  };
}

/** Live backlog in play order (head first), tombstones excluded. */
export function getBacklog(userId: string): BacklogItemV3[] {
  const db = getUsersDb();
  const rows = db.prepare(
    `SELECT ${BACKLOG_COLS} FROM backlog
     WHERE user_id = ? AND deleted_at IS NULL
     ORDER BY position ASC`
  ).all(userId) as Record<string, unknown>[];
  return rows.map(rowToBacklogItem);
}

/** All backlog rows including tombstones — used by /api/user/sync. */
function getBacklogWithTombstones(userId: string): BacklogItemV3[] {
  const db = getUsersDb();
  const rows = db.prepare(
    `SELECT ${BACKLOG_COLS} FROM backlog WHERE user_id = ? ORDER BY position ASC`
  ).all(userId) as Record<string, unknown>[];
  return rows.map(rowToBacklogItem);
}

// Upsert one backlog row. LWW-guarded on updated_at so an out-of-order push
// (the auto-popping queue makes these likely) can't clobber newer state; a
// live upsert that wins resurrects any tombstone (re-add after pop/remove).
export function upsertBacklogItem(userId: string, item: BacklogItemV3): void {
  const db = getUsersDb();
  const now = Math.floor(Date.now() / 1000);
  db.prepare(
    `INSERT INTO backlog (user_id, show_id, position, added_at, updated_at, deleted_at)
     VALUES (?, ?, ?, ?, ?, ?)
     ON CONFLICT(user_id, show_id) DO UPDATE SET
       position = excluded.position,
       updated_at = excluded.updated_at,
       deleted_at = excluded.deleted_at
     WHERE excluded.updated_at >= backlog.updated_at`
  ).run(
    userId, item.showId, item.position,
    item.addedAt ?? now, item.updatedAt ?? now, item.deletedAt ?? null,
  );
}

/** Soft-delete (pop or remove): row stays as a tombstone so other devices learn of it. */
export function deleteBacklogItem(userId: string, showId: string): boolean {
  const db = getUsersDb();
  const now = Math.floor(Date.now() / 1000);
  const result = db.prepare(
    `UPDATE backlog SET deleted_at = ?, updated_at = ?
      WHERE user_id = ? AND show_id = ? AND deleted_at IS NULL`
  ).run(now, now, userId, showId);
  return result.changes > 0;
}

/** Rewrite positions to match `orderedShowIds` (drag-to-reorder). */
export function reorderBacklog(userId: string, orderedShowIds: string[]): void {
  const db = getUsersDb();
  const now = Math.floor(Date.now() / 1000);
  const stmt = db.prepare(
    `UPDATE backlog SET position = ?, updated_at = ?
      WHERE user_id = ? AND show_id = ?`
  );
  const tx = db.transaction(() => {
    orderedShowIds.forEach((showId, i) => stmt.run(i, now, userId, showId));
  });
  tx();
}

// ── Favorite Songs ──────────────────────────────────────────────────

const FAV_SONG_COLS = `id, show_id, track_title, track_number, recording_id, updated_at, deleted_at`;

function rowToFavoriteSong(r: Record<string, unknown>): FavoriteTrackV3 {
  return {
    id: r.id as number,
    showId: r.show_id as string,
    trackTitle: r.track_title as string,
    trackNumber: r.track_number as number | null,
    recordingId: r.recording_id as string | null,
    updatedAt: r.updated_at as number,
    deletedAt: r.deleted_at as number | null,
  };
}

export function getFavoriteSongs(userId: string): FavoriteTrackV3[] {
  const db = getUsersDb();
  const rows = db.prepare(
    `SELECT ${FAV_SONG_COLS}
     FROM favorite_songs
     WHERE user_id = ? AND deleted_at IS NULL
     ORDER BY created_at DESC`
  ).all(userId) as Record<string, unknown>[];
  return rows.map(rowToFavoriteSong);
}

function getFavoriteSongsWithTombstones(userId: string): FavoriteTrackV3[] {
  const db = getUsersDb();
  const rows = db.prepare(
    `SELECT ${FAV_SONG_COLS}
     FROM favorite_songs WHERE user_id = ?
     ORDER BY created_at DESC`
  ).all(userId) as Record<string, unknown>[];
  return rows.map(rowToFavoriteSong);
}

export function upsertFavoriteSong(userId: string, song: FavoriteTrackV3): number {
  const db = getUsersDb();
  const now = Math.floor(Date.now() / 1000);
  // Match by (user, show, track_title) — track_title is the stable identity
  // for a song within a show. Recording id can change without breaking the
  // favorite. Includes tombstoned rows so re-favoriting resurrects.
  const existing = db.prepare(
    `SELECT id FROM favorite_songs WHERE user_id = ? AND show_id = ? AND track_title = ?`
  ).get(userId, song.showId, song.trackTitle) as { id: number } | undefined;

  if (existing) {
    db.prepare(
      `UPDATE favorite_songs
          SET track_number = ?, recording_id = ?, updated_at = ?, deleted_at = NULL
        WHERE id = ?`
    ).run(song.trackNumber ?? null, song.recordingId ?? null, song.updatedAt ?? now, existing.id);
    return existing.id;
  }

  const result = db.prepare(
    `INSERT INTO favorite_songs (user_id, show_id, track_title, track_number, recording_id, updated_at)
     VALUES (?, ?, ?, ?, ?, ?)`
  ).run(userId, song.showId, song.trackTitle, song.trackNumber ?? null, song.recordingId ?? null, song.updatedAt ?? now);
  return Number(result.lastInsertRowid);
}

/**
 * Soft-delete a favorite song by natural key (showId + trackTitle). Mobile
 * clients don't know the server-side autoincrement id, but they do know the
 * stable identity tuple — matches what upsertFavoriteSong does.
 */
export function deleteFavoriteSongByKey(
  userId: string, showId: string, trackTitle: string
): boolean {
  const db = getUsersDb();
  const now = Math.floor(Date.now() / 1000);
  const result = db.prepare(
    `UPDATE favorite_songs
        SET deleted_at = ?, updated_at = ?
      WHERE user_id = ? AND show_id = ? AND track_title = ? AND deleted_at IS NULL`
  ).run(now, now, userId, showId, trackTitle);
  return result.changes > 0;
}

// ── Reviews ─────────────────────────────────────────────────────────

function rowToReview(db: Database.Database, userId: string, r: Record<string, unknown>): ReviewV3 {
  const showId = r.show_id as string;
  const tags = db.prepare(
    `SELECT player_name, instruments, is_standout, notes
     FROM show_player_tags WHERE user_id = ? AND show_id = ?`
  ).all(userId, showId) as Record<string, unknown>[];

  return {
    showId,
    notes: r.notes as string | null,
    overallRating: r.overall_rating as number | null,
    recordingQuality: r.recording_quality as number | null,
    playingQuality: r.playing_quality as number | null,
    reviewedRecordingId: r.reviewed_recording_id as string | null,
    playerTags: tags.length > 0 ? tags.map((t) => ({
      playerName: t.player_name as string,
      instruments: t.instruments as string | null,
      isStandout: (t.is_standout as number) === 1,
      notes: t.notes as string | null,
    })) : null,
    updatedAt: r.updated_at as number,
    deletedAt: r.deleted_at as number | null,
  };
}

export function getReviews(userId: string): ReviewV3[] {
  const db = getUsersDb();
  const rows = db.prepare(
    `SELECT show_id, notes, overall_rating, recording_quality, playing_quality,
            reviewed_recording_id, created_at, updated_at, deleted_at
     FROM show_reviews
     WHERE user_id = ? AND deleted_at IS NULL
     ORDER BY updated_at DESC`
  ).all(userId) as Record<string, unknown>[];
  return rows.map((r) => rowToReview(db, userId, r));
}

function getReviewsWithTombstones(userId: string): ReviewV3[] {
  const db = getUsersDb();
  const rows = db.prepare(
    `SELECT show_id, notes, overall_rating, recording_quality, playing_quality,
            reviewed_recording_id, created_at, updated_at, deleted_at
     FROM show_reviews WHERE user_id = ?
     ORDER BY updated_at DESC`
  ).all(userId) as Record<string, unknown>[];
  return rows.map((r) => rowToReview(db, userId, r));
}

export function upsertReview(userId: string, review: ReviewV3): void {
  const db = getUsersDb();
  const now = Math.floor(Date.now() / 1000);

  db.prepare(
    `INSERT INTO show_reviews (user_id, show_id, notes, overall_rating, recording_quality,
      playing_quality, reviewed_recording_id, created_at, updated_at, deleted_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
     ON CONFLICT(user_id, show_id) DO UPDATE SET
       notes = excluded.notes,
       overall_rating = excluded.overall_rating,
       recording_quality = excluded.recording_quality,
       playing_quality = excluded.playing_quality,
       reviewed_recording_id = excluded.reviewed_recording_id,
       updated_at = excluded.updated_at,
       deleted_at = NULL`
  ).run(
    userId, review.showId, review.notes ?? null, review.overallRating ?? null,
    review.recordingQuality ?? null, review.playingQuality ?? null,
    review.reviewedRecordingId ?? null, now, review.updatedAt ?? now
  );

  // Replace player tags (they travel with the review)
  db.prepare(
    `DELETE FROM show_player_tags WHERE user_id = ? AND show_id = ?`
  ).run(userId, review.showId);

  if (review.playerTags?.length) {
    const insert = db.prepare(
      `INSERT INTO show_player_tags (user_id, show_id, player_name, instruments, is_standout, notes)
       VALUES (?, ?, ?, ?, ?, ?)`
    );
    for (const tag of review.playerTags) {
      insert.run(userId, review.showId, tag.playerName,
        tag.instruments ?? null, tag.isStandout ? 1 : 0, tag.notes ?? null);
    }
  }
}

export function deleteReview(userId: string, showId: string): boolean {
  const db = getUsersDb();
  const now = Math.floor(Date.now() / 1000);
  // Drop the tag children (they're embedded in the review record on the wire),
  // soft-delete the parent.
  db.prepare(
    `DELETE FROM show_player_tags WHERE user_id = ? AND show_id = ?`
  ).run(userId, showId);
  const result = db.prepare(
    `UPDATE show_reviews
        SET deleted_at = ?, updated_at = ?
      WHERE user_id = ? AND show_id = ? AND deleted_at IS NULL`
  ).run(now, now, userId, showId);
  return result.changes > 0;
}

// ── Recording Preferences ───────────────────────────────────────────

function rowToRecordingPref(r: Record<string, unknown>): RecordingPrefV3 {
  return {
    showId: r.show_id as string,
    recordingId: r.recording_id as string,
    updatedAt: r.updated_at as number,
    deletedAt: r.deleted_at as number | null,
  };
}

export function getRecordingPreferences(userId: string): RecordingPrefV3[] {
  const db = getUsersDb();
  const rows = db.prepare(
    `SELECT show_id, recording_id, updated_at, deleted_at
     FROM recording_preferences
     WHERE user_id = ? AND deleted_at IS NULL`
  ).all(userId) as Record<string, unknown>[];
  return rows.map(rowToRecordingPref);
}

function getRecordingPreferencesWithTombstones(userId: string): RecordingPrefV3[] {
  const db = getUsersDb();
  const rows = db.prepare(
    `SELECT show_id, recording_id, updated_at, deleted_at
     FROM recording_preferences WHERE user_id = ?`
  ).all(userId) as Record<string, unknown>[];
  return rows.map(rowToRecordingPref);
}

export function upsertRecordingPreference(userId: string, showId: string, recordingId: string): void {
  const db = getUsersDb();
  db.prepare(
    `INSERT INTO recording_preferences (user_id, show_id, recording_id, updated_at, deleted_at)
     VALUES (?, ?, ?, unixepoch(), NULL)
     ON CONFLICT(user_id, show_id) DO UPDATE SET
       recording_id = excluded.recording_id,
       updated_at = excluded.updated_at,
       deleted_at = NULL`
  ).run(userId, showId, recordingId);
}

export function deleteRecordingPreference(userId: string, showId: string): boolean {
  const db = getUsersDb();
  const now = Math.floor(Date.now() / 1000);
  const result = db.prepare(
    `UPDATE recording_preferences
        SET deleted_at = ?, updated_at = ?
      WHERE user_id = ? AND show_id = ? AND deleted_at IS NULL`
  ).run(now, now, userId, showId);
  return result.changes > 0;
}

// ── Recent Shows ────────────────────────────────────────────────────

function rowToRecentShow(r: Record<string, unknown>): RecentShowV3 {
  return {
    showId: r.show_id as string,
    lastPlayedAt: r.last_played_at as number,
    firstPlayedAt: r.first_played_at as number,
    totalPlayCount: r.total_play_count as number,
    deletedAt: r.deleted_at as number | null,
  };
}

export function getRecentShows(userId: string): RecentShowV3[] {
  const db = getUsersDb();
  const rows = db.prepare(
    `SELECT show_id, last_played_at, first_played_at, total_play_count, deleted_at
     FROM recent_shows
     WHERE user_id = ? AND deleted_at IS NULL
     ORDER BY last_played_at DESC`
  ).all(userId) as Record<string, unknown>[];
  return rows.map(rowToRecentShow);
}

function getRecentShowsWithTombstones(userId: string): RecentShowV3[] {
  const db = getUsersDb();
  const rows = db.prepare(
    `SELECT show_id, last_played_at, first_played_at, total_play_count, deleted_at
     FROM recent_shows WHERE user_id = ?
     ORDER BY last_played_at DESC`
  ).all(userId) as Record<string, unknown>[];
  return rows.map(rowToRecentShow);
}

export function upsertRecentShow(userId: string, showId: string): void {
  const db = getUsersDb();
  const now = Math.floor(Date.now() / 1000);
  // Upsert clears tombstone — replaying after a "Clear history" resurrects.
  db.prepare(
    `INSERT INTO recent_shows (user_id, show_id, last_played_at, first_played_at, total_play_count, deleted_at)
     VALUES (?, ?, ?, ?, 1, NULL)
     ON CONFLICT(user_id, show_id) DO UPDATE SET
       last_played_at = excluded.last_played_at,
       total_play_count = total_play_count + 1,
       deleted_at = NULL`
  ).run(userId, showId, now, now);
}

export function deleteRecentShow(userId: string, showId: string): boolean {
  const db = getUsersDb();
  const now = Math.floor(Date.now() / 1000);
  const result = db.prepare(
    `UPDATE recent_shows
        SET deleted_at = ?, last_played_at = ?
      WHERE user_id = ? AND show_id = ? AND deleted_at IS NULL`
  ).run(now, now, userId, showId);
  return result.changes > 0;
}

// ── Playback Position ───────────────────────────────────────────────

export function getPlaybackPosition(userId: string): PlaybackPositionV3 | null {
  const db = getUsersDb();
  const row = db.prepare(
    `SELECT show_id, recording_id, track_index, position_ms, date, venue, location, updated_at
     FROM playback_position WHERE user_id = ?`
  ).get(userId) as Record<string, unknown> | undefined;

  if (!row) return null;
  return {
    showId: row.show_id as string,
    recordingId: row.recording_id as string,
    trackIndex: row.track_index as number,
    positionMs: row.position_ms as number,
    date: row.date as string | undefined,
    venue: row.venue as string | undefined,
    location: row.location as string | undefined,
    updatedAt: row.updated_at as number,
  };
}

export function upsertPlaybackPosition(userId: string, pos: PlaybackPositionV3): void {
  const db = getUsersDb();
  db.prepare(
    `INSERT INTO playback_position (user_id, show_id, recording_id, track_index, position_ms, date, venue, location, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, unixepoch())
     ON CONFLICT(user_id) DO UPDATE SET
       show_id = excluded.show_id,
       recording_id = excluded.recording_id,
       track_index = excluded.track_index,
       position_ms = excluded.position_ms,
       date = excluded.date,
       venue = excluded.venue,
       location = excluded.location,
       updated_at = excluded.updated_at`
  ).run(userId, pos.showId, pos.recordingId, pos.trackIndex, pos.positionMs,
    pos.date ?? null, pos.venue ?? null, pos.location ?? null);
}

export function clearPlaybackPosition(userId: string): void {
  const db = getUsersDb();
  db.prepare(`DELETE FROM playback_position WHERE user_id = ?`).run(userId);
}

// ── Settings ────────────────────────────────────────────────────────

export function getSettings(userId: string): SettingsV3 | null {
  const db = getUsersDb();
  const row = db.prepare(
    `SELECT include_shows_without_recordings, favorites_display_mode, force_online,
            source_badge_style, share_attach_image, eq_enabled, eq_preset, eq_band_levels, updated_at
     FROM user_settings WHERE user_id = ?`
  ).get(userId) as Record<string, unknown> | undefined;

  if (!row) return null;
  return {
    includeShowsWithoutRecordings: (row.include_shows_without_recordings as number) === 1,
    favoritesDisplayMode: row.favorites_display_mode as string,
    forceOnline: (row.force_online as number) === 1,
    sourceBadgeStyle: row.source_badge_style as string,
    shareAttachImage: (row.share_attach_image as number) === 1,
    eqEnabled: (row.eq_enabled as number) === 1,
    eqPreset: row.eq_preset as string | null,
    eqBandLevels: row.eq_band_levels as string | null,
    updatedAt: row.updated_at as number,
  };
}

export function upsertSettings(userId: string, settings: SettingsV3): void {
  const db = getUsersDb();
  db.prepare(
    `INSERT INTO user_settings (user_id, include_shows_without_recordings, favorites_display_mode,
      force_online, source_badge_style, share_attach_image, eq_enabled, eq_preset, eq_band_levels, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, unixepoch())
     ON CONFLICT(user_id) DO UPDATE SET
       include_shows_without_recordings = excluded.include_shows_without_recordings,
       favorites_display_mode = excluded.favorites_display_mode,
       force_online = excluded.force_online,
       source_badge_style = excluded.source_badge_style,
       share_attach_image = excluded.share_attach_image,
       eq_enabled = excluded.eq_enabled,
       eq_preset = excluded.eq_preset,
       eq_band_levels = excluded.eq_band_levels,
       updated_at = excluded.updated_at`
  ).run(
    userId,
    settings.includeShowsWithoutRecordings ? 1 : 0,
    settings.favoritesDisplayMode ?? "LIST",
    settings.forceOnline ? 1 : 0,
    settings.sourceBadgeStyle ?? "LONG",
    settings.shareAttachImage ? 1 : 0,
    settings.eqEnabled ? 1 : 0,
    settings.eqPreset ?? null,
    settings.eqBandLevels ?? null
  );
}

// ── Notification State (ADR-0015) ───────────────────────────────────

function rowToNotificationState(r: Record<string, unknown>): NotificationStateV3 {
  return {
    notificationId: r.notification_id as number,
    seenAt: r.seen_at as number | null,
    dismissedAt: r.dismissed_at as number | null,
    updatedAt: r.updated_at as number,
  };
}

export function getNotificationState(userId: string): NotificationStateV3[] {
  const db = getUsersDb();
  const rows = db.prepare(
    `SELECT notification_id, seen_at, dismissed_at, updated_at
     FROM notification_state WHERE user_id = ?`
  ).all(userId) as Record<string, unknown>[];
  return rows.map(rowToNotificationState);
}

/**
 * Union-merge one overlay row. seen_at/dismissed_at are monotonic, so we keep
 * the earliest non-null on each column independently: a message is seen if seen
 * on any device, dismissed if dismissed on any device. SQLite's scalar MIN()
 * returns NULL only when every argument is NULL; COALESCE strips nulls unless
 * both sides are null — so the pair below is null iff neither side has a value.
 */
export function upsertNotificationState(userId: string, s: NotificationStateV3): void {
  const db = getUsersDb();
  db.prepare(
    `INSERT INTO notification_state (user_id, notification_id, seen_at, dismissed_at, updated_at)
     VALUES (?, ?, ?, ?, unixepoch())
     ON CONFLICT(user_id, notification_id) DO UPDATE SET
       seen_at      = MIN(COALESCE(notification_state.seen_at,      excluded.seen_at),
                          COALESCE(excluded.seen_at,      notification_state.seen_at)),
       dismissed_at = MIN(COALESCE(notification_state.dismissed_at, excluded.dismissed_at),
                          COALESCE(excluded.dismissed_at, notification_state.dismissed_at)),
       updated_at   = unixepoch()`
  ).run(userId, s.notificationId, s.seenAt ?? null, s.dismissedAt ?? null);
}

/** Bulk union-merge — one transaction for the mark-all/archive-all path. */
export function upsertNotificationStates(userId: string, rows: NotificationStateV3[]): void {
  const db = getUsersDb();
  const tx = db.transaction((items: NotificationStateV3[]) => {
    for (const s of items) upsertNotificationState(userId, s);
  });
  tx(rows);
}

// ── Full Backup V3 ──────────────────────────────────────────────────

/**
 * Full V3 backup including tombstones. This is the sync wire format —
 * clients need to see deleted_at rows so they can apply deletes locally.
 * For UI list endpoints, prefer the non-tombstone getters.
 */
export function getFullBackupV3(userId: string): BackupV3 {
  return {
    version: 3,
    exportedAt: Math.floor(Date.now() / 1000),
    app: "deadly-web",
    favorites: {
      shows: getFavoriteShowsWithTombstones(userId),
      tracks: getFavoriteSongsWithTombstones(userId),
    },
    reviews: getReviewsWithTombstones(userId),
    recordingPreferences: getRecordingPreferencesWithTombstones(userId),
    settings: getSettings(userId),
    recentShows: getRecentShowsWithTombstones(userId),
    playbackPosition: getPlaybackPosition(userId),
    notificationState: getNotificationState(userId),
    backlog: getBacklogWithTombstones(userId),
  };
}

export function importFullBackupV3(userId: string, data: BackupV3): void {
  const db = getUsersDb();
  const transaction = db.transaction(() => {
    // Favorites
    for (const show of data.favorites?.shows ?? []) {
      upsertFavoriteShow(userId, show);
    }
    for (const track of data.favorites?.tracks ?? []) {
      upsertFavoriteSong(userId, track);
    }

    // Reviews
    for (const review of data.reviews ?? []) {
      upsertReview(userId, review);
    }

    // Recording preferences
    for (const pref of data.recordingPreferences ?? []) {
      upsertRecordingPreference(userId, pref.showId, pref.recordingId);
    }

    // Settings
    if (data.settings) {
      upsertSettings(userId, data.settings);
    }

    // Recent shows. Tombstones in the import carry their deleted_at through.
    if (data.recentShows) {
      const insertRecent = db.prepare(
        `INSERT INTO recent_shows (user_id, show_id, last_played_at, first_played_at, total_play_count, deleted_at)
         VALUES (?, ?, ?, ?, ?, ?)
         ON CONFLICT(user_id, show_id) DO UPDATE SET
           last_played_at = MAX(recent_shows.last_played_at, excluded.last_played_at),
           total_play_count = MAX(recent_shows.total_play_count, excluded.total_play_count),
           deleted_at = excluded.deleted_at`
      );
      for (const r of data.recentShows) {
        insertRecent.run(userId, r.showId, r.lastPlayedAt, r.firstPlayedAt, r.totalPlayCount, r.deletedAt ?? null);
      }
    }

    // Playback position
    if (data.playbackPosition) {
      upsertPlaybackPosition(userId, data.playbackPosition);
    }

    // Notification state (ADR-0015) — union-merge each overlay row.
    for (const s of data.notificationState ?? []) {
      upsertNotificationState(userId, s);
    }

    // Backlog (Show Queue) — LWW-guarded upsert; tombstones carry through.
    for (const item of data.backlog ?? []) {
      upsertBacklogItem(userId, item);
    }
  });

  transaction();
}

// ── Helpers ─────────────────────────────────────────────────────────

function rowToFavoriteShow(r: Record<string, unknown>): FavoriteShowV3 {
  let tags: string[] | null = null;
  if (r.tags && typeof r.tags === "string") {
    try { tags = JSON.parse(r.tags); } catch { /* ignore */ }
  }
  return {
    showId: r.show_id as string,
    addedAt: r.added_at as number,
    isPinned: (r.is_pinned as number) === 1,
    lastAccessedAt: r.last_accessed_at as number | null,
    tags,
    notes: r.notes as string | null,
    preferredRecordingId: r.preferred_recording_id as string | null,
    downloadedRecordingId: r.downloaded_recording_id as string | null,
    downloadedFormat: r.downloaded_format as string | null,
    recordingQuality: r.recording_quality as number | null,
    playingQuality: r.playing_quality as number | null,
    customRating: r.custom_rating as number | null,
    updatedAt: r.updated_at as number,
    deletedAt: r.deleted_at as number | null,
  };
}
