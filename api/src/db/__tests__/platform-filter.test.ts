import { describe, it, expect, beforeEach, afterAll } from "vitest";
import path from "node:path";
import fs from "node:fs";
import os from "node:os";

// Point the analytics module at a temp DB before importing it so its
// module-level singleton opens our test file, not data/analytics.db.
const TMP_DB = path.join(
  os.tmpdir(),
  `platform-filter-test-${process.pid}-${Date.now()}.db`,
);
process.env.ANALYTICS_DB_PATH = TMP_DB;

const {
  getAnalyticsDb,
  insertEvents,
  getSummary,
  getTimeseries,
  getDetail,
  getGrowthByPlatform,
  platformClause,
  closeAnalyticsDb,
} = await import("../analytics.js");

const DAY_MS = 24 * 3600 * 1000;

function open(iid: string, platform: string, ts: number) {
  return {
    event: "app_open",
    ts,
    iid,
    sid: `${iid}-s`,
    platform,
    app_version: "2.30.0",
    props: {},
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

describe("platformClause", () => {
  it("is a no-op for absent / empty / all-invalid filters", () => {
    expect(platformClause(undefined)).toBe("");
    expect(platformClause([])).toBe("");
    expect(platformClause(["nonsense"])).toBe("");
  });

  it("is a no-op when every known platform is selected", () => {
    expect(platformClause(["ios", "android", "web"])).toBe("");
  });

  it("builds an IN clause for a subset, dropping unknown tokens", () => {
    expect(platformClause(["ios", "android"])).toBe(
      " AND platform IN ('ios', 'android')",
    );
    expect(platformClause(["web", "bogus"])).toBe(" AND platform IN ('web')");
  });

  it("respects a custom column alias", () => {
    expect(platformClause(["ios"], "s.platform")).toBe(
      " AND s.platform IN ('ios')",
    );
  });
});

describe("install counts exclude web when filtered", () => {
  beforeEach(() => {
    const now = Date.now();
    insertEvents([
      open("ios-1", "ios", now - 1 * DAY_MS),
      open("ios-2", "ios", now - 2 * DAY_MS),
      open("and-1", "android", now - 1 * DAY_MS),
      open("web-1", "web", now - 1 * DAY_MS),
      open("web-2", "web", now - 3 * DAY_MS),
      open("web-3", "web", now - 5 * DAY_MS),
    ]);
  });

  it("getSummary native-only drops web from install + active counts", () => {
    const all = getSummary();
    const native = getSummary(["ios", "android"]);

    expect(all.total_installs).toBe(6);
    expect(native.total_installs).toBe(3); // 2 ios + 1 android, no web

    expect(all.mau).toBe(6);
    expect(native.mau).toBe(3);

    // platform_split is keyed by platform; web disappears entirely when
    // the filter excludes it.
    expect(all.platform_split).toMatchObject({ ios: 2, android: 1, web: 3 });
    expect(native.platform_split.web).toBeUndefined();
    expect(native.platform_split).toMatchObject({ ios: 2, android: 1 });
  });

  it("total == native + web (the filter partitions the install base)", () => {
    const all = getSummary();
    const native = getSummary(["ios", "android"]);
    const web = getSummary(["web"]);
    expect(native.total_installs + web.total_installs).toBe(all.total_installs);
  });

  it("getDetail total_installs returns only native rows when filtered", () => {
    const rows = getDetail("total_installs", undefined, ["ios", "android"]);
    expect(rows).toHaveLength(3);
    expect(rows.every((r) => r.platform !== "web")).toBe(true);
  });

  it("getTimeseries dau honors the platform filter", () => {
    const all = getTimeseries("dau", 7);
    const native = getTimeseries("dau", 7, ["ios", "android"]);
    const sum = (pts: Array<{ value: number }>) =>
      pts.reduce((s, p) => s + p.value, 0);
    expect(sum(all)).toBe(6);
    expect(sum(native)).toBe(3);
  });

  it("getGrowthByPlatform drops web from per-day series and totals", () => {
    const sum = (rows: ReturnType<typeof getGrowthByPlatform>, k: string) =>
      rows.reduce(
        (s, d) =>
          s + (((d as unknown as Record<string, unknown>)[k] as number) ?? 0),
        0,
      );
    const all = getGrowthByPlatform(30);
    const native = getGrowthByPlatform(30, ["ios", "android"]);
    expect(sum(all, "total")).toBe(6);
    expect(sum(native, "total")).toBe(3);
    expect(sum(native, "web")).toBe(0);
  });
});
