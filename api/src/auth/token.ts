import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import { Auth } from "@auth/core";
import { authConfig } from "./config.js";

export async function tokenRoutes(app: FastifyInstance): Promise<void> {
  app.get("/api/auth/token", {
    schema: {
      tags: ["auth"],
      summary: "Get JWT for mobile apps",
      description: "Reads the session cookie and returns the raw JWT for use as a Bearer token",
      response: {
        200: {
          type: "object",
          properties: {
            token: { type: "string" },
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
  }, async (request: FastifyRequest, reply: FastifyReply) => {
    // Extract the session token from cookies
    const cookieHeader = request.headers.cookie;
    if (!cookieHeader) {
      return reply.code(401).send({ error: "No session cookie" });
    }

    // Auth.js stores the JWT in a cookie named __Secure-authjs.session-token (production)
    // or authjs.session-token (development)
    const cookies = parseCookies(cookieHeader);
    const token = cookies["__Secure-authjs.session-token"]
      ?? cookies["authjs.session-token"];

    if (!token) {
      return reply.code(401).send({ error: "No session token" });
    }

    // Verify the session is valid by calling Auth.js
    const protocol = request.headers["x-forwarded-proto"] ?? "http";
    const host = request.headers["x-forwarded-host"] ?? request.headers.host ?? "localhost";
    const sessionResponse = await Auth(
      new Request(`${protocol}://${host}/api/auth/session`, {
        headers: new Headers({ cookie: cookieHeader }),
      }),
      authConfig,
    );

    if (!sessionResponse.ok) {
      return reply.code(401).send({ error: "Invalid session" });
    }

    const session = await sessionResponse.json() as { user?: { id?: string } };
    if (!session?.user?.id) {
      return reply.code(401).send({ error: "Invalid session" });
    }

    return reply.send({ token });
  });
}

function parseCookies(header: string): Record<string, string> {
  const cookies: Record<string, string> = {};
  for (const pair of header.split(";")) {
    const eqIndex = pair.indexOf("=");
    if (eqIndex === -1) continue;
    const key = pair.slice(0, eqIndex).trim();
    const value = pair.slice(eqIndex + 1).trim();
    cookies[key] = decodeURIComponent(value);
  }
  return cookies;
}
