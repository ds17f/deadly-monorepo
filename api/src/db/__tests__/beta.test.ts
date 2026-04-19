import { describe, it, expect, beforeEach, afterEach } from "vitest";
import Database from "better-sqlite3";
import crypto from "node:crypto";

let db: Database.Database;

function initTestSchema(db: Database.Database): void {
  db.exec(`
    CREATE TABLE IF NOT EXISTS beta_applicants (
      id TEXT PRIMARY KEY,
      email TEXT UNIQUE NOT NULL COLLATE NOCASE,
      first_name TEXT,
      last_name TEXT,
      status TEXT NOT NULL,
      asc_invitation_id TEXT,
      asc_user_id TEXT,
      last_error TEXT,
      ip_address TEXT,
      user_agent TEXT,
      created_at INTEGER NOT NULL DEFAULT (unixepoch()),
      invited_at INTEGER,
      member_at INTEGER,
      installed_at INTEGER,
      removed_at INTEGER
    );

    CREATE TABLE IF NOT EXISTS beta_settings (
      key TEXT PRIMARY KEY,
      value TEXT NOT NULL
    );
  `);
  db.exec(`
    INSERT OR IGNORE INTO beta_settings (key, value) VALUES ('auto_approve', 'true');
    INSERT OR IGNORE INTO beta_settings (key, value) VALUES ('slot_cap', '100');
  `);
}

function insertApplicant(
  email: string,
  firstName: string | null,
  lastName: string | null,
  ip: string | null,
  ua: string | null,
) {
  const id = crypto.randomUUID();
  db.prepare(
    `INSERT INTO beta_applicants (id, email, first_name, last_name, status, ip_address, user_agent)
     VALUES (?, ?, ?, ?, 'pending', ?, ?)`,
  ).run(id, email, firstName, lastName, ip, ua);
  return db.prepare(`SELECT * FROM beta_applicants WHERE id = ?`).get(id) as Record<string, unknown>;
}

function listApplicants() {
  return db.prepare(`SELECT * FROM beta_applicants ORDER BY created_at DESC`).all();
}

function getApplicantById(id: string) {
  return db.prepare(`SELECT * FROM beta_applicants WHERE id = ?`).get(id) as Record<string, unknown> | undefined;
}

function getApplicantByEmail(email: string) {
  return db.prepare(`SELECT * FROM beta_applicants WHERE email = ?`).get(email) as Record<string, unknown> | undefined;
}

function updateApplicantStatus(
  id: string,
  status: string,
  fields?: Record<string, unknown>,
): void {
  const sets = ["status = ?"];
  const params: unknown[] = [status];
  if (fields) {
    for (const [key, value] of Object.entries(fields)) {
      sets.push(`${key} = ?`);
      params.push(value);
    }
  }
  params.push(id);
  db.prepare(`UPDATE beta_applicants SET ${sets.join(", ")} WHERE id = ?`).run(...params);
}

function getSettings(): Record<string, string> {
  const rows = db.prepare(`SELECT key, value FROM beta_settings`).all() as { key: string; value: string }[];
  const result: Record<string, string> = {};
  for (const row of rows) result[row.key] = row.value;
  return result;
}

function setSetting(key: string, value: string): void {
  db.prepare(
    `INSERT INTO beta_settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value`,
  ).run(key, value);
}

function countSlotsUsed(): number {
  const row = db.prepare(
    `SELECT COUNT(*) as count FROM beta_applicants WHERE status IN ('invited', 'member', 'installed')`,
  ).get() as { count: number };
  return row.count;
}

function tryReserveSlot(
  email: string,
  firstName: string | null,
  lastName: string | null,
  ip: string | null,
  ua: string | null,
): { ok: true; id: string } | { ok: false; reason: "duplicate" | "slots_full" } {
  const reserve = db.transaction(() => {
    const existing = db.prepare(`SELECT id FROM beta_applicants WHERE email = ?`).get(email);
    if (existing) return { ok: false as const, reason: "duplicate" as const };

    const { count } = db.prepare(
      `SELECT COUNT(*) as count FROM beta_applicants WHERE status IN ('invited', 'member', 'installed')`,
    ).get() as { count: number };

    const { value: capStr } = db.prepare(
      `SELECT value FROM beta_settings WHERE key = 'slot_cap'`,
    ).get() as { value: string };

    if (count >= Number(capStr)) return { ok: false as const, reason: "slots_full" as const };

    const id = crypto.randomUUID();
    db.prepare(
      `INSERT INTO beta_applicants (id, email, first_name, last_name, status, ip_address, user_agent)
       VALUES (?, ?, ?, ?, 'pending', ?, ?)`,
    ).run(id, email, firstName, lastName, ip, ua);
    return { ok: true as const, id };
  });
  return reserve();
}

beforeEach(() => {
  db = new Database(":memory:");
  db.pragma("journal_mode = WAL");
  db.pragma("busy_timeout = 5000");
  db.pragma("foreign_keys = ON");
  initTestSchema(db);
});

