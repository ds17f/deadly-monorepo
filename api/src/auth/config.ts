import type { AuthConfig } from "@auth/core";
import Google from "@auth/core/providers/google";
import Apple from "@auth/core/providers/apple";
import { SqliteAdapter } from "./adapter.js";
import { getAppUserByAuthId } from "../db/users.js";

export const authConfig: AuthConfig = {
  adapter: SqliteAdapter(),
  providers: [
    Google({
      clientId: process.env.GOOGLE_CLIENT_ID,
      clientSecret: process.env.GOOGLE_CLIENT_SECRET,
    }),
    Apple({
      clientId: process.env.APPLE_CLIENT_ID!,
      clientSecret: process.env.APPLE_CLIENT_SECRET!,
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
  pages: {
    error: "/auth/error",
  },
  trustHost: true,
};
