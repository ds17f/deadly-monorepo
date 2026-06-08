import { describe, it, expect, beforeAll, beforeEach } from "vitest";
import os from "node:os";
import path from "node:path";
import crypto from "node:crypto";

// Point the users-db singleton at a throwaway file BEFORE importing the module.
process.env.USERS_DB_PATH = path.join(os.tmpdir(), `notif-test-${crypto.randomUUID()}.db`);

const { getUsersDb } = await import("../users.js");
const {
  createNotification,
  getNotificationsSince,
  getActiveNotifications,
  getLatestNotificationId,
  deleteNotification,
} = await import("../notifications.js");

const AUTHOR = "author-1";

beforeAll(() => {
  const db = getUsersDb();
  db.prepare(
    `INSERT OR IGNORE INTO accounts (id, email, provider) VALUES (?, ?, 'test')`,
  ).run(AUTHOR, "author@test.dev");
});

beforeEach(() => {
  // Clean slate between tests; reset the AUTOINCREMENT counter too.
  const db = getUsersDb();
  db.exec(`DELETE FROM notifications`);
  db.exec(`DELETE FROM sqlite_sequence WHERE name = 'notifications'`);
});

describe("notifications targeting/category round-trip", () => {
  it("defaults category=general and null targeting when omitted", () => {
    createNotification({ authorId: AUTHOR, title: "Hi", body: "there" });
    const [msg] = getActiveNotifications();
    expect(msg.category).toBe("general");
    expect(msg.min_version).toBeNull();
    expect(msg.max_version).toBeNull();
    expect(msg.platforms).toBeNull();
  });

  it("persists category, version range, and platforms", () => {
    createNotification({
      authorId: AUTHOR,
      title: "v2.4 is live",
      body: "Update now",
      category: "release",
      maxVersion: "2.3.9",
      platforms: ["ios", "android"],
    });
    const [msg] = getActiveNotifications();
    expect(msg.category).toBe("release");
    expect(msg.max_version).toBe("2.3.9");
    expect(msg.platforms).toEqual(["ios", "android"]);
  });

  it("drops unknown platforms and empty arrays to null", () => {
    createNotification({
      authorId: AUTHOR,
      title: "x",
      body: "y",
      // @ts-expect-error — exercising the runtime filter with a bad value
      platforms: ["ios", "nintendo"],
    });
    const [msg] = getActiveNotifications();
    expect(msg.platforms).toEqual(["ios"]);
  });

  it("delta fetch carries the same targeting fields", () => {
    const a = createNotification({ authorId: AUTHOR, title: "a", body: "a" });
    createNotification({
      authorId: AUTHOR,
      title: "b",
      body: "b",
      category: "outage",
      minVersion: "2.0.0",
      platforms: ["web"],
    });
    const delta = getNotificationsSince(a.id);
    expect(delta).toHaveLength(1);
    expect(delta[0].title).toBe("b");
    expect(delta[0].category).toBe("outage");
    expect(delta[0].min_version).toBe("2.0.0");
    expect(delta[0].platforms).toEqual(["web"]);
  });
});

describe("latest-id polling short-circuit", () => {
  it("tracks the newest live id and reflects publishes/retires", () => {
    // Each mutation flows through the module, which keeps the in-memory
    // high-water id warm (production's only writers are POST/retire).
    const a = createNotification({ authorId: AUTHOR, title: "a", body: "a" });
    expect(getLatestNotificationId()).toBe(a.id);

    const b = createNotification({ authorId: AUTHOR, title: "b", body: "b" });
    expect(getLatestNotificationId()).toBe(b.id);

    // Retiring the newest live message drops the high-water mark back to a.
    deleteNotification(b.id);
    expect(getLatestNotificationId()).toBe(a.id);
  });

  it("a caught-up cursor equals the latest id (caller can skip the query)", () => {
    const a = createNotification({ authorId: AUTHOR, title: "a", body: "a" });
    expect(a.id).toBe(getLatestNotificationId());
    // Nothing newer than the latest id.
    expect(getNotificationsSince(getLatestNotificationId())).toHaveLength(0);
  });
});
