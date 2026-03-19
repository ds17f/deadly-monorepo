import type { AuthConfig } from "@auth/core";
import Google from "@auth/core/providers/google";
import Apple from "@auth/core/providers/apple";
import { SqliteAdapter } from "./adapter.js";
import { getAppUserByAuthId } from "../db/users.js";
import { generateAppleSecret } from "./apple-secret.js";

const appleClientSecret = await generateAppleSecret();

export const authConfig: AuthConfig = {
  adapter: SqliteAdapter(),
  providers: [
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
  ],
  secret: process.env.AUTH_SECRET,
  session: { strategy: "jwt" },
  basePath: "/api/auth",
  callbacks: {
    jwt({ token, user }) {
      if (user) {
        // First sign-in: embed the auth user ID
        token.authUserId = user.id;
      }
      // Resolve the app-level account ID
      if (token.authUserId && !token.accountId) {
        const appUser = getAppUserByAuthId(token.authUserId as string);
        if (appUser) {
          token.accountId = appUser.id;
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
