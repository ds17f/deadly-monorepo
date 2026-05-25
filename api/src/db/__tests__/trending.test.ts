import { describe, it, expect, beforeEach, afterAll } from "vitest";
import path from "node:path";
import fs from "node:fs";
import os from "node:os";

// Point the analytics module at a temp DB before importing it so its
// module-level singleton opens our test file, not data/analytics.db.
const TMP_DB = path.join(
  os.tmpdir(),
  `trending-test-${process.pid}-${Date.now()}.db`,
);
process.env.ANALYTICS_DB_PATH = TMP_DB;

const {
  getAnalyticsDb,
  insertEvents,
  rollupShowPlaysDay,
  backfillShowPlaysIfEmpty,
  getTrending,
  closeAnalyticsDb,
} = await import("../analytics.js");

function todayUtc(daysOffset = 0): string {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() + daysOffset);
  return d.toISOString().slice(0, 10);
}

function tsForDay(daysAgo: number, hour = 12): number {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() - daysAgo);
  d.setUTCHours(hour, 0, 0, 0);
  return d.getTime();
}

function play(
  iid: string,
  sid: string,
  showId: string,
  ts: number,
  trackIndex = 0,
) {
  return {
    event: "playback_start",
    ts,
    iid,
    sid,
    platform: "ios",
    app_version: "2.30.0",
    props: { show_id: showId, track_index: trackIndex },
  };
}

beforeEach(() => {
  // Truncate between tests so each starts clean. Cheaper than reopening
  // the DB.
  const db = getAnalyticsDb();
  db.exec(`DELETE FROM analytics_events; DELETE FROM show_plays_daily;`);
});

afterAll(() => {
  closeAnalyticsDb();
  if (fs.existsSync(TMP_DB)) fs.unlinkSync(TMP_DB);
});

describe("rollupShowPlaysDay", () => {
  it("groups plays by show and counts sessions and plays", () => {
    const day = todayUtc();
    const ts = tsForDay(0);
    insertEvents([
      // Alice, one session, 3 tracks of show A
      play("alice", "s1", "show-A", ts, 0),
      play("alice", "s1", "show-A", ts + 1, 1),
      play("alice", "s1", "show-A", ts + 2, 2),
      // Bob, one session, 1 track of show A
      play("bob", "s2", "show-A", ts + 3, 0),
      // Alice, second session, 1 track of show B
      play("alice", "s3", "show-B", ts + 4, 0),
    ]);

    rollupShowPlaysDay(day);

    const rows = getAnalyticsDb()
      .prepare(
        `SELECT show_id, sessions, plays FROM show_plays_daily
         WHERE day = ? ORDER BY show_id`,
      )
      .all(day);

    expect(rows).toEqual([
      { show_id: "show-A", sessions: 2, plays: 4 },
      { show_id: "show-B", sessions: 1, plays: 1 },
    ]);
  });

  it("re-running replaces the day's rows (idempotent)", () => {
    const day = todayUtc();
    const ts = tsForDay(0);
    insertEvents([play("alice", "s1", "show-A", ts, 0)]);
    rollupShowPlaysDay(day);
    rollupShowPlaysDay(day);

    const count = (
      getAnalyticsDb()
        .prepare(`SELECT COUNT(*) AS c FROM show_plays_daily WHERE day = ?`)
        .get(day) as { c: number }
    ).c;
    expect(count).toBe(1);
  });

  it("ignores events without a show_id", () => {
    const day = todayUtc();
    const ts = tsForDay(0);
    getAnalyticsDb()
      .prepare(
        `INSERT INTO analytics_events (event, ts, iid, sid, platform, app_version, props)
         VALUES ('playback_start', ?, 'a', 's', 'ios', '2.30.0', NULL)`,
      )
      .run(ts);

    rollupShowPlaysDay(day);

    const rows = getAnalyticsDb()
      .prepare(`SELECT * FROM show_plays_daily WHERE day = ?`)
      .all(day);
    expect(rows).toEqual([]);
  });
});

