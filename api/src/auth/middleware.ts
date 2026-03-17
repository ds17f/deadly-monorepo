import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import { jwtDecrypt } from "jose";
import { Auth } from "@auth/core";
import { authConfig } from "./config.js";

declare module "fastify" {
  interface FastifyRequest {
    user?: {
      id: string;
      email?: string;
      name?: string;
    };
  }
}

const AUTH_SECRET = process.env.AUTH_SECRET;

// Auth.js v5 uses JWE with dir + A256CBC-HS512, which requires a 512-bit key
// derived via HKDF. We use Auth.js's own getDerivedEncryptionKey for correctness.
let encryptionKey: Uint8Array | null = null;

async function getSecretKey(): Promise<Uint8Array> {
  if (encryptionKey) return encryptionKey;
  // Auth.js uses @panva/hkdf with:
  //   digest: sha256, ikm: secret, salt: cookieName, info: "Auth.js Generated Encryption Key (<cookieName>)"
  //   length: 64 bytes for A256CBC-HS512
  const cookieName = "authjs.session-token";
  const encoder = new TextEncoder();
  const keyMaterial = encoder.encode(AUTH_SECRET);
  const rawKey = await crypto.subtle.importKey("raw", keyMaterial, "HKDF", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits(
    {
      name: "HKDF",
      hash: "SHA-256",
      salt: encoder.encode(cookieName),
      info: encoder.encode(`Auth.js Generated Encryption Key (${cookieName})`),
    },
    rawKey,
    512, // A256CBC-HS512 needs 64 bytes
  );
  encryptionKey = new Uint8Array(bits);
  return encryptionKey;
}

async function decodeJwt(token: string): Promise<Record<string, unknown> | null> {
  try {
    const key = await getSecretKey();
    const { payload } = await jwtDecrypt(token, key);
    return payload as Record<string, unknown>;
  } catch (err) {
    return null;
  }
}

async function resolveUser(request: FastifyRequest): Promise<{ id: string; email?: string; name?: string } | null> {
  // 1. Check Authorization: Bearer header
  const authHeader = request.headers.authorization;
  if (authHeader?.startsWith("Bearer ")) {
    const token = authHeader.slice(7);
    const payload = await decodeJwt(token);
    if (payload?.accountId) {
      return {
        id: payload.accountId as string,
        email: payload.email as string | undefined,
        name: payload.name as string | undefined,
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
    const session = await sessionResponse.json() as { user?: { id?: string; email?: string; name?: string } };
    if (session?.user?.id) {
      return {
        id: session.user.id,
        email: session.user.email,
        name: session.user.name,
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

export async function optionalAuth(request: FastifyRequest): Promise<void> {
  const user = await resolveUser(request);
  if (user) {
    request.user = user;
  }
}
