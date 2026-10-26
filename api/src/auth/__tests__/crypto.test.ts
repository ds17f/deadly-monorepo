import { afterEach, describe, expect, it, vi } from "vitest";

afterEach(() => {
  vi.unstubAllEnvs();
  vi.resetModules();
});

describe("token secret", () => {
  it.each([undefined, "", "   "])("refuses to derive a key from missing/blank configuration (%s)", async (secret) => {
    vi.stubEnv("AUTH_SECRET", secret);
    vi.resetModules();
    const { getSecretKey, decodeJwt } = await import("../crypto.js");
    await expect(getSecretKey()).rejects.toThrow("AUTH_SECRET must be configured");
    expect(await decodeJwt("untrusted-token")).toBeNull();
  });

  it("round-trips a token with a configured secret and rejects a different secret", async () => {
    vi.stubEnv("AUTH_SECRET", "test-secret-one");
    vi.resetModules();
    const { mintToken, decodeJwt } = await import("../crypto.js");
    const token = await mintToken({ accountId: "account", authUserId: "auth", email: "user@example.com" });
    expect(await decodeJwt(token)).toMatchObject({ accountId: "account" });
    vi.stubEnv("AUTH_SECRET", "test-secret-two");
    vi.resetModules();
    const other = await import("../crypto.js");
    expect(await other.decodeJwt(token)).toBeNull();
  });
});
