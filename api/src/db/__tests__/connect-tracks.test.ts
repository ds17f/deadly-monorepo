import { describe, it, expect, beforeEach } from "vitest";

// Isolated in-memory users DB. USERS_DB_PATH is read at module load in users.ts,
// so set it before the DAO (and its getUsersDb) is imported.
process.env.USERS_DB_PATH = ":memory:";

const { getUsersDb } = await import("../users.js");
const { getRecordingTracks, upsertRecordingTracks, purgeStaleRecordingTracks } =
  await import("../connectTracks.js");

const tracks = [
  { title: "Bertha", durationMs: 360_000 },
  { title: "Sugar Magnolia", durationMs: 420_000 },
];

beforeEach(() => {
  getUsersDb().exec("DELETE FROM connect_recording_tracks");
});

describe("connect recording-tracks cache", () => {
  it("round-trips an upserted track list", () => {
    upsertRecordingTracks("rec-1", tracks);
    expect(getRecordingTracks("rec-1")).toEqual(tracks);
  });

  it("returns null for an unknown recording", () => {
    expect(getRecordingTracks("nope")).toBeNull();
  });

  it("is keyed by recording (global) and refreshes on re-upsert", () => {
    upsertRecordingTracks("rec-1", tracks);
    const newer = [{ title: "Truckin'", durationMs: 300_000 }];
    upsertRecordingTracks("rec-1", newer);
    expect(getRecordingTracks("rec-1")).toEqual(newer);
  });

  it("ignores empty track lists and blank ids", () => {
    upsertRecordingTracks("rec-empty", []);
    upsertRecordingTracks("", tracks);
    expect(getRecordingTracks("rec-empty")).toBeNull();
    expect(getRecordingTracks("")).toBeNull();
  });

  it("purges only rows older than the TTL", () => {
    upsertRecordingTracks("fresh", tracks);
    upsertRecordingTracks("stale", tracks);
    // Age "stale" to 31 days ago.
    const old = Math.floor(Date.now() / 1000) - 31 * 24 * 60 * 60;
    getUsersDb()
      .prepare("UPDATE connect_recording_tracks SET updated_at = ? WHERE recording_id = ?")
      .run(old, "stale");

    const removed = purgeStaleRecordingTracks(30 * 24 * 60 * 60);
    expect(removed).toBe(1);
    expect(getRecordingTracks("stale")).toBeNull();
    expect(getRecordingTracks("fresh")).toEqual(tracks);
  });
});
