import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import { Auth } from "@auth/core";
import { authConfig } from "./config.js";
import { decodeJwt } from "./crypto.js";
import { getAppUserById } from "../db/users.js";

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

async function resolveUser(request: FastifyRequest): Promise<{ id: string; email?: string; name?: string; isAdmin?: boolean } | null> {
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
      return {
        id: session.user.id,
        email: session.user.email,
        name: session.user.name,
        isAdmin: session.user.isAdmin,
      };
    }
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
