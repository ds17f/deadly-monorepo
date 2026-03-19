import { EncryptJWT, jwtDecrypt } from "jose";

const AUTH_SECRET = process.env.AUTH_SECRET;

// Auth.js v5 uses JWE with dir + A256CBC-HS512, which requires a 512-bit key
// derived via HKDF. We replicate Auth.js's own key derivation for correctness.
let encryptionKey: Uint8Array | null = null;

export async function getSecretKey(): Promise<Uint8Array> {
  if (encryptionKey) return encryptionKey;
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

export async function decodeJwt(token: string): Promise<Record<string, unknown> | null> {
  try {
    const key = await getSecretKey();
    const { payload } = await jwtDecrypt(token, key);
    return payload as Record<string, unknown>;
  } catch (err) {
    return null;
  }
}

/** Mint a JWE token compatible with the existing Bearer token middleware. */
export async function mintToken(payload: {
  accountId: string;
  authUserId: string;
  email: string;
  name?: string | null;
}): Promise<string> {
  const key = await getSecretKey();
  const now = Math.floor(Date.now() / 1000);
  return new EncryptJWT({
    accountId: payload.accountId,
    authUserId: payload.authUserId,
    email: payload.email,
    name: payload.name ?? undefined,
    sub: payload.authUserId,
    iat: now,
    exp: now + 365 * 24 * 60 * 60, // 1 year
  })
    .setProtectedHeader({ alg: "dir", enc: "A256CBC-HS512" })
    .setIssuedAt(now)
    .setExpirationTime(now + 365 * 24 * 60 * 60)
    .encrypt(key);
}
