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
}

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
}

export interface FavoriteTrackV3 {
  showId: string;
  trackTitle: string;
  trackNumber?: number | null;
  recordingId?: string | null;
}

export interface ReviewV3 {
  showId: string;
  notes?: string | null;
  overallRating?: number | null;
  recordingQuality?: number | null;
  playingQuality?: number | null;
  reviewedRecordingId?: string | null;
  playerTags?: PlayerTagV3[] | null;
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
}

export interface RecentShowV3 {
  showId: string;
  lastPlayedAt: number;
  firstPlayedAt: number;
  totalPlayCount: number;
}

export interface PlaybackPositionV3 {
  showId: string;
  recordingId: string;
  trackIndex: number;
  positionMs: number;
  date?: string;
  venue?: string;
  location?: string;
}

// ── Favorite Shows ──────────────────────────────────────────────────

export function getFavoriteShows(userId: string): FavoriteShowV3[] {
  const db = getUsersDb();
  const rows = db.prepare(
    `SELECT show_id, added_at, is_pinned, notes, preferred_recording_id,
            downloaded_recording_id, downloaded_format, recording_quality,
            playing_quality, custom_rating, last_accessed_at, tags
     FROM favorite_shows WHERE user_id = ? ORDER BY added_at DESC`
  ).all(userId) as Record<string, unknown>[];

  return rows.map(rowToFavoriteShow);
}

export function upsertFavoriteShow(userId: string, show: FavoriteShowV3): void {
  const db = getUsersDb();
  db.prepare(
    `INSERT INTO favorite_shows (user_id, show_id, added_at, is_pinned, notes,
      preferred_recording_id, downloaded_recording_id, downloaded_format,
      recording_quality, playing_quality, custom_rating, last_accessed_at, tags)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
       tags = excluded.tags`
  ).run(
    userId, show.showId, show.addedAt ?? Math.floor(Date.now() / 1000),
    show.isPinned ? 1 : 0, show.notes ?? null,
    show.preferredRecordingId ?? null, show.downloadedRecordingId ?? null,
    show.downloadedFormat ?? null, show.recordingQuality ?? null,
    show.playingQuality ?? null, show.customRating ?? null,
    show.lastAccessedAt ?? null,
    show.tags ? JSON.stringify(show.tags) : null
  );
}

export function deleteFavoriteShow(userId: string, showId: string): boolean {
  const db = getUsersDb();
  const result = db.prepare(
    `DELETE FROM favorite_shows WHERE user_id = ? AND show_id = ?`
  ).run(userId, showId);
  return result.changes > 0;
}

// ── Favorite Songs ──────────────────────────────────────────────────

export function getFavoriteSongs(userId: string): FavoriteTrackV3[] {
  const db = getUsersDb();
  const rows = db.prepare(
    `SELECT show_id, track_title, track_number, recording_id
     FROM favorite_songs WHERE user_id = ? ORDER BY created_at DESC`
  ).all(userId) as Record<string, unknown>[];

  return rows.map((r) => ({
    showId: r.show_id as string,
    trackTitle: r.track_title as string,
    trackNumber: r.track_number as number | null,
    recordingId: r.recording_id as string | null,
  }));
}

export function upsertFavoriteSong(userId: string, song: FavoriteTrackV3): number {
  const db = getUsersDb();
  // Check for existing match
  const existing = db.prepare(
    `SELECT id FROM favorite_songs WHERE user_id = ? AND show_id = ? AND track_title = ?`
  ).get(userId, song.showId, song.trackTitle) as { id: number } | undefined;

  if (existing) {
    db.prepare(
      `UPDATE favorite_songs SET track_number = ?, recording_id = ? WHERE id = ?`
    ).run(song.trackNumber ?? null, song.recordingId ?? null, existing.id);
    return existing.id;
  }

  const result = db.prepare(
    `INSERT INTO favorite_songs (user_id, show_id, track_title, track_number, recording_id)
     VALUES (?, ?, ?, ?, ?)`
  ).run(userId, song.showId, song.trackTitle, song.trackNumber ?? null, song.recordingId ?? null);
  return Number(result.lastInsertRowid);
}

export function deleteFavoriteSong(userId: string, id: number): boolean {
  const db = getUsersDb();
  const result = db.prepare(
    `DELETE FROM favorite_songs WHERE id = ? AND user_id = ?`
  ).run(id, userId);
  return result.changes > 0;
}

// ── Reviews ─────────────────────────────────────────────────────────

export function getReviews(userId: string): ReviewV3[] {
  const db = getUsersDb();
  const rows = db.prepare(
    `SELECT show_id, notes, overall_rating, recording_quality, playing_quality,
            reviewed_recording_id, created_at, updated_at
     FROM show_reviews WHERE user_id = ? ORDER BY updated_at DESC`
  ).all(userId) as Record<string, unknown>[];

  return rows.map((r) => {
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
    };
  });
}

