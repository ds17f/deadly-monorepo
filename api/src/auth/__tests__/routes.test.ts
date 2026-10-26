import Fastify from "fastify";
import formbody from "@fastify/formbody";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ auth: vi.fn(), run: vi.fn() }));
vi.mock("@auth/core", () => ({ Auth: mocks.auth }));
vi.mock("../config.js", () => ({ authConfig: {} }));
vi.mock("../middleware.js", () => ({ requireAuth: vi.fn() }));
vi.mock("../../db/users.js", () => ({ getUsersDb: () => ({ prepare: () => ({ run: mocks.run }) }) }));
import { authRoutes } from "../routes.js";

beforeEach(() => vi.clearAllMocks());

describe("Apple callback name backfill", () => {
  it.each([false, true])("updates only the verified sign-in user (successful=%s)", async (successful) => {
    mocks.auth.mockImplementation(async (_request, config) => {
      if (successful) {
        await config.events.signIn({ user: { id: "verified-user" }, account: { provider: "apple" } });
      }
      return new Response(null, { status: 302, headers: { location: "/" } });
    });
    const app = Fastify();
    await app.register(formbody);
    await app.register(authRoutes);
    try {
      await app.inject({
        method: "POST", url: "/api/auth/callback/apple",
        payload: { email: "victim@example.com", user: JSON.stringify({ email: "victim@example.com", name: { firstName: "Name" } }) },
      });
      if (successful) {
        expect(mocks.run).toHaveBeenCalledTimes(2);
        expect(mocks.run).toHaveBeenNthCalledWith(1, "Name", "verified-user");
        expect(mocks.run).toHaveBeenNthCalledWith(2, "Name", "verified-user");
      } else {
        expect(mocks.run).not.toHaveBeenCalled();
      }
    } finally { await app.close(); }
  });
});
