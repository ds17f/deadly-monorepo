import { describe, it, expect, beforeEach, afterAll } from "vitest";
import path from "node:path";
import fs from "node:fs";
import os from "node:os";

const TMP_DB = path.join(
  os.tmpdir(),
  `popular-test-${process.pid}-${Date.now()}.db`,
);
process.env.ANALYTICS_DB_PATH = TMP_DB;

const {
  getAnalyticsDb,
  insertEvents,
  getPopularShows,
  closeAnalyticsDb,
} = await import("../analytics.js");

function favAction(
  iid: string,
  showId: string,
  feature: "add_favorite" | "remove_favorite",
  ts: number,
  targetType: "show" | "recording_track" | null = "show",
) {
  return {
    event: "feature_use",
    ts,
    iid,
    sid: `${iid}-s1`,
    platform: "ios",
    app_version: "2.30.0",
    props: {
      feature,
      category: "action",
      target_type: targetType,
      target_id: showId,
    },
  };
}

function play(iid: string, showId: string, ts: number, trackIndex = 0) {
  return {
    event: "playback_start",
    ts,
    iid,
    sid: `${iid}-s1`,
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

describe("getPopularShows", () => {
  it("ranks by favorites/listens ratio; high-ratio show beats high-play show", () => {
    const t = Date.now() - 86400_000;
    insertEvents([
      // show-A: 2 distinct favorites, 1 listen → ratio 2.0
      favAction("alice", "show-A", "add_favorite", t),
      favAction("bob", "show-A", "add_favorite", t),
      play("alice", "show-A", t + 100),
      // show-B: 2 favorites, 10 listens → ratio 0.2
      favAction("carol", "show-B", "add_favorite", t),
      favAction("dave", "show-B", "add_favorite", t),
      ...Array.from({ length: 10 }, (_, i) =>
        play(`listener${i}`, "show-B", t + i * 1000),
      ),
    ]);

    const ids = getPopularShows(10).shows.map((s) => s.show_id);
    expect(ids[0]).toBe("show-A");
    expect(ids[1]).toBe("show-B");
  });

  it("nets out add-then-remove favorites (last action wins)", () => {
    const t = Date.now() - 86400_000;
    insertEvents([
      // alice: add then remove → net 0
      favAction("alice", "show-X", "add_favorite", t),
      favAction("alice", "show-X", "remove_favorite", t + 1000),
      // bob: add only → net 1
      favAction("bob", "show-X", "add_favorite", t),
      // carol: remove then re-add → net 1
      favAction("carol", "show-X", "remove_favorite", t),
      favAction("carol", "show-X", "add_favorite", t + 1000),
      // dave: just an add to satisfy the floor
      favAction("dave", "show-X", "add_favorite", t),
    ]);

    const row = getPopularShows(10).shows.find((s) => s.show_id === "show-X");
    expect(row).toBeDefined();
    expect(row!.favorites).toBe(3); // bob, carol, dave (not alice)
  });

  it("sorts higher-favorites shows above single-favorite shows", () => {
    const t = Date.now() - 86400_000;
    // ts values must differ for the same install — the analytics_events
    // unique index is (iid, sid, event, ts), so two same-ts events from
    // alice would dedupe to one.
    insertEvents([
      // show-Y: 1 favorite (recently added)
      favAction("alice", "show-Y", "add_favorite", t),
      // show-Z: 2 favorites
      favAction("alice", "show-Z", "add_favorite", t + 1000),
      favAction("bob", "show-Z", "add_favorite", t),
    ]);

    const ids = getPopularShows(10).shows.map((s) => s.show_id);
    // Both surface at floor=1, but the 2-favorite show is pinned first.
    expect(ids[0]).toBe("show-Z");
    expect(ids).toContain("show-Y");
  });

  it("orders single-favorite shows by recency of the last favorite action", () => {
    // Among shows tied on favorites count, the recency tiebreaker keeps
    // the rail from feeling static — the most recently favorited shows
    // float toward the top.
    const t = Date.now() - 86400_000;
    insertEvents([
      favAction("alice", "show-old", "add_favorite", t),
      favAction("bob", "show-recent", "add_favorite", t + 60_000),
      favAction("carol", "show-newest", "add_favorite", t + 120_000),
    ]);

    const ids = getPopularShows(10).shows.map((s) => s.show_id);
    expect(ids).toEqual(["show-newest", "show-recent", "show-old"]);
  });

  it("ignores legacy NULL-target_type favorites (unrecoverable)", () => {
    const t = Date.now() - 86400_000;
    insertEvents([
      favAction("alice", "show-Legacy", "add_favorite", t, null),
      favAction("bob", "show-Legacy", "add_favorite", t, null),
    ]);

    expect(getPopularShows(10).shows).toEqual([]);
  });

  it("ignores recording_track favorites (Unit 3 territory)", () => {
    const t = Date.now() - 86400_000;
    insertEvents([
      favAction("alice", "gd1977-05-08.../03", "add_favorite", t, "recording_track"),
      favAction("bob", "gd1977-05-08.../03", "add_favorite", t, "recording_track"),
    ]);

    expect(getPopularShows(10).shows).toEqual([]);
  });

  it("counts logical listens (not raw playback_start) per show", () => {
    // Same listener firing 5 starts on the same show within an hour =
    // 1 logical listen. The rail's denominator should reflect that.
    const t = Date.now() - 86400_000;
    insertEvents([
      favAction("alice", "show-L", "add_favorite", t),
      favAction("bob", "show-L", "add_favorite", t),
      // alice listens to 5 tracks of show-L back-to-back
      play("alice", "show-L", t + 100, 0),
      play("alice", "show-L", t + 200, 1),
      play("alice", "show-L", t + 300, 2),
      play("alice", "show-L", t + 400, 3),
      play("alice", "show-L", t + 500, 4),
    ]);

    const row = getPopularShows(10).shows.find((s) => s.show_id === "show-L");
    expect(row!.listens).toBe(1);
    expect(row!.favorites).toBe(2);
    expect(row!.ratio).toBe(2);
  });
});