afterEach(() => {
  db.close();
});

describe("beta CRUD", () => {
  it("inserts and retrieves an applicant by id", () => {
    const applicant = insertApplicant("jerry@dead.net", "Jerry", "Garcia", "127.0.0.1", "test-agent");
    expect(applicant.email).toBe("jerry@dead.net");
    expect(applicant.status).toBe("pending");

    const fetched = getApplicantById(applicant.id as string);
    expect(fetched).toBeDefined();
    expect(fetched!.first_name).toBe("Jerry");
  });

  it("retrieves an applicant by email (case-insensitive)", () => {
    insertApplicant("Bob@Dead.Net", "Bob", "Weir", null, null);
    const fetched = getApplicantByEmail("bob@dead.net");
    expect(fetched).toBeDefined();
    expect(fetched!.first_name).toBe("Bob");
  });

  it("lists applicants in reverse chronological order", () => {
    const a = insertApplicant("a@test.com", "A", null, null, null);
    db.prepare(`UPDATE beta_applicants SET created_at = created_at - 10 WHERE id = ?`).run(a.id);
    insertApplicant("b@test.com", "B", null, null, null);
    const list = listApplicants() as Record<string, unknown>[];
    expect(list).toHaveLength(2);
    expect(list[0].first_name).toBe("B");
  });

  it("updates applicant status with extra fields", () => {
    const applicant = insertApplicant("phil@dead.net", "Phil", "Lesh", null, null);
    const now = Math.floor(Date.now() / 1000);
    updateApplicantStatus(applicant.id as string, "invited", {
      asc_invitation_id: "inv-123",
      invited_at: now,
    });
    const updated = getApplicantById(applicant.id as string);
    expect(updated!.status).toBe("invited");
    expect(updated!.asc_invitation_id).toBe("inv-123");
    expect(updated!.invited_at).toBe(now);
  });
});

describe("beta settings", () => {
  it("returns seeded defaults", () => {
    const settings = getSettings();
    expect(settings.auto_approve).toBe("true");
    expect(settings.slot_cap).toBe("100");
  });

  it("upserts a setting", () => {
    setSetting("slot_cap", "50");
    const settings = getSettings();
    expect(settings.slot_cap).toBe("50");
  });

  it("adds a new setting", () => {
    setSetting("waitlist_message", "Stay tuned!");
    expect(getSettings().waitlist_message).toBe("Stay tuned!");
  });
});

describe("slot reservation", () => {
  it("reserves a slot when capacity available", () => {
    const result = tryReserveSlot("fan@test.com", "Dead", "Fan", "1.2.3.4", "ua");
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.id).toBeDefined();
      const applicant = getApplicantById(result.id);
      expect(applicant!.status).toBe("pending");
    }
  });

  it("rejects duplicate email", () => {
    tryReserveSlot("fan@test.com", "Dead", "Fan", null, null);
    const result = tryReserveSlot("fan@test.com", "Dead", "Fan", null, null);
    expect(result).toEqual({ ok: false, reason: "duplicate" });
  });

  it("rejects when slots are full", () => {
    setSetting("slot_cap", "1");
    const a = insertApplicant("invited@test.com", "A", null, null, null);
    updateApplicantStatus(a.id as string, "invited");

    const result = tryReserveSlot("new@test.com", "B", null, null, null);
    expect(result).toEqual({ ok: false, reason: "slots_full" });
  });

  it("only one of two reservations succeeds at the last slot", () => {
    setSetting("slot_cap", "1");

    const r1 = tryReserveSlot("first@test.com", "First", null, null, null);
    expect(r1.ok).toBe(true);

    updateApplicantStatus((r1 as { ok: true; id: string }).id, "invited");

    const r2 = tryReserveSlot("second@test.com", "Second", null, null, null);
    expect(r2).toEqual({ ok: false, reason: "slots_full" });
  });

  it("pending applicants do not count toward slot cap", () => {
    setSetting("slot_cap", "1");
    tryReserveSlot("pending@test.com", "Pending", null, null, null);

    const result = tryReserveSlot("another@test.com", "Another", null, null, null);
    expect(result.ok).toBe(true);
  });

  it("counts only invited/member/installed toward slots", () => {
    setSetting("slot_cap", "3");

    const a1 = insertApplicant("a@t.com", null, null, null, null);
    updateApplicantStatus(a1.id as string, "invited");

    const a2 = insertApplicant("b@t.com", null, null, null, null);
    updateApplicantStatus(a2.id as string, "member");

    const a3 = insertApplicant("c@t.com", null, null, null, null);
    updateApplicantStatus(a3.id as string, "installed");

    expect(countSlotsUsed()).toBe(3);

    const result = tryReserveSlot("d@t.com", null, null, null, null);
    expect(result).toEqual({ ok: false, reason: "slots_full" });
  });
});
