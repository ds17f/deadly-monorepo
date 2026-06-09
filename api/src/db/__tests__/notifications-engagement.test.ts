import { describe, it, expect, beforeEach, afterAll } from "vitest";
import path from "node:path";
import fs from "node:fs";
import os from "node:os";

// Point the analytics module at a temp DB before importing it so its
// module-level singleton opens our test file, not data/analytics.db.
const TMP_DB = path.join(
  os.tmpdir(),
  `notif-engagement-test-${process.pid}-${Date.now()}.db`,
);
process.env.ANALYTICS_DB_PATH = TMP_DB;

const { getAnalyticsDb, insertEvents, getNotificationEngagement, closeAnalyticsDb } =
  await import("../analytics.js");

function evt(
  event: string,
  iid: string,
  props: Record<string, unknown>,
  platform = "ios",
) {
  return {
    event,
    ts: Date.now(),
    iid,
    sid: `${iid}-s`,
    platform,
    app_version: "2.34.0",
    props,
  };
}

beforeEach(() => {
  getAnalyticsDb().exec(`DELETE FROM analytics_events;`);
});

afterAll(() => {
  closeAnalyticsDb();
  if (fs.existsSync(TMP_DB)) fs.unlinkSync(TMP_DB);
});

describe("getNotificationEngagement", () => {
  it("counts distinct installs per notification id across the funnel", () => {
    insertEvents([
      // Notification 1: delivered to 2 clients, both displayed, one opened,
      // one archived, one link click. Duplicate received from iid-a must not
      // double-count delivered.
      evt("notification_received", "iid-a", { id: 1, category: "release", level: "info", reason: "cold_start" }),
      evt("notification_received", "iid-a", { id: 1, category: "release", level: "info", reason: "foreground" }),
      evt("notification_received", "iid-b", { id: 1, category: "release", level: "info", reason: "cold_start" }),
      evt("notification_impression", "iid-a", { id: 1, category: "release", level: "info" }),
      evt("notification_impression", "iid-b", { id: 1, category: "release", level: "info" }),
      evt("notification_open", "iid-a", { id: 1, category: "release", level: "info", was_unread: true }),
      evt("notification_archive", "iid-b", { id: 1, category: "release" }),
      evt("notification_link_tap", "iid-a", { id: 1, url: "https://example.com" }),

      // Notification 2: delivered to 1 client; "displayed" unions impression
      // and toast_shown, but the same iid seeing both counts once.
      evt("notification_received", "iid-c", { id: 2, category: "feature", level: "info", reason: "refresh" }),
      evt("notification_impression", "iid-c", { id: 2, category: "feature", level: "info" }),
      evt("notification_toast_shown", "iid-c", { id: 2, count: 1 }),
    ]);

    const rows = getNotificationEngagement();
    const byId = Object.fromEntries(rows.map((r) => [r.id, r]));

    expect(byId[1]).toMatchObject({
      delivered: 2,
      displayed: 2,
      opened: 1,
      archived: 1,
      link_clicks: 1,
    });
    expect(byId[2]).toMatchObject({
      delivered: 1,
      displayed: 1, // impression + toast_shown from the same install = 1
      opened: 0,
      archived: 0,
      link_clicks: 0,
    });
  });

  it("honors the platform filter", () => {
    insertEvents([
      evt("notification_received", "ios-1", { id: 5, category: "general", level: "info", reason: "cold_start" }, "ios"),
      evt("notification_received", "web-1", { id: 5, category: "general", level: "info", reason: "cold_start" }, "web"),
    ]);

    expect(getNotificationEngagement(90)[0].delivered).toBe(2);
    expect(getNotificationEngagement(90, ["ios"])[0].delivered).toBe(1);
  });
});
