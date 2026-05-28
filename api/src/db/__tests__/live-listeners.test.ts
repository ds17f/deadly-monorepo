import { describe, it, expect, beforeEach, afterAll } from "vitest";
import path from "node:path";
import fs from "node:fs";
import os from "node:os";

const TMP_DB = path.join(
  os.tmpdir(),
  `live-listeners-test-${process.pid}-${Date.now()}.db`,
);
process.env.ANALYTICS_DB_PATH = TMP_DB;

const {
  getAnalyticsDb,
  insertEvents,
  getLiveListeners,
  getRecentListening,
  closeAnalyticsDb,
} = await import("../analytics.js");

function ev(
  event: "playback_start" | "playback_end",
  iid: string,
  sid: string,
  showId: string,
  trackIndex: number,
  ts: number,
  extra: Record<string, unknown> = {},
) {
  return {
    event,
    ts,
    iid,
    sid,
    platform: "ios",
    app_version: "2.30.0",
    props: { show_id: showId, track_index: trackIndex, ...extra },
  };
}

beforeEach(() => {
  const db = getAnalyticsDb();
  db.exec(`DELETE FROM analytics_events;`);
});

afterAll(() => {
  closeAnalyticsDb();
  if (fs.existsSync(TMP_DB)) fs.unlinkSync(TMP_DB);
});

describe("getLiveListeners — dual live boundary", () => {
  it("a user 30s past a playback_end still counts as live (between-tracks gap)", () => {
    // Reported bug: between tracks, the user was dropping into Recent
    // Listening as "36 seconds ago" while still actively listening.
    const now = Date.now();
    insertEvents([
      ev("playback_start", "alice", "s1", "show-A", 0, now - 60_000),
      ev("playback_end", "alice", "s1", "show-A", 0, now - 30_000, {
        reason: "completed",
      }),
    ]);

    const live = getLiveListeners();
    expect(live).toHaveLength(1);
    expect(live[0].iid).toBe("alice");
    expect(live[0].show_id).toBe("show-A");
    expect(live[0].started_at).toBe(now - 60_000);
  });

  it("a user mid-long-track is live even with no events for 30 minutes", () => {
    // The case the previous fix broke: Dark Star can run 30+ minutes
    // with no intervening events. The 45-min START window covers this.
    const now = Date.now();
    insertEvents([
      ev("playback_start", "bob", "s1", "show-B", 5, now - 30 * 60_000),
    ]);

    const live = getLiveListeners();
    expect(live.map((l) => l.iid)).toEqual(["bob"]);
    expect(live[0].show_id).toBe("show-B");
  });

  it("a user whose start was >45min ago is no longer live", () => {
    const now = Date.now();
    insertEvents([
      ev("playback_start", "carol", "s1", "show-C", 0, now - 60 * 60_000),
    ]);
    expect(getLiveListeners()).toEqual([]);
  });

  it("a user whose latest end is >2min ago is no longer live (finished)", () => {
    const now = Date.now();
    insertEvents([
      ev("playback_start", "dave", "s1", "show-D", 0, now - 5 * 60_000),
      ev("playback_end", "dave", "s1", "show-D", 0, now - 3 * 60_000, {
        reason: "completed",
      }),
    ]);
    expect(getLiveListeners()).toEqual([]);
  });

  it("switching shows updates current state — latest event wins", () => {
    const now = Date.now();
    insertEvents([
      ev("playback_start", "eve", "s1", "show-A", 0, now - 10 * 60_000),
      ev("playback_end", "eve", "s1", "show-A", 0, now - 60_000, {
        reason: "skipped_next",
      }),
      ev("playback_start", "eve", "s1", "show-B", 0, now - 20_000),
    ]);

    const live = getLiveListeners();
    expect(live).toHaveLength(1);
    expect(live[0].show_id).toBe("show-B");
  });

  it("app_backgrounded ends do not stop the live keepalive (phone in pocket)", () => {
    // ~25% of playback_end events in prod are app_backgrounded — the
    // user locked their phone but audio kept playing. Treating those
    // as real ends dropped them off Live within 2 min; we now ignore
    // them so the preceding playback_start remains the keepalive
    // anchor under the 45-min START window.
    const now = Date.now();
    insertEvents([
      ev("playback_start", "gina", "s1", "show-G", 0, now - 10 * 60_000),
      ev("playback_end", "gina", "s1", "show-G", 0, now - 9 * 60_000, {
        reason: "app_backgrounded",
      }),
    ]);

    const live = getLiveListeners();
    expect(live).toHaveLength(1);
    expect(live[0].iid).toBe("gina");
    expect(live[0].show_id).toBe("show-G");
    // started_at should reflect the original start, not the bg end.
    expect(live[0].started_at).toBe(now - 10 * 60_000);

    // And the same user must NOT also appear in Recent.
    const recent = getRecentListening(24);
    expect(recent.find((r) => r.iid === "gina")).toBeUndefined();
  });

  it("backgrounded user falls off Live once their start ages past 45min", () => {
    const now = Date.now();
    insertEvents([
      ev("playback_start", "harry", "s1", "show-H", 0, now - 50 * 60_000),
      ev("playback_end", "harry", "s1", "show-H", 0, now - 49 * 60_000, {
        reason: "app_backgrounded",
      }),
    ]);
    expect(getLiveListeners()).toEqual([]);
    const recent = getRecentListening(24);
    expect(recent.find((r) => r.iid === "harry")).toBeDefined();
  });

  it("at most one row per iid even with multiple unmatched starts", () => {
    const now = Date.now();
    insertEvents([
      ev("playback_start", "frank", "s1", "show-A", 0, now - 5 * 60_000),
      ev("playback_start", "frank", "s2", "show-B", 0, now - 60_000),
    ]);

    const live = getLiveListeners();
    expect(live).toHaveLength(1);
    expect(live[0].show_id).toBe("show-B");
  });
});

