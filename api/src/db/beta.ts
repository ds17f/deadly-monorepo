import crypto from "node:crypto";
import { getUsersDb } from "./users.js";

export type BetaStatus =
  | "pending"
  | "invited"
  | "member"
  | "installed"
  | "expired"
  | "removed"
  | "rejected"
  | "error";

export interface BetaApplicant {
  id: string;
  email: string;
  first_name: string | null;
  last_name: string | null;
  status: BetaStatus;
  asc_invitation_id: string | null;
  asc_user_id: string | null;
  last_error: string | null;
  ip_address: string | null;
  user_agent: string | null;
  created_at: number;
  invited_at: number | null;
  member_at: number | null;
  installed_at: number | null;
  removed_at: number | null;
}

export type ReserveResult =
  | { ok: true; id: string }
  | { ok: false; reason: "duplicate" | "slots_full" };

export function insertApplicant(
  email: string,
  firstName: string | null,
  lastName: string | null,
  ip: string | null,
  ua: string | null,
): BetaApplicant {
  const db = getUsersDb();
  const id = crypto.randomUUID();
  db.prepare(
    `INSERT INTO beta_applicants (id, email, first_name, last_name, status, ip_address, user_agent)
     VALUES (?, ?, ?, ?, 'pending', ?, ?)`,
  ).run(id, email, firstName, lastName, ip, ua);
  return db.prepare(`SELECT * FROM beta_applicants WHERE id = ?`).get(id) as BetaApplicant;
}

export function listApplicants(): BetaApplicant[] {
  const db = getUsersDb();
  return db.prepare(`SELECT * FROM beta_applicants ORDER BY created_at DESC`).all() as BetaApplicant[];
}

export function getApplicantById(id: string): BetaApplicant | undefined {
  const db = getUsersDb();
  return db.prepare(`SELECT * FROM beta_applicants WHERE id = ?`).get(id) as BetaApplicant | undefined;
}

export function getApplicantByEmail(email: string): BetaApplicant | undefined {
  const db = getUsersDb();
  return db.prepare(`SELECT * FROM beta_applicants WHERE email = ?`).get(email) as BetaApplicant | undefined;
}

export function updateApplicantStatus(
  id: string,
  status: BetaStatus,
  fields?: Partial<Pick<BetaApplicant, "first_name" | "last_name" | "asc_invitation_id" | "asc_user_id" | "last_error" | "invited_at" | "member_at" | "installed_at" | "removed_at">>,
): void {
  const db = getUsersDb();
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

export function getSettings(): Record<string, string> {
  const db = getUsersDb();
  const rows = db.prepare(`SELECT key, value FROM beta_settings`).all() as { key: string; value: string }[];
  const result: Record<string, string> = {};
  for (const row of rows) {
    result[row.key] = row.value;
  }
  return result;
}

export function setSetting(key: string, value: string): void {
  const db = getUsersDb();
  db.prepare(
    `INSERT INTO beta_settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value`,
  ).run(key, value);
}

export function countSlotsUsed(): number {
  const db = getUsersDb();
  const row = db.prepare(
    `SELECT COUNT(*) as count FROM beta_applicants WHERE status IN ('invited', 'member', 'installed')`,
  ).get() as { count: number };
  return row.count;
}

export function tryReserveSlot(
  email: string,
  firstName: string | null,
  lastName: string | null,
  ip: string | null,
  ua: string | null,
): ReserveResult {
  const db = getUsersDb();

  const reserve = db.transaction(() => {
    const existing = db.prepare(
      `SELECT id FROM beta_applicants WHERE email = ?`,
    ).get(email);
    if (existing) {
      return { ok: false as const, reason: "duplicate" as const };
    }

    const { count } = db.prepare(
      `SELECT COUNT(*) as count FROM beta_applicants WHERE status IN ('invited', 'member', 'installed')`,
    ).get() as { count: number };

    const { value: capStr } = db.prepare(
      `SELECT value FROM beta_settings WHERE key = 'slot_cap'`,
    ).get() as { value: string };

    if (count >= Number(capStr)) {
      return { ok: false as const, reason: "slots_full" as const };
    }

    const id = crypto.randomUUID();
    db.prepare(
      `INSERT INTO beta_applicants (id, email, first_name, last_name, status, ip_address, user_agent)
       VALUES (?, ?, ?, ?, 'pending', ?, ?)`,
    ).run(id, email, firstName, lastName, ip, ua);

    return { ok: true as const, id };
  });

  return reserve();
}
