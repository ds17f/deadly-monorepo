import type { AuthConfig } from "@auth/core";
import type { Provider } from "@auth/core/providers";
import Google from "@auth/core/providers/google";
import Apple from "@auth/core/providers/apple";
import Credentials from "@auth/core/providers/credentials";
import { SqliteAdapter } from "./adapter.js";
import { getAppUserByAuthId, getUsersDb } from "../db/users.js";
import { generateAppleSecret } from "./apple-secret.js";
import { isDev } from "../env.js";

const appleClientSecret = await generateAppleSecret();

const providers: Provider[] = [
  Google({
    clientId: process.env.GOOGLE_CLIENT_ID,
    clientSecret: process.env.GOOGLE_CLIENT_SECRET,
  }),
  Apple({
    clientId: process.env.APPLE_CLIENT_ID!,
    clientSecret: appleClientSecret,
    // Apple's OIDC discovery omits userinfo_endpoint, causing @auth/core to
    // throw. Explicit endpoints skip discovery. The userinfo URL is never
    // fetched — Apple is OIDC so identity comes from the ID token.
    token: { url: "https://appleid.apple.com/auth/token" },
    userinfo: { url: "https://appleid.apple.com" },
    client: { token_endpoint_auth_method: "client_secret_post" },
  }),
];

// Dev sign-in: passwordless email-only credentials provider.
// Hard-gated by THREE conditions to prevent accidental prod exposure:
//   1. NODE_ENV !== "production" (the isDev flag)
//   2. ENABLE_DEV_SIGNIN=1 must be explicitly set (lives only in local api/.env;
//      a CI check forbids it from appearing in any deploy/infra config)
//   3. Hard refusal to start if NODE_ENV=production yet the flag is set
const devSigninRequested = process.env.ENABLE_DEV_SIGNIN === "1";
if (process.env.NODE_ENV === "production" && devSigninRequested) {
  throw new Error(
    "FATAL: ENABLE_DEV_SIGNIN=1 with NODE_ENV=production. Refusing to start."
  );
}
const devSigninEnabled = isDev && devSigninRequested;

if (devSigninEnabled) {
  console.warn(
    "⚠️  DEV SIGN-IN ENABLED — passwordless email login active. Never run with this in production."
  );
  providers.push(
    Credentials({
      id: "dev",
      name: "Dev",
      credentials: { email: { label: "Email", type: "email" } },
      async authorize(credentials) {
        const email = typeof credentials?.email === "string" ? credentials.email : "";
        if (!email) return null;
        const row = getUsersDb().prepare(
          `SELECT auth_user_id, email, name FROM accounts WHERE email = ?`
        ).get(email) as { auth_user_id: string; email: string; name: string | null } | undefined;
        if (!row?.auth_user_id) return null;
        return { id: row.auth_user_id, email: row.email, name: row.name };
      },
    })
  );
}

export const authConfig: AuthConfig = {
  adapter: SqliteAdapter(),
  providers,
  secret: process.env.AUTH_SECRET,
  session: { strategy: "jwt" },
  basePath: "/api/auth",
  callbacks: {
    jwt({ token, user }) {
      if (user) {
        // First sign-in: embed the auth user ID
        token.authUserId = user.id;
      }
      // Resolve the app-level account ID and admin flag
      if (token.authUserId) {
        const appUser = getAppUserByAuthId(token.authUserId as string);
        if (appUser) {
          token.accountId = appUser.id;
          token.isAdmin = appUser.is_admin === 1;
          // accounts.name is the editable source of truth for the display
          // name (PATCH /api/user/account). Re-read it each refresh so an edit
          // shows up without re-login; keep the OAuth name if it's unset.
          if (appUser.name) token.name = appUser.name;
        }
      }
      return token;
    },
    session({ session, token }) {
      if (token.accountId) {
        session.user.id = token.accountId as string;
      }
      if (token.authUserId) {
        (session.user as unknown as Record<string, unknown>).authUserId = token.authUserId;
      }
      if (token.isAdmin) {
        (session.user as unknown as Record<string, unknown>).isAdmin = token.isAdmin;
      }
      return session;
    },
  },
  cookies: {
    // Apple uses response_mode=form_post, so the callback is a cross-origin
    // POST from appleid.apple.com. SameSite=Lax (the default) blocks cookies
    // on cross-origin POST — use "none" so state/nonce survive the round-trip.
    state: {
      name: "__Secure-authjs.state",
      options: { httpOnly: true, sameSite: "none" as const, path: "/", secure: true },
    },
    nonce: {
      name: "__Secure-authjs.nonce",
      options: { httpOnly: true, sameSite: "none" as const, path: "/", secure: true },
    },
    pkceCodeVerifier: {
      name: "__Secure-authjs.pkce.code_verifier",
      options: { httpOnly: true, sameSite: "none" as const, path: "/", secure: true },
    },
  },
  pages: {
    error: "/auth/error",
  },
  trustHost: true,
};