describe("getTrending", () => {
  it("returns four windows with correct ordering", () => {
    // Build a corpus that differentiates the windows.
    const events = [
      // show-A: heavy today, dominates `now`
      play("u1", "s1", "show-A", tsForDay(0, 10)),
      play("u2", "s2", "show-A", tsForDay(0, 11)),
      play("u3", "s3", "show-A", tsForDay(0, 12)),
      // show-B: spread across the week, dominates `week`
      play("u1", "wk1", "show-B", tsForDay(1)),
      play("u2", "wk2", "show-B", tsForDay(2)),
      play("u3", "wk3", "show-B", tsForDay(3)),
      play("u4", "wk4", "show-B", tsForDay(4)),
      // show-C: only month-old, dominates `month` but not week
      play("u1", "mo1", "show-C", tsForDay(15)),
      play("u2", "mo2", "show-C", tsForDay(20)),
      // show-D: old (out of month), only shows in `all`
      play("u1", "old1", "show-D", tsForDay(60)),
    ];
    insertEvents(events);

    // Roll up every day that has events. The endpoint's rollup job does
    // this for today + yesterday in real life; here we just do all of
    // them so we can assert on every window.
    for (let i = 0; i <= 60; i++) {
      rollupShowPlaysDay(todayUtc(-i));
    }

    const result = getTrending(10);

    expect(result.windows.now[0]?.show_id).toBe("show-A");
    expect(result.windows.now[0]?.sessions).toBe(3);

    const weekIds = result.windows.week.map((r) => r.show_id);
    expect(weekIds[0]).toBe("show-B");
    expect(weekIds).toContain("show-A");
    expect(weekIds).not.toContain("show-D");

    const monthIds = result.windows.month.map((r) => r.show_id);
    expect(monthIds).toContain("show-C");
    expect(monthIds).not.toContain("show-D");

    const allIds = result.windows.all.map((r) => r.show_id);
    expect(allIds).toContain("show-D");
  });

  it("respects the limit parameter", () => {
    const ts = tsForDay(0);
    const events = Array.from({ length: 15 }, (_, i) =>
      play(`u${i}`, `s${i}`, `show-${i}`, ts + i),
    );
    insertEvents(events);
    rollupShowPlaysDay(todayUtc());

    const result = getTrending(5);
    expect(result.windows.now).toHaveLength(5);
    expect(result.windows.all).toHaveLength(5);
  });

  it("orders by sessions desc, then plays desc as tiebreaker", () => {
    const ts = tsForDay(0);
    insertEvents([
      // show-X: 1 session, 5 plays
      play("u1", "s1", "show-X", ts, 0),
      play("u1", "s1", "show-X", ts + 1, 1),
      play("u1", "s1", "show-X", ts + 2, 2),
      play("u1", "s1", "show-X", ts + 3, 3),
      play("u1", "s1", "show-X", ts + 4, 4),
      // show-Y: 2 sessions, 2 plays — should beat X on sessions
      play("u2", "s2", "show-Y", ts, 0),
      play("u3", "s3", "show-Y", ts + 1, 0),
    ]);
    rollupShowPlaysDay(todayUtc());

    const result = getTrending(10);
    expect(result.windows.now[0]?.show_id).toBe("show-Y");
    expect(result.windows.now[1]?.show_id).toBe("show-X");
  });

  it("backfillShowPlaysIfEmpty populates every day with events on first run", () => {
    insertEvents([
      play("u1", "s1", "show-A", tsForDay(0)),
      play("u1", "s2", "show-A", tsForDay(5)),
      play("u2", "s3", "show-B", tsForDay(10)),
    ]);

    const filled = backfillShowPlaysIfEmpty();
    expect(filled).toBe(3); // 3 distinct days

    // Second call should be a no-op.
    expect(backfillShowPlaysIfEmpty()).toBe(0);

    const all = getTrending(10).windows.all.map((r) => r.show_id);
    expect(all).toContain("show-A");
    expect(all).toContain("show-B");
  });

  it("returns empty arrays when there are no events", () => {
    const result = getTrending(10);
    expect(result.windows.now).toEqual([]);
    expect(result.windows.week).toEqual([]);
    expect(result.windows.month).toEqual([]);
    expect(result.windows.all).toEqual([]);
    expect(typeof result.generated_at).toBe("string");
  });
});
