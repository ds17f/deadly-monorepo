import { describe, it, expect, beforeAll, beforeEach } from "vitest";
import os from "node:os";
import path from "node:path";
import crypto from "node:crypto";

// Point the users-db singleton at a throwaway file BEFORE importing the module.
process.env.USERS_DB_PATH = path.join(os.tmpdir(), `notif-state-test-${crypto.randomUUID()}.db`);

const { getUsersDb } = await import("../users.js");
const { createNotification, deleteNotification } = await import("../notifications.js");
const {
  getNotificationState,
  upsertNotificationState,
  upsertNotificationStates,
  getFullBackupV3,
  importFullBackupV3,
} = await import("../userdata.js");

const AUTHOR = "author-1";
const USER = "user-1";

beforeAll(() => {
  const db = getUsersDb();
  db.prepare(`INSERT OR IGNORE INTO accounts (id, email, provider) VALUES (?, ?, 'test')`)
    .run(AUTHOR, "author@test.dev");
  db.prepare(`INSERT OR IGNORE INTO accounts (id, email, provider) VALUES (?, ?, 'test')`)
    .run(USER, "user@test.dev");
});

beforeEach(() => {
  const db = getUsersDb();
  db.exec(`DELETE FROM notification_state`);
  db.exec(`DELETE FROM notifications`);
  db.exec(`DELETE FROM sqlite_sequence WHERE name = 'notifications'`);
});

function publish(): number {
  return createNotification({ authorId: AUTHOR, title: "t", body: "b" }).id;
}

describe("notification_state union merge (ADR-0015)", () => {
  it("stores seen and dismissed independently for a user", () => {
    const id = publish();
    upsertNotificationState(USER, { notificationId: id, seenAt: 100, updatedAt: 0 });
    const [row] = getNotificationState(USER);
    expect(row.notificationId).toBe(id);
    expect(row.seenAt).toBe(100);
    expect(row.dismissedAt).toBeNull();
  });

  it("a later write fills the other column without clobbering the first", () => {
    const id = publish();
    upsertNotificationState(USER, { notificationId: id, seenAt: 100, updatedAt: 0 });
    upsertNotificationState(USER, { notificationId: id, dismissedAt: 200, updatedAt: 0 });
    const [row] = getNotificationState(USER);
    expect(row.seenAt).toBe(100);
    expect(row.dismissedAt).toBe(200);
  });

  it("keeps the EARLIEST non-null timestamp per column (union, not last-write)", () => {
    const id = publish();
    // Device B read it later...
    upsertNotificationState(USER, { notificationId: id, seenAt: 500, updatedAt: 0 });
    // ...device A read it earlier; the earlier instant wins.
    upsertNotificationState(USER, { notificationId: id, seenAt: 100, updatedAt: 0 });
    expect(getNotificationState(USER)[0].seenAt).toBe(100);

    // A later write never resurrects/overwrites an existing earlier value.
    upsertNotificationState(USER, { notificationId: id, seenAt: 900, updatedAt: 0 });
    expect(getNotificationState(USER)[0].seenAt).toBe(100);
  });

  it("a null-only write is a no-op against existing state", () => {
    const id = publish();
    upsertNotificationState(USER, { notificationId: id, seenAt: 100, updatedAt: 0 });
    upsertNotificationState(USER, { notificationId: id, updatedAt: 0 }); // both null
    const [row] = getNotificationState(USER);
    expect(row.seenAt).toBe(100);
    expect(row.dismissedAt).toBeNull();
  });

  it("bulk upsert covers many ids in one shot", () => {
    const ids = [publish(), publish(), publish()];
    upsertNotificationStates(USER, ids.map((notificationId) => ({
      notificationId, seenAt: 300, updatedAt: 0,
    })));
    const rows = getNotificationState(USER);
    expect(rows).toHaveLength(3);
    expect(rows.every((r) => r.seenAt === 300)).toBe(true);
  });

  it("state is per-user", () => {
    const id = publish();
    upsertNotificationState(USER, { notificationId: id, seenAt: 100, updatedAt: 0 });
    expect(getNotificationState("author-1")).toHaveLength(0);
  });

  it("admin unsend cascades away the user's overlay row", () => {
    const id = publish();
    upsertNotificationState(USER, { notificationId: id, seenAt: 100, updatedAt: 0 });
    expect(getNotificationState(USER)).toHaveLength(1);

    // deleteNotification only tombstones (soft delete) — overlay survives.
    deleteNotification(id);
    expect(getNotificationState(USER)).toHaveLength(1);

    // A hard delete (the only thing that triggers the FK CASCADE) reaps it.
    getUsersDb().prepare(`DELETE FROM notifications WHERE id = ?`).run(id);
    expect(getNotificationState(USER)).toHaveLength(0);
  });
});

describe("notification_state in the V3 backup", () => {
  it("round-trips through export/import with union merge", () => {
    const id = publish();
    upsertNotificationState(USER, { notificationId: id, seenAt: 100, updatedAt: 0 });

    const backup = getFullBackupV3(USER);
    expect(backup.notificationState).toEqual([
      { notificationId: id, seenAt: 100, dismissedAt: null, updatedAt: expect.any(Number) },
    ]);

    // Importing an earlier-dismissed view of the same row unions in dismissed.
    importFullBackupV3(USER, {
      ...backup,
      notificationState: [{ notificationId: id, dismissedAt: 50, updatedAt: 0 }],
    });
    const [row] = getNotificationState(USER);
    expect(row.seenAt).toBe(100);
    expect(row.dismissedAt).toBe(50);
  });
});
