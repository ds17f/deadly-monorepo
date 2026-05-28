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

// Pin to a single rotation window for deterministic shuffles. Picked
// arbitrarily — any fixed instant works.
const FIXED_NOW = Date.parse("2026-05-27T12:00:00Z");
const ROTATION_HOURS = 4;
const BUCKET_MS = ROTATION_HOURS * 3600 * 1000;

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

/** Build a show_id whose decade matches. */
function showId(year: number, slug = "01"): string {
  return `gd${year}-06-${slug}.sbd.miller`;
}

/** Seed `count` shows in a single decade with `favsPerShow` distinct favoriters. */
function seedDecade(
  decadeYears: number[],
  favsPerShow: number,
  baseTs: number,
) {
  const events: ReturnType<typeof favAction>[] = [];
  let ev = 0;
  for (const year of decadeYears) {
    for (let f = 0; f < favsPerShow; f++) {
      events.push(
        favAction(
          `user-${year}-${f}`,
          showId(year, String(year).slice(-2)),
          "add_favorite",
          baseTs + ev++,
        ),
      );
    }
  }
  return events;
}

beforeEach(() => {
  const db = getAnalyticsDb();
  db.exec(`DELETE FROM analytics_events; DELETE FROM show_listens_rollup;`);
});

afterAll(() => {
  closeAnalyticsDb();
  if (fs.existsSync(TMP_DB)) fs.unlinkSync(TMP_DB);
});

