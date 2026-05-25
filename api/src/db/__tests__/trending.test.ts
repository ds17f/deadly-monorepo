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
  rebuildShowListensRollup,
  getTrending,
  closeAnalyticsDb,
} = await import("../analytics.js");

const HOUR_MS = 3600 * 1000;
const DAY_MS = 24 * HOUR_MS;

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
  const db = getAnalyticsDb();
  db.exec(`DELETE FROM analytics_events; DELETE FROM show_listens_rollup;`);
});

afterAll(() => {
  closeAnalyticsDb();
  if (fs.existsSync(TMP_DB)) fs.unlinkSync(TMP_DB);
});

describe("logical-listen aggregation", () => {
  it("5 restarts of the same show within 6h = 1 listen", () => {
    // The bug ADR-0004 was rewritten to fix: same listener restarting the
    // same show across app sessions used to count as N listens.
    const t = Date.now() - HOUR_MS;
    insertEvents([
      play("alice", "s1", "show-A", t),
      play("alice", "s2", "show-A", t + 5 * 60_000), // 5 min later, new sid
      play("alice", "s3", "show-A", t + 10 * 60_000),
      play("alice", "s4", "show-A", t + 20 * 60_000),
      play("alice", "s5", "show-A", t + 30 * 60_000),
    ]);
    rebuildShowListensRollup();

    const result = getTrending(10);
    const row = result.windows.now.find((r) => r.show_id === "show-A");
    expect(row).toBeDefined();
    expect(row!.listens).toBe(1);
    expect(row!.plays).toBe(5);
    expect(row!.installs).toBe(1);
  });

  it("plays on the same show separated by > 6h = separate listens", () => {
    const t = Date.now() - 12 * HOUR_MS;
    insertEvents([
      play("alice", "s1", "show-A", t),
      play("alice", "s2", "show-A", t + 7 * HOUR_MS), // 7h later
    ]);
    rebuildShowListensRollup();

    const row = getTrending(10).windows.now.find((r) => r.show_id === "show-A");
    expect(row!.listens).toBe(2);
    expect(row!.plays).toBe(2);
    expect(row!.installs).toBe(1);
  });

  it("interleaved A → B → A breaks the A listen", () => {
    const t = Date.now() - HOUR_MS;
    insertEvents([
      play("alice", "s1", "show-A", t),
      play("alice", "s1", "show-B", t + 60_000),
      play("alice", "s1", "show-A", t + 120_000),
    ]);
    rebuildShowListensRollup();

    const a = getTrending(10).windows.now.find((r) => r.show_id === "show-A")!;
    const b = getTrending(10).windows.now.find((r) => r.show_id === "show-B")!;
    expect(a.listens).toBe(2); // started, interrupted by B, resumed = new listen
    expect(a.plays).toBe(2);
    expect(b.listens).toBe(1);
  });

  it("distinct installs each get their own first-listen credit", () => {
    const t = Date.now() - HOUR_MS;
    insertEvents([
      play("alice", "s1", "show-A", t),
      play("bob", "s2", "show-A", t + 60_000),
      play("carol", "s3", "show-A", t + 120_000),
    ]);
    rebuildShowListensRollup();

    const row = getTrending(10).windows.now.find((r) => r.show_id === "show-A")!;
    expect(row.listens).toBe(3);
    expect(row.plays).toBe(3);
    expect(row.installs).toBe(3);
  });

  it("deep listen (many tracks, one sitting) counts as 1 listen", () => {
    // The other half of the bias problem: a 22-track listen-through
    // shouldn't outweigh 3 different people each playing one track.
    const t = Date.now() - HOUR_MS;
    const deepListen = Array.from({ length: 22 }, (_, i) =>
      play("alice", "s1", "show-A", t + i * 60_000, i),
    );
    const threeLightListens = [
      play("bob", "s2", "show-B", t),
      play("carol", "s3", "show-B", t + 60_000),
      play("dave", "s4", "show-B", t + 120_000),
    ];
    insertEvents([...deepListen, ...threeLightListens]);
    rebuildShowListensRollup();

    const a = getTrending(10).windows.now.find((r) => r.show_id === "show-A")!;
    const b = getTrending(10).windows.now.find((r) => r.show_id === "show-B")!;
    expect(a.listens).toBe(1);
    expect(b.listens).toBe(3);
    // B beats A on the primary signal even though A has way more plays.
    const order = getTrending(10).windows.now.map((r) => r.show_id);
    expect(order.indexOf("show-B")).toBeLessThan(order.indexOf("show-A"));
  });
});

