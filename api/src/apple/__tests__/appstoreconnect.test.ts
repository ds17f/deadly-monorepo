import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { decodeJwt, decodeProtectedHeader } from "jose";
import crypto from "node:crypto";

const TEST_KEY_ID = "ABCDEF1234";
const TEST_ISSUER_ID = "9501cc7b-1a6c-4e4d-8c37-c04149a31886";
const TEST_APP_ID = "1234567890";

let testKeyPem: string;
let testKeyBase64: string;

async function generateTestKey() {
  const { privateKey } = await crypto.subtle.generateKey(
    { name: "ECDSA", namedCurve: "P-256" },
    true,
    ["sign"],
  );
  const exported = await crypto.subtle.exportKey("pkcs8", privateKey);
  const pem =
    "-----BEGIN PRIVATE KEY-----\n" +
    Buffer.from(exported).toString("base64") +
    "\n-----END PRIVATE KEY-----";
  return pem;
}

beforeEach(async () => {
  testKeyPem = await generateTestKey();
  testKeyBase64 = Buffer.from(testKeyPem).toString("base64");

  process.env.APP_STORE_CONNECT_KEY_ID = TEST_KEY_ID;
  process.env.APP_STORE_CONNECT_ISSUER_ID = TEST_ISSUER_ID;
  process.env.APP_STORE_CONNECT_PRIVATE_KEY = testKeyBase64;
  process.env.APP_STORE_CONNECT_APP_ID = TEST_APP_ID;
});

afterEach(() => {
  vi.restoreAllMocks();
  delete process.env.APP_STORE_CONNECT_KEY_ID;
  delete process.env.APP_STORE_CONNECT_ISSUER_ID;
  delete process.env.APP_STORE_CONNECT_PRIVATE_KEY;
  delete process.env.APP_STORE_CONNECT_APP_ID;
});

async function freshImport() {
  vi.resetModules();
  return import("../appstoreconnect.js");
}

describe("mintToken", () => {
  it("produces a JWT with correct header and claims", async () => {
    const { mintToken, clearTokenCache } = await freshImport();
    clearTokenCache();

    const token = await mintToken();
    const header = decodeProtectedHeader(token);
    const claims = decodeJwt(token);

    expect(header.alg).toBe("ES256");
    expect(header.kid).toBe(TEST_KEY_ID);
    expect(header.typ).toBe("JWT");
    expect(claims.iss).toBe(TEST_ISSUER_ID);
    expect(claims.aud).toBe("appstoreconnect-v1");
    expect(typeof claims.iat).toBe("number");
    expect(typeof claims.exp).toBe("number");
    expect(claims.exp! - claims.iat!).toBe(20 * 60);
  });

  it("returns cached token on subsequent calls", async () => {
    const { mintToken, clearTokenCache } = await freshImport();
    clearTokenCache();

    const t1 = await mintToken();
    const t2 = await mintToken();
    expect(t1).toBe(t2);
  });
});

describe("inviteUser", () => {
  it("sends correct request body and returns invitationId", async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ data: { id: "inv-abc-123" } }),
    });
    vi.stubGlobal("fetch", mockFetch);

    const { inviteUser, clearTokenCache } = await freshImport();
    clearTokenCache();

    const result = await inviteUser({
      email: "jerry@dead.net",
      firstName: "Jerry",
      lastName: "Garcia",
    });

    expect(result).toEqual({ ok: true, invitationId: "inv-abc-123" });

    const [url, init] = mockFetch.mock.calls[0];
    expect(url).toBe("https://api.appstoreconnect.apple.com/v1/userInvitations");
    expect(init.method).toBe("POST");

    const body = JSON.parse(init.body);
    expect(body.data.type).toBe("userInvitations");
    expect(body.data.attributes.email).toBe("jerry@dead.net");
    expect(body.data.attributes.roles).toEqual(["SALES"]);
    expect(body.data.attributes.allAppsVisible).toBe(false);
    expect(body.data.attributes.provisioningAllowed).toBe(false);
    expect(body.data.relationships.visibleApps.data[0].id).toBe(TEST_APP_ID);
  });

  it("handles 409 already-invited gracefully", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 409,
        json: async () => ({
          errors: [{ detail: "A user invitation already exists for this email." }],
        }),
      }),
    );

    const { inviteUser, clearTokenCache } = await freshImport();
    clearTokenCache();

    const result = await inviteUser({
      email: "bob@dead.net",
      firstName: "Bob",
      lastName: "Weir",
    });

    expect(result).toEqual({ ok: false, reason: "already_invited" });
  });

  it("handles 409 already-a-member gracefully", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 409,
        json: async () => ({
          errors: [{ detail: "This user is already a member of your team." }],
        }),
      }),
    );

    const { inviteUser, clearTokenCache } = await freshImport();
    clearTokenCache();

    const result = await inviteUser({
      email: "phil@dead.net",
      firstName: "Phil",
      lastName: "Lesh",
    });

    expect(result).toEqual({ ok: false, reason: "already_member" });
  });

  it("throws on non-409 errors", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 403,
        text: async () => "Forbidden",
      }),
    );

    const { inviteUser, clearTokenCache } = await freshImport();
    clearTokenCache();

    await expect(
      inviteUser({ email: "x@y.com", firstName: "X", lastName: "Y" }),
    ).rejects.toThrow("ASC inviteUser failed (403)");
  });
});

describe("deleteInvitation", () => {
  it("succeeds on 204", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({ ok: true, status: 204 }),
    );

    const { deleteInvitation, clearTokenCache } = await freshImport();
    clearTokenCache();
    await expect(deleteInvitation("inv-123")).resolves.toBeUndefined();
  });

  it("treats 404 as success", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({ ok: false, status: 404 }),
    );

    const { deleteInvitation, clearTokenCache } = await freshImport();
    clearTokenCache();
    await expect(deleteInvitation("inv-gone")).resolves.toBeUndefined();
  });
});

describe("listInvitations", () => {
  it("paginates through multiple pages", async () => {
    const mockFetch = vi
      .fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          data: [{ id: "inv-1", type: "userInvitations" }],
          links: { next: "https://api.appstoreconnect.apple.com/v1/userInvitations?cursor=abc" },
        }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          data: [{ id: "inv-2", type: "userInvitations" }],
          links: {},
        }),
      });
    vi.stubGlobal("fetch", mockFetch);

    const { listInvitations, clearTokenCache } = await freshImport();
    clearTokenCache();

    const results = await listInvitations();
    expect(results).toHaveLength(2);
    expect(results[0].id).toBe("inv-1");
    expect(results[1].id).toBe("inv-2");
    expect(mockFetch).toHaveBeenCalledTimes(2);
  });
});
