import type { Adapter, AdapterUser, AdapterAccount } from "@auth/core/adapters";
import { getUsersDb, createAppUser } from "../db/users.js";

export function SqliteAdapter(): Adapter {
  return {
    createUser(user) {
      const db = getUsersDb();
      const id = crypto.randomUUID();
      db.prepare(
        `INSERT INTO auth_users (id, name, email, "emailVerified", image) VALUES (?, ?, ?, ?, ?)`
      ).run(id, user.name ?? null, user.email, user.emailVerified?.toISOString() ?? null, user.image ?? null);

      const created: AdapterUser = {
        id,
        name: user.name ?? null,
        email: user.email,
        emailVerified: user.emailVerified ?? null,
        image: user.image ?? null,
      };
      return created;
    },

    getUser(id) {
      const db = getUsersDb();
      const row = db.prepare(`SELECT * FROM auth_users WHERE id = ?`).get(id) as Record<string, unknown> | undefined;
      if (!row) return null;
      return toAdapterUser(row);
    },

    getUserByEmail(email) {
      const db = getUsersDb();
      const row = db.prepare(`SELECT * FROM auth_users WHERE email = ?`).get(email) as Record<string, unknown> | undefined;
      if (!row) return null;
      return toAdapterUser(row);
    },

    getUserByAccount({ providerAccountId, provider }) {
      const db = getUsersDb();
      const row = db.prepare(
        `SELECT u.* FROM auth_users u
         JOIN auth_accounts a ON a."userId" = u.id
         WHERE a.provider = ? AND a."providerAccountId" = ?`
      ).get(provider, providerAccountId) as Record<string, unknown> | undefined;
      if (!row) return null;
      return toAdapterUser(row);
    },

    updateUser(user) {
      const db = getUsersDb();
      db.prepare(
        `UPDATE auth_users SET name = COALESCE(?, name), email = COALESCE(?, email),
         "emailVerified" = COALESCE(?, "emailVerified"), image = COALESCE(?, image)
         WHERE id = ?`
      ).run(user.name ?? null, user.email ?? null, user.emailVerified?.toISOString() ?? null, user.image ?? null, user.id);
      const row = db.prepare(`SELECT * FROM auth_users WHERE id = ?`).get(user.id) as Record<string, unknown>;
      return toAdapterUser(row);
    },

    linkAccount(account) {
      const db = getUsersDb();
      const id = crypto.randomUUID();
      db.prepare(
        `INSERT INTO auth_accounts (id, "userId", type, provider, "providerAccountId",
         refresh_token, access_token, expires_at, token_type, scope, id_token, session_state)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
      ).run(
        id,
        account.userId,
        account.type,
        account.provider,
        account.providerAccountId,
        account.refresh_token ?? null,
        account.access_token ?? null,
        account.expires_at ?? null,
        account.token_type ?? null,
        account.scope ?? null,
        account.id_token ?? null,
        account.session_state ?? null,
      );

      // Create app-level account if it doesn't exist yet
      const authUser = db.prepare(`SELECT * FROM auth_users WHERE id = ?`).get(account.userId) as Record<string, unknown>;
      if (authUser) {
        const existing = db.prepare(`SELECT id FROM accounts WHERE auth_user_id = ?`).get(account.userId);
        if (!existing) {
          createAppUser(
            account.userId,
            authUser.email as string,
            authUser.name as string | null,
            account.provider,
          );
        }
      }

      return account as AdapterAccount;
    },
  };
}

function toAdapterUser(row: Record<string, unknown>): AdapterUser {
  return {
    id: row.id as string,
    name: (row.name as string) ?? null,
    email: row.email as string,
    emailVerified: row.emailVerified ? new Date(row.emailVerified as string) : null,
    image: (row.image as string) ?? null,
  };
}
