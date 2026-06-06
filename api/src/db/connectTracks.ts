import { getUsersDb } from "./users.js";
import type { SessionTrack } from "../connect/types.js";

// Global, per-recording cache of Connect session track lists.
//
// A recording's track list (titles + durations) is identical for every user,
// so this is keyed by recordingId alone — one row per recording, shared across
// all users. The Connect state machine persists only a position-only bookmark
// (show + track index + ms) via PlaybackPositionV3; this cache lets a
// position-only hydrate restore the full track list so the server can broadcast
// complete state. Self-healing: any client that loads a recording tops it up.

export function getRecordingTracks(recordingId: string): SessionTrack[] | null {
  const db = getUsersDb();
  const row = db
    .prepare(`SELECT tracks FROM connect_recording_tracks WHERE recording_id = ?`)
    .get(recordingId) as { tracks: string } | undefined;
  if (!row) return null;
  try {
    return JSON.parse(row.tracks) as SessionTrack[];
  } catch {
    return null;
  }
}

export function upsertRecordingTracks(recordingId: string, tracks: SessionTrack[]): void {
  if (!recordingId || tracks.length === 0) return;
  const db = getUsersDb();
  db.prepare(
    `INSERT INTO connect_recording_tracks (recording_id, tracks, updated_at)
     VALUES (?, ?, unixepoch())
     ON CONFLICT(recording_id) DO UPDATE SET
       tracks = excluded.tracks,
       updated_at = excluded.updated_at`
  ).run(recordingId, JSON.stringify(tracks));
}

/// Delete cache rows untouched for longer than ttlSeconds. Safe to purge
/// aggressively — entries self-heal from the next client that loads the
/// recording. Returns the number of rows removed.
export function purgeStaleRecordingTracks(ttlSeconds: number): number {
  const db = getUsersDb();
  const cutoff = Math.floor(Date.now() / 1000) - ttlSeconds;
  const result = db
    .prepare(`DELETE FROM connect_recording_tracks WHERE updated_at < ?`)
    .run(cutoff);
  return result.changes;
}
