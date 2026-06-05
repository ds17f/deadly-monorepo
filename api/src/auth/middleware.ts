import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import { Auth } from "@auth/core";
import { authConfig } from "./config.js";
import { decodeJwt } from "./crypto.js";
import { getAppUserById, getAppUserByAuthId, getAppUserByEmail } from "../db/users.js";

declare module "fastify" {
  interface FastifyRequest {
    user?: {
      id: string;
      email?: string;
      name?: string;
      isAdmin?: boolean;
    };
  }
}

export async function resolveUser(request: FastifyRequest): Promise<{ id: string; email?: string; name?: string; isAdmin?: boolean } | null> {
  // 1. Check Authorization: Bearer header
  const authHeader = request.headers.authorization;
  if (authHeader?.startsWith("Bearer ")) {
    const token = authHeader.slice(7);
    const payload = await decodeJwt(token);
    if (payload?.accountId) {
      const appUser = getAppUserById(payload.accountId as string);
      return {
        id: payload.accountId as string,
        email: payload.email as string | undefined,
        name: payload.name as string | undefined,
        isAdmin: appUser?.is_admin === 1,
      };
    }
    return null;
  }

  // 2. Fall back to session cookie via Auth.js
  const protocol = request.headers["x-forwarded-proto"] ?? "http";
  const host = request.headers["x-forwarded-host"] ?? request.headers.host ?? "localhost";
  const url = `${protocol}://${host}/api/auth/session`;

  const headers = new Headers();
  if (request.headers.cookie) {
    headers.set("cookie", request.headers.cookie);
  }

  const sessionResponse = await Auth(new Request(url, { headers }), authConfig);
  if (sessionResponse.ok) {
    const session = await sessionResponse.json() as { user?: { id?: string; email?: string; name?: string; isAdmin?: boolean } };
    if (session?.user?.id) {
      // Old cookies (minted before the session callback stamped accountId, or
      // surviving a DB rotation) may carry an id that's not in accounts and not
      // in auth_users. Try by id, then by auth_user_id, then by email.
      const id = canonicalizeAccountId(session.user.id, session.user.email);
      if (!id) return null;
      const appUser = getAppUserById(id);
      return {
        id,
        email: session.user.email,
        name: session.user.name,
        isAdmin: session.user.isAdmin ?? appUser?.is_admin === 1,
      };
    }
  }

  return null;
}

/** Returns the accounts.id given any of: accounts.id, auth_users.id, or email. */
function canonicalizeAccountId(id: string, email?: string): string | null {
  if (getAppUserById(id)) return id;
  const byAuth = getAppUserByAuthId(id);
  if (byAuth) return byAuth.id;
  if (email) {
    const byEmail = getAppUserByEmail(email);
    if (byEmail) return byEmail.id;
  }
  return null;
}

export async function authMiddleware(app: FastifyInstance): Promise<void> {
  app.decorateRequest("user", undefined);
}

export async function requireAuth(request: FastifyRequest, reply: FastifyReply): Promise<void> {
  const user = await resolveUser(request);
  if (!user) {
    reply.code(401).send({ error: "Unauthorized" });
    return;
  }
  request.user = user;
}

export async function requireAdmin(request: FastifyRequest, reply: FastifyReply): Promise<void> {
  const user = await resolveUser(request);
  if (!user) {
    reply.code(401).send({ error: "Unauthorized" });
    return;
  }
  if (!user.isAdmin) {
    reply.code(403).send({ error: "Forbidden" });
    return;
  }
  request.user = user;
}

export async function optionalAuth(request: FastifyRequest): Promise<void> {
  const user = await resolveUser(request);
  if (user) {
    request.user = user;
  }
}
