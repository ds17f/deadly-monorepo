import { Auth } from "@auth/core";
import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import { authConfig } from "./config.js";
import { requireAuth } from "./middleware.js";
import { getUsersDb } from "../db/users.js";

export async function authRoutes(app: FastifyInstance): Promise<void> {
  app.get("/api/auth/me", {
    schema: {
      tags: ["auth"],
      summary: "Get current user",
      description: "Returns the authenticated user's profile",
      response: {
        200: {
          type: "object",
          properties: {
            id: { type: "string" },
            email: { type: "string" },
            name: { type: "string" },
          },
        },
        401: {
          type: "object",
          properties: {
            error: { type: "string" },
          },
        },
      },
    },
    preHandler: requireAuth,
  }, async (request) => {
    return request.user;
  });

  app.all("/api/auth/*", {
    schema: { hide: true },
  }, async (request: FastifyRequest, reply: FastifyReply) => {
    // Apple sends the user's name only on the first authorization, in a `user`
    // POST body parameter (JSON). Auth.js ignores it — extract before forwarding.
    const appleName = extractAppleName(request);

    const webRequest = await toWebRequest(request);
    const webResponse = await Auth(webRequest, authConfig);

    // If Apple sent a name, backfill it into the freshly-created user record.
    if (appleName) {
      backfillAppleName(request, appleName);
    }

    return sendWebResponse(reply, webResponse);
  });
}

async function toWebRequest(req: FastifyRequest): Promise<Request> {
  const protocol = req.headers["x-forwarded-proto"] ?? "http";
  const host = req.headers["x-forwarded-host"] ?? req.headers.host ?? "localhost";
  const url = `${protocol}://${host}${req.url}`;

  const headers = new Headers();
  for (const [key, value] of Object.entries(req.headers)) {
    if (value === undefined) continue;
    if (Array.isArray(value)) {
      for (const v of value) headers.append(key, v);
    } else {
      headers.set(key, value);
    }
  }

  const method = req.method.toUpperCase();
  const init: RequestInit = { method, headers };

  if (method !== "GET" && method !== "HEAD") {
    // Fastify may have already parsed the body (formbody, JSON, etc.)
    if (req.body && typeof req.body === "object") {
      const params = new URLSearchParams();
      for (const [key, value] of Object.entries(req.body as Record<string, string>)) {
        params.set(key, value);
      }
      init.body = params.toString();
      headers.set("content-type", "application/x-www-form-urlencoded");
    } else if (typeof req.body === "string") {
      init.body = req.body;
    } else {
      // Raw body fallback: read from the underlying stream
      const chunks: Buffer[] = [];
      for await (const chunk of req.raw) {
        chunks.push(chunk as Buffer);
      }
      if (chunks.length > 0) {
        init.body = Buffer.concat(chunks);
      }
    }
  }

  return new Request(url, init);
}

async function sendWebResponse(reply: FastifyReply, response: Response): Promise<void> {
  reply.status(response.status);

  for (const [key, value] of response.headers.entries()) {
    if (key === "set-cookie") {
      // getSetCookie() returns each Set-Cookie separately
      const cookies = response.headers.getSetCookie();
      for (const cookie of cookies) {
        reply.header("set-cookie", cookie);
      }
    } else {
      reply.header(key, value);
    }
  }

  const body = await response.text();
  if (body) {
    reply.send(body);
  } else {
    reply.send();
  }
}

/** Extract the user's name from Apple's `user` POST parameter (JSON). */
function extractAppleName(request: FastifyRequest): string | null {
  if (!request.url.includes("/callback/apple")) return null;
  if (request.method !== "POST") return null;

  const body = request.body as Record<string, string> | undefined;
  const userParam = body?.user;
  if (!userParam) return null;

  try {
    const parsed = JSON.parse(typeof userParam === "string" ? userParam : JSON.stringify(userParam));
    const first = parsed?.name?.firstName ?? "";
    const last = parsed?.name?.lastName ?? "";
    const full = [first, last].filter(Boolean).join(" ");
    return full || null;
  } catch {
    return null;
  }
}

/** Update both auth_users and accounts tables with the Apple-provided name. */
function backfillAppleName(request: FastifyRequest, name: string): void {
  try {
    const body = request.body as Record<string, string> | undefined;
    const email = body?.email ?? (body?.user ? JSON.parse(body.user)?.email : null);
    if (!email) return;

    const db = getUsersDb();
    // Update auth_users (Auth.js layer) — only if name is currently null
    db.prepare(
      `UPDATE auth_users SET name = ? WHERE email = ? AND name IS NULL`
    ).run(name, email.toLowerCase());

    // Update accounts (app layer) — only if name is currently null
    db.prepare(
      `UPDATE accounts SET name = ? WHERE email = ? AND name IS NULL`
    ).run(name, email.toLowerCase());
  } catch {
    // Non-critical — don't break the auth flow
  }
}
