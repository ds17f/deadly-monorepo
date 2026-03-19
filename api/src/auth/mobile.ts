import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import * as jose from "jose";
import { mintToken } from "./crypto.js";
import { SqliteAdapter } from "./adapter.js";
import { getUsersDb, getAppUserByAuthId } from "../db/users.js";

// Cache JWKS key sets
let googleJWKS: jose.JWTVerifyGetKey | null = null;
let appleJWKS: jose.JWTVerifyGetKey | null = null;

function getGoogleJWKS(): jose.JWTVerifyGetKey {
  if (!googleJWKS) {
    googleJWKS = jose.createRemoteJWKSet(new URL("https://www.googleapis.com/oauth2/v3/certs"));
  }
  return googleJWKS;
}

function getAppleJWKS(): jose.JWTVerifyGetKey {
  if (!appleJWKS) {
    appleJWKS = jose.createRemoteJWKSet(new URL("https://appleid.apple.com/auth/keys"));
  }
  return appleJWKS;
}

interface MobileTokenRequest {
  provider: "google" | "apple";
  idToken: string;
  name?: string;
}

export async function mobileAuthRoutes(app: FastifyInstance): Promise<void> {
  app.post("/api/auth/mobile/token", {
    schema: {
      tags: ["auth"],
      summary: "Exchange native OAuth ID token for app JWT",
      description: "Mobile apps send a provider ID token; the server verifies it and returns a JWE Bearer token",
      body: {
        type: "object",
        required: ["provider", "idToken"],
        properties: {
          provider: { type: "string", enum: ["google", "apple"] },
          idToken: { type: "string" },
          name: { type: "string" },
        },
      },
      response: {
        200: {
          type: "object",
          properties: {
            token: { type: "string" },
            user: {
              type: "object",
              properties: {
                id: { type: "string" },
                email: { type: "string" },
                name: { type: "string" },
              },
            },
          },
        },
        400: {
          type: "object",
          properties: { error: { type: "string" } },
        },
        401: {
          type: "object",
          properties: { error: { type: "string" } },
        },
      },
    },
  }, async (request: FastifyRequest, reply: FastifyReply) => {
    const { provider, idToken, name } = request.body as MobileTokenRequest;

    // 1. Verify the ID token
    let sub: string;
    let email: string;
    let verifiedName: string | null = null;

    try {
      if (provider === "google") {
        const allowedAudiences = [
          process.env.GOOGLE_CLIENT_ID,
          process.env.GOOGLE_IOS_CLIENT_ID,
          process.env.GOOGLE_ANDROID_CLIENT_ID,
        ].filter(Boolean) as string[];

        const { payload } = await jose.jwtVerify(idToken, getGoogleJWKS(), {
          issuer: "https://accounts.google.com",
          audience: allowedAudiences,
        });

        sub = payload.sub!;
        email = (payload as Record<string, unknown>).email as string;
        verifiedName = (payload as Record<string, unknown>).name as string | null;
      } else if (provider === "apple") {
        const allowedAudiences = [
          process.env.APPLE_CLIENT_ID,
          "com.grateful.deadly", // iOS bundle ID
        ].filter(Boolean) as string[];

        const { payload } = await jose.jwtVerify(idToken, getAppleJWKS(), {
          issuer: "https://appleid.apple.com",
          audience: allowedAudiences,
        });

        sub = payload.sub!;
        email = (payload as Record<string, unknown>).email as string;
        // Apple sends name only on first auth, passed via request body
        verifiedName = name ?? null;
      } else {
        return reply.code(400).send({ error: "Unsupported provider" });
      }
    } catch (err) {
      request.log.warn({ err, provider }, "ID token verification failed");
      return reply.code(401).send({ error: "Invalid ID token" });
    }

    if (!sub || !email) {
      return reply.code(401).send({ error: "ID token missing sub or email" });
    }

    // 2. Find or create user using adapter functions
    const adapter = SqliteAdapter();
    let authUser = await adapter.getUserByAccount!({
      provider,
      providerAccountId: sub,
    });

    if (!authUser) {
      // Check if a user with this email already exists (linked via another provider)
      authUser = await adapter.getUserByEmail!(email);

      if (!authUser) {
        // Create new user
        authUser = await adapter.createUser!({
          id: "", // adapter generates the real UUID
          email,
          name: verifiedName,
          emailVerified: new Date(),
        });
      }

      // Link the account
      await adapter.linkAccount!({
        userId: authUser.id,
        type: "oauth",
        provider,
        providerAccountId: sub,
        access_token: undefined,
        refresh_token: undefined,
        expires_at: undefined,
        token_type: undefined,
        scope: undefined,
        id_token: undefined,
        session_state: undefined,
      });
    }

    // 3. Backfill name if needed (Apple sends name only on first auth)
    if (verifiedName && !authUser.name) {
      await adapter.updateUser!({ id: authUser.id, name: verifiedName });
      // Also update app-level accounts table
      const db = getUsersDb();
      db.prepare(
        `UPDATE accounts SET name = ? WHERE auth_user_id = ? AND name IS NULL`
      ).run(verifiedName, authUser.id);
    }

    // 4. Resolve app-level account
    const appUser = getAppUserByAuthId(authUser.id);
    if (!appUser) {
      return reply.code(500).send({ error: "Failed to resolve app user" });
    }

    // 5. Mint JWE token
    const token = await mintToken({
      accountId: appUser.id,
      authUserId: authUser.id,
      email: appUser.email,
      name: appUser.name,
    });

    return reply.send({
      token,
      user: {
        id: appUser.id,
        email: appUser.email,
        name: appUser.name,
      },
    });
  });
}
