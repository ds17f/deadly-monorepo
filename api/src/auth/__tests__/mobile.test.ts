import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  verify: vi.fn(),
  getUserByAccount: vi.fn(),
  getUserByEmail: vi.fn(),
  linkAccount: vi.fn(),
}));
vi.mock("jose", () => ({ jwtVerify: mocks.verify, createRemoteJWKSet: vi.fn() }));
vi.mock("../adapter.js", () => ({ SqliteAdapter: () => mocks }));
vi.mock("../crypto.js", () => ({ mintToken: async () => "app-token" }));
vi.mock("../../db/users.js", () => ({ getAppUserByAuthId: () => ({ id: "account", email: "user@example.com" }), getUsersDb: vi.fn() }));
import { mobileAuthRoutes } from "../mobile.js";

beforeEach(() => {
  vi.clearAllMocks();
  mocks.getUserByAccount.mockResolvedValue(null);
  mocks.getUserByEmail.mockResolvedValue({ id: "auth", name: "User" });
});
afterEach(() => vi.restoreAllMocks());

describe("mobile account linking", () => {
  it.each(["google", "apple"])("requires verified email claims from %s before accessing accounts", async (provider) => {
    const app = Fastify();
    await app.register(mobileAuthRoutes);
    try {
      for (const email_verified of [undefined, false, "false", 1, "yes"]) {
        mocks.verify.mockResolvedValue({ payload: { sub: "subject", email: "user@example.com", email_verified } });
        const response = await app.inject({ method: "POST", url: "/api/auth/mobile/token", payload: { provider, idToken: "signed-token" } });
        expect(response.statusCode).toBe(401);
      }
      expect(mocks.getUserByAccount).not.toHaveBeenCalled();
      expect(mocks.getUserByEmail).not.toHaveBeenCalled();
      expect(mocks.linkAccount).not.toHaveBeenCalled();
    } finally { await app.close(); }
  });

  it.each([true, "true"])("permits linking for a verified email (%s)", async (email_verified) => {
    mocks.verify.mockResolvedValue({ payload: { sub: "subject", email: "user@example.com", email_verified } });
    const app = Fastify();
    await app.register(mobileAuthRoutes);
    try {
      const response = await app.inject({ method: "POST", url: "/api/auth/mobile/token", payload: { provider: "apple", idToken: "signed-token" } });
      expect(response.statusCode).toBe(200);
      expect(mocks.linkAccount).toHaveBeenCalledWith(expect.objectContaining({ userId: "auth", provider: "apple", providerAccountId: "subject" }));
    } finally { await app.close(); }
  });
});
