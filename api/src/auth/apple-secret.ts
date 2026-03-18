import { SignJWT, importPKCS8 } from "jose";

let cachedSecret: string | undefined;
let cachedExpiry = 0;

/**
 * Generate an Apple client secret JWT (ES256, valid for 6 months).
 * Caches the result until 1 hour before expiry.
 */
export async function generateAppleSecret(): Promise<string> {
  const now = Math.floor(Date.now() / 1000);

  if (cachedSecret && now < cachedExpiry - 3600) {
    return cachedSecret;
  }

  const teamId = process.env.APPLE_TEAM_ID!;
  const clientId = process.env.APPLE_CLIENT_ID!;
  const keyId = process.env.APPLE_KEY_ID!;
  const privateKeyPem = process.env.APPLE_PRIVATE_KEY!.replace(/\\n/g, "\n");

  const key = await importPKCS8(privateKeyPem, "ES256");
  const exp = now + 86400 * 180; // 6 months

  cachedSecret = await new SignJWT({})
    .setAudience("https://appleid.apple.com")
    .setIssuer(teamId)
    .setSubject(clientId)
    .setIssuedAt(now)
    .setExpirationTime(exp)
    .setProtectedHeader({ alg: "ES256", kid: keyId })
    .sign(key);

  cachedExpiry = exp;
  return cachedSecret;
}