export function upsertReview(userId: string, review: ReviewV3): void {
  const db = getUsersDb();
  const now = Math.floor(Date.now() / 1000);

  db.prepare(
    `INSERT INTO show_reviews (user_id, show_id, notes, overall_rating, recording_quality,
      playing_quality, reviewed_recording_id, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
     ON CONFLICT(user_id, show_id) DO UPDATE SET
       notes = excluded.notes,
       overall_rating = excluded.overall_rating,
       recording_quality = excluded.recording_quality,
       playing_quality = excluded.playing_quality,
       reviewed_recording_id = excluded.reviewed_recording_id,
       updated_at = excluded.updated_at`
  ).run(
    userId, review.showId, review.notes ?? null, review.overallRating ?? null,
    review.recordingQuality ?? null, review.playingQuality ?? null,
    review.reviewedRecordingId ?? null, now, now
  );

  // Replace player tags
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
  db.prepare(
    `DELETE FROM show_player_tags WHERE user_id = ? AND show_id = ?`
  ).run(userId, showId);
  const result = db.prepare(
    `DELETE FROM show_reviews WHERE user_id = ? AND show_id = ?`
  ).run(userId, showId);
  return result.changes > 0;
}

// ── Recording Preferences ───────────────────────────────────────────

export function getRecordingPreferences(userId: string): RecordingPrefV3[] {
  const db = getUsersDb();
  const rows = db.prepare(
    `SELECT show_id, recording_id FROM recording_preferences WHERE user_id = ?`
  ).all(userId) as Record<string, unknown>[];

  return rows.map((r) => ({
    showId: r.show_id as string,
    recordingId: r.recording_id as string,
  }));
}

export function upsertRecordingPreference(userId: string, showId: string, recordingId: string): void {
  const db = getUsersDb();
  db.prepare(
    `INSERT INTO recording_preferences (user_id, show_id, recording_id, updated_at)
     VALUES (?, ?, ?, unixepoch())
     ON CONFLICT(user_id, show_id) DO UPDATE SET
       recording_id = excluded.recording_id,
       updated_at = excluded.updated_at`
  ).run(userId, showId, recordingId);
}

// ── Recent Shows ────────────────────────────────────────────────────

export function getRecentShows(userId: string): RecentShowV3[] {
  const db = getUsersDb();
  const rows = db.prepare(
    `SELECT show_id, last_played_at, first_played_at, total_play_count
     FROM recent_shows WHERE user_id = ? ORDER BY last_played_at DESC`
  ).all(userId) as Record<string, unknown>[];

  return rows.map((r) => ({
    showId: r.show_id as string,
    lastPlayedAt: r.last_played_at as number,
    firstPlayedAt: r.first_played_at as number,
    totalPlayCount: r.total_play_count as number,
  }));
}

export function upsertRecentShow(userId: string, showId: string): void {
  const db = getUsersDb();
  const now = Math.floor(Date.now() / 1000);
  db.prepare(
    `INSERT INTO recent_shows (user_id, show_id, last_played_at, first_played_at, total_play_count)
     VALUES (?, ?, ?, ?, 1)
     ON CONFLICT(user_id, show_id) DO UPDATE SET
       last_played_at = excluded.last_played_at,
       total_play_count = total_play_count + 1`
  ).run(userId, showId, now, now);
}

// ── Playback Position ───────────────────────────────────────────────

export function getPlaybackPosition(userId: string): PlaybackPositionV3 | null {
  const db = getUsersDb();
  const row = db.prepare(
    `SELECT show_id, recording_id, track_index, position_ms, date, venue, location
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

export function loadUserPlaybackState(userId: string): import("../connect/types.js").UserPlaybackState | null {
  const pos = getPlaybackPosition(userId);
  if (!pos) return null;
  return {
    showId: pos.showId,
    recordingId: pos.recordingId,
    trackIndex: pos.trackIndex,
    positionMs: pos.positionMs,
    date: pos.date,
    venue: pos.venue,
    location: pos.location,
    activeDeviceId: null,
    activeDeviceName: null,
    activeDeviceType: null,
    isPlaying: false,
    updatedAt: Date.now(),
  };
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
            source_badge_style, share_attach_image, eq_enabled, eq_preset, eq_band_levels
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

// ── Full Backup V3 ──────────────────────────────────────────────────

export function getFullBackupV3(userId: string): BackupV3 {
  return {
    version: 3,
    exportedAt: Math.floor(Date.now() / 1000),
    app: "deadly-web",
    favorites: {
      shows: getFavoriteShows(userId),
      tracks: getFavoriteSongs(userId),
    },
    reviews: getReviews(userId),
    recordingPreferences: getRecordingPreferences(userId),
    settings: getSettings(userId),
    recentShows: getRecentShows(userId),
    playbackPosition: getPlaybackPosition(userId),
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

    // Recent shows
    if (data.recentShows) {
      const insertRecent = db.prepare(
        `INSERT INTO recent_shows (user_id, show_id, last_played_at, first_played_at, total_play_count)
         VALUES (?, ?, ?, ?, ?)
         ON CONFLICT(user_id, show_id) DO UPDATE SET
           last_played_at = MAX(recent_shows.last_played_at, excluded.last_played_at),
           total_play_count = MAX(recent_shows.total_play_count, excluded.total_play_count)`
      );
      for (const r of data.recentShows) {
        insertRecent.run(userId, r.showId, r.lastPlayedAt, r.firstPlayedAt, r.totalPlayCount);
      }
    }

    // Playback position
    if (data.playbackPosition) {
      upsertPlaybackPosition(userId, data.playbackPosition);
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
  };
}
