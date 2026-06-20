import { describe, it, expect } from "vitest";
import Fastify from "fastify";
import os from "node:os";
import path from "node:path";
import crypto from "node:crypto";

// Throwaway users-db so the app_settings flag persists to a real (temp) file.
process.env.USERS_DB_PATH = path.join(os.tmpdir(), `connect-public-test-${crypto.randomUUID()}.db`);

const { setConnectEnabled } = await import("../../db/appSettings.js");
const { connectPublicRoutes } = await import("../connectPublic.js");

// Register the route in isolation (no auth stack) — it must be reachable without
// any session/admin credentials and reflect the persisted flag. See ADR-0018.
async function buildBareApp() {
  const app = Fastify();
  await app.register(connectPublicRoutes);
  await app.ready();
  return app;
}

describe("GET /api/connect/enabled (public)", () => {
  it("is reachable with no auth and reports the flag", async () => {
    const app = await buildBareApp();

    setConnectEnabled(false);
    let res = await app.inject({ method: "GET", url: "/api/connect/enabled" });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({ connectEnabled: false });

    setConnectEnabled(true);
    res = await app.inject({ method: "GET", url: "/api/connect/enabled" });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({ connectEnabled: true });

    await app.close();
  });
});