describe("getTrending window correctness", () => {
  it("populates all four windows; out-of-window shows are absent", () => {
    const now = Date.now();
    insertEvents([
      play("u1", "s1", "show-now", now - HOUR_MS),
      play("u1", "s2", "show-week", now - 3 * DAY_MS),
      play("u1", "s3", "show-month", now - 20 * DAY_MS),
      play("u1", "s4", "show-old", now - 60 * DAY_MS),
    ]);
    rebuildShowListensRollup();

    const r = getTrending(10);
    const ids = (rows: { show_id: string }[]) => rows.map((x) => x.show_id);

    expect(ids(r.windows.now)).toEqual(["show-now"]);
    expect(ids(r.windows.week).sort()).toEqual(["show-now", "show-week"]);
    expect(ids(r.windows.month).sort()).toEqual([
      "show-month",
      "show-now",
      "show-week",
    ]);
    expect(ids(r.windows.all).sort()).toEqual([
      "show-month",
      "show-now",
      "show-old",
      "show-week",
    ]);
  });

  it("orders by listens DESC, plays DESC tiebreaker", () => {
    const t = Date.now() - HOUR_MS;
    insertEvents([
      // show-X: 1 listen with many plays
      ...Array.from({ length: 5 }, (_, i) =>
        play("u1", "s1", "show-X", t + i * 60_000, i),
      ),
      // show-Y: 2 listens (2 different installs)
      play("u2", "s2", "show-Y", t),
      play("u3", "s3", "show-Y", t + 60_000),
    ]);
    rebuildShowListensRollup();

    const order = getTrending(10).windows.now.map((r) => r.show_id);
    expect(order[0]).toBe("show-Y"); // 2 listens > 1 listen
    expect(order[1]).toBe("show-X");
  });

  it("respects the limit parameter", () => {
    const t = Date.now() - HOUR_MS;
    const events = Array.from({ length: 15 }, (_, i) =>
      play(`u${i}`, `s${i}`, `show-${i}`, t + i),
    );
    insertEvents(events);
    rebuildShowListensRollup();

    const result = getTrending(5);
    expect(result.windows.now).toHaveLength(5);
    expect(result.windows.all).toHaveLength(5);
  });

  it("empty events → empty windows, valid timestamp", () => {
    rebuildShowListensRollup();
    const result = getTrending(10);
    expect(result.windows.now).toEqual([]);
    expect(result.windows.week).toEqual([]);
    expect(result.windows.month).toEqual([]);
    expect(result.windows.all).toEqual([]);
    expect(typeof result.generated_at).toBe("string");
  });

  it("rebuild is idempotent — running twice yields the same rollup", () => {
    const t = Date.now() - HOUR_MS;
    insertEvents([play("u1", "s1", "show-A", t)]);

    rebuildShowListensRollup();
    const first = getTrending(10);
    rebuildShowListensRollup();
    const second = getTrending(10);

    expect(first.windows.now).toEqual(second.windows.now);
    expect(first.windows.all).toEqual(second.windows.all);
  });

  it("ignores events without a show_id", () => {
    const t = Date.now() - HOUR_MS;
    getAnalyticsDb()
      .prepare(
        `INSERT INTO analytics_events (event, ts, iid, sid, platform, app_version, props)
         VALUES ('playback_start', ?, 'a', 's', 'ios', '2.30.0', NULL)`,
      )
      .run(t);
    rebuildShowListensRollup();
    expect(getTrending(10).windows.all).toEqual([]);
  });
});