describe("getRecentListening excludes live listeners under the new boundary", () => {
  it("between-tracks user appears in Live, NOT Recent", () => {
    // The same scenario that proves the live fix shouldn't also leak
    // the user into Recent Listening.
    const now = Date.now();
    insertEvents([
      ev("playback_start", "alice", "s1", "show-A", 0, now - 60_000),
      ev("playback_end", "alice", "s1", "show-A", 0, now - 30_000, {
        reason: "completed",
      }),
    ]);

    const live = getLiveListeners();
    const recent = getRecentListening(24);
    expect(live.map((l) => l.iid)).toEqual(["alice"]);
    expect(recent.find((r) => r.iid === "alice" && r.show_id === "show-A")).toBeUndefined();
  });

  it("a user 5 min past their last end appears in Recent, not Live", () => {
    const now = Date.now();
    insertEvents([
      ev("playback_start", "bob", "s1", "show-B", 0, now - 10 * 60_000),
      ev("playback_end", "bob", "s1", "show-B", 0, now - 5 * 60_000, {
        reason: "skipped_next",
      }),
    ]);

    expect(getLiveListeners()).toEqual([]);
    const recent = getRecentListening(24);
    expect(recent.find((r) => r.iid === "bob")).toBeDefined();
  });

  it("a user listening to show B (live) is excluded from B but still appears in Recent for past show A", () => {
    const now = Date.now();
    insertEvents([
      // Past listen to show A, fully ended 1 hour ago.
      ev("playback_start", "carol", "s1", "show-A", 0, now - 2 * 3600_000),
      ev("playback_end", "carol", "s1", "show-A", 0, now - 3600_000, {
        reason: "completed",
      }),
      // Current live session on show B.
      ev("playback_start", "carol", "s2", "show-B", 0, now - 10_000),
    ]);

    const live = getLiveListeners();
    expect(live).toHaveLength(1);
    expect(live[0].show_id).toBe("show-B");

    const recent = getRecentListening(24);
    const carolRecents = recent.filter((r) => r.iid === "carol");
    // show-A appears in recent (past), show-B does not (live).
    expect(carolRecents.map((r) => r.show_id).sort()).toEqual(["show-A"]);
  });
});