describe("getPopularShows — per-decade pools", () => {
  it("buckets slug-format show_ids (no `gd` prefix) — the prod format", () => {
    const t = FIXED_NOW - 86400_000;
    insertEvents([
      favAction("alice", "1977-05-11-st-paul-civic-center-st-paul-mn-usa", "add_favorite", t),
      favAction("bob",   "1977-05-11-st-paul-civic-center-st-paul-mn-usa", "add_favorite", t + 1),
      favAction("carol", "1969-04-05-avalon-ballroom-san-francisco-ca-usa", "add_favorite", t + 2),
      favAction("dave",  "1969-04-05-avalon-ballroom-san-francisco-ca-usa", "add_favorite", t + 3),
    ]);

    const { decades } = getPopularShows({
      nowMs: FIXED_NOW,
      minFavorites: 2,
      perDecade: 8,
      rotationHours: ROTATION_HOURS,
    });

    expect(decades["70s"].map((s) => s.show_id)).toContain(
      "1977-05-11-st-paul-civic-center-st-paul-mn-usa",
    );
    expect(decades["60s"].map((s) => s.show_id)).toContain(
      "1969-04-05-avalon-ballroom-san-francisco-ca-usa",
    );
  });

  it("buckets shows into 60s/70s/80s/90s by show_id year", () => {
    const t = FIXED_NOW - 86400_000;
    insertEvents([
      ...seedDecade([1968], 2, t),
      ...seedDecade([1977], 2, t + 1000),
      ...seedDecade([1985], 2, t + 2000),
      ...seedDecade([1993], 2, t + 3000),
    ]);

    const { decades } = getPopularShows({
      nowMs: FIXED_NOW,
      minFavorites: 2,
      perDecade: 8,
      rotationHours: ROTATION_HOURS,
    });

    expect(decades["60s"].map((s) => s.show_id)).toEqual([showId(1968, "68")]);
    expect(decades["70s"].map((s) => s.show_id)).toEqual([showId(1977, "77")]);
    expect(decades["80s"].map((s) => s.show_id)).toEqual([showId(1985, "85")]);
    expect(decades["90s"].map((s) => s.show_id)).toEqual([showId(1993, "93")]);
    // Response no longer carries an `all` bucket — the client computes it.
    expect("all" in decades).toBe(false);
  });

  it("enforces the minFavorites floor server-side", () => {
    const t = FIXED_NOW - 86400_000;
    insertEvents([
      // One favoriter — should be filtered when floor=2
      favAction("alice", showId(1977, "77"), "add_favorite", t),
      // Two favoriters — passes the floor
      favAction("alice", showId(1978, "78"), "add_favorite", t + 1),
      favAction("bob",   showId(1978, "78"), "add_favorite", t + 2),
    ]);

    const { decades } = getPopularShows({
      nowMs: FIXED_NOW,
      minFavorites: 2,
      perDecade: 8,
      rotationHours: ROTATION_HOURS,
    });

    expect(decades["70s"].map((s) => s.show_id)).toEqual([showId(1978, "78")]);
  });

  it("caps each decade pool at perDecade", () => {
    const t = FIXED_NOW - 86400_000;
    // 10 shows in the 70s, all qualifying
    const years = [1970, 1971, 1972, 1973, 1974, 1975, 1976, 1977, 1978, 1979];
    insertEvents(seedDecade(years, 2, t));

    const { decades } = getPopularShows({
      nowMs: FIXED_NOW,
      minFavorites: 2,
      perDecade: 5,
      rotationHours: ROTATION_HOURS,
    });

    expect(decades["70s"]).toHaveLength(5);
    // Pool order is shuffled (client picks + sorts); we don't assert order.
  });

  it("returns the same sample within a rotation window", () => {
    const t = FIXED_NOW - 86400_000;
    const years = Array.from({ length: 20 }, (_, i) => 1970 + (i % 10));
    insertEvents(
      years.flatMap((y, i) => [
        favAction(`a${i}`, showId(y, String(i).padStart(2, "0")), "add_favorite", t + i * 2),
        favAction(`b${i}`, showId(y, String(i).padStart(2, "0")), "add_favorite", t + i * 2 + 1),
      ]),
    );

    const a = getPopularShows({
      nowMs: FIXED_NOW,
      minFavorites: 2,
      perDecade: 5,
      rotationHours: ROTATION_HOURS,
    });
    const b = getPopularShows({
      nowMs: FIXED_NOW + BUCKET_MS - 1, // same bucket
      minFavorites: 2,
      perDecade: 5,
      rotationHours: ROTATION_HOURS,
    });

    expect(b.decades["70s"].map((s) => s.show_id)).toEqual(
      a.decades["70s"].map((s) => s.show_id),
    );
  });

  it("returns a different sample once the rotation window rolls", () => {
    const t = FIXED_NOW - 86400_000;
    // Enough shows that the shuffled top-N is very unlikely to coincide
    // across two seeds by accident.
    const events: ReturnType<typeof favAction>[] = [];
    for (let i = 0; i < 30; i++) {
      const year = 1970 + (i % 10);
      const id = showId(year, String(i).padStart(2, "0"));
      events.push(favAction(`a${i}`, id, "add_favorite", t + i * 2));
      events.push(favAction(`b${i}`, id, "add_favorite", t + i * 2 + 1));
    }
    insertEvents(events);

    const a = getPopularShows({
      nowMs: FIXED_NOW,
      minFavorites: 2,
      perDecade: 5,
      rotationHours: ROTATION_HOURS,
    });
    const b = getPopularShows({
      nowMs: FIXED_NOW + BUCKET_MS, // next bucket
      minFavorites: 2,
      perDecade: 5,
      rotationHours: ROTATION_HOURS,
    });

    const sampleA = a.decades["70s"].map((s) => s.show_id);
    const sampleB = b.decades["70s"].map((s) => s.show_id);
    expect(sampleB).not.toEqual(sampleA);
  });

  it("returns empty pools for decades with no qualifying shows", () => {
    const t = FIXED_NOW - 86400_000;
    insertEvents([
      ...seedDecade([1977], 2, t),
      ...seedDecade([1985], 2, t + 100),
    ]);

    const { decades } = getPopularShows({
      nowMs: FIXED_NOW,
      minFavorites: 2,
      perDecade: 8,
      rotationHours: ROTATION_HOURS,
    });

    expect(decades["70s"]).toHaveLength(1);
    expect(decades["80s"]).toHaveLength(1);
    expect(decades["60s"]).toEqual([]);
    expect(decades["90s"]).toEqual([]);
  });

  it("nets out add-then-remove favorites (last action wins)", () => {
    const t = FIXED_NOW - 86400_000;
    const id = showId(1977, "77");
    insertEvents([
      // alice: add then remove → net 0
      favAction("alice", id, "add_favorite", t),
      favAction("alice", id, "remove_favorite", t + 1000),
      // bob: add only → net 1
      favAction("bob", id, "add_favorite", t),
      // carol: remove then re-add → net 1
      favAction("carol", id, "remove_favorite", t),
      favAction("carol", id, "add_favorite", t + 1000),
    ]);

    const { decades } = getPopularShows({
      nowMs: FIXED_NOW,
      minFavorites: 2,
      perDecade: 8,
      rotationHours: ROTATION_HOURS,
    });
    const row = decades["70s"].find((s) => s.show_id === id);
    expect(row).toBeDefined();
    expect(row!.favorites).toBe(2); // bob, carol (not alice)
  });

  it("ignores legacy NULL-target_type favorites and recording_track favorites", () => {
    const t = FIXED_NOW - 86400_000;
    insertEvents([
      favAction("alice", showId(1977, "77"), "add_favorite", t, null),
      favAction("bob",   showId(1977, "77"), "add_favorite", t + 1, null),
      favAction("alice", "gd1977-05-08.../03", "add_favorite", t + 2, "recording_track"),
      favAction("bob",   "gd1977-05-08.../03", "add_favorite", t + 3, "recording_track"),
    ]);

    const { decades } = getPopularShows({
      nowMs: FIXED_NOW,
      minFavorites: 2,
      perDecade: 8,
      rotationHours: ROTATION_HOURS,
    });
    expect(decades["70s"]).toEqual([]);
  });

  it("counts logical listens (not raw playback_start) per show", () => {
    const t = FIXED_NOW - 86400_000;
    const id = showId(1977, "77");
    insertEvents([
      favAction("alice", id, "add_favorite", t),
      favAction("bob",   id, "add_favorite", t + 1),
      // alice listens to 5 tracks of the show back-to-back
      play("alice", id, t + 100, 0),
      play("alice", id, t + 200, 1),
      play("alice", id, t + 300, 2),
      play("alice", id, t + 400, 3),
      play("alice", id, t + 500, 4),
    ]);

    const { decades } = getPopularShows({
      nowMs: FIXED_NOW,
      minFavorites: 2,
      perDecade: 8,
      rotationHours: ROTATION_HOURS,
    });
    const row = decades["70s"].find((s) => s.show_id === id);
    expect(row!.listens).toBe(1);
    expect(row!.favorites).toBe(2);
    expect(row!.ratio).toBe(2);
  });
});
