import { SignJWT, importPKCS8 } from "jose";

const BASE_URL = "https://api.appstoreconnect.apple.com";

let cachedToken: string | undefined;
let cachedTokenExpiry = 0;
let cachedKey: CryptoKey | undefined;

function getConfig() {
  return {
    keyId: process.env.APP_STORE_CONNECT_KEY_ID!,
    issuerId: process.env.APP_STORE_CONNECT_ISSUER_ID!,
    privateKey: process.env.APP_STORE_CONNECT_PRIVATE_KEY!,
    appId: process.env.APP_STORE_CONNECT_APP_ID!,
  };
}

async function getSigningKey(): Promise<CryptoKey> {
  if (cachedKey) return cachedKey;
  const { privateKey } = getConfig();
  const pem = Buffer.from(privateKey, "base64").toString("utf-8").replace(/\\n/g, "\n");
  cachedKey = await importPKCS8(pem, "ES256");
  return cachedKey;
}

export async function mintToken(): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && now < cachedTokenExpiry - 30) {
    return cachedToken;
  }

  const { keyId, issuerId } = getConfig();
  const key = await getSigningKey();
  const exp = now + 20 * 60; // 20 minutes

  cachedToken = await new SignJWT({})
    .setAudience("appstoreconnect-v1")
    .setIssuer(issuerId)
    .setIssuedAt(now)
    .setExpirationTime(exp)
    .setProtectedHeader({ alg: "ES256", kid: keyId, typ: "JWT" })
    .sign(key);

  cachedTokenExpiry = exp;
  return cachedToken;
}

export function clearTokenCache(): void {
  cachedToken = undefined;
  cachedTokenExpiry = 0;
  cachedKey = undefined;
}

async function ascFetch(path: string, init?: RequestInit): Promise<Response> {
  const token = await mintToken();
  return fetch(`${BASE_URL}${path}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      ...init?.headers,
    },
  });
}

export interface InviteUserParams {
  email: string;
  firstName: string;
  lastName: string;
}

export interface InviteResult {
  ok: true;
  invitationId: string;
}

export interface InviteConflict {
  ok: false;
  reason: "already_invited" | "already_member";
}

export async function inviteUser(
  params: InviteUserParams,
): Promise<InviteResult | InviteConflict> {
  const { appId } = getConfig();

  const res = await ascFetch("/v1/userInvitations", {
    method: "POST",
    body: JSON.stringify({
      data: {
        type: "userInvitations",
        attributes: {
          email: params.email,
          firstName: params.firstName,
          lastName: params.lastName,
          roles: ["SALES"],
          allAppsVisible: false,
          provisioningAllowed: false,
        },
        relationships: {
          visibleApps: {
            data: [{ type: "apps", id: appId }],
          },
        },
      },
    }),
  });

  if (res.status === 409) {
    const body = await res.json();
    const detail: string = body?.errors?.[0]?.detail ?? "";
    const reason = detail.toLowerCase().includes("already a member")
      ? "already_member"
      : "already_invited";
    return { ok: false, reason };
  }

  if (!res.ok) {
    const body = await res.text();
    throw new Error(`ASC inviteUser failed (${res.status}): ${body}`);
  }

  const body = await res.json();
  return { ok: true, invitationId: body.data.id };
}

export async function deleteInvitation(invitationId: string): Promise<void> {
  const res = await ascFetch(`/v1/userInvitations/${invitationId}`, {
    method: "DELETE",
  });
  if (!res.ok && res.status !== 404) {
    const body = await res.text();
    throw new Error(`ASC deleteInvitation failed (${res.status}): ${body}`);
  }
}

export async function deleteUser(userId: string): Promise<void> {
  const res = await ascFetch(`/v1/users/${userId}`, {
    method: "DELETE",
  });
  if (!res.ok && res.status !== 404) {
    const body = await res.text();
    throw new Error(`ASC deleteUser failed (${res.status}): ${body}`);
  }
}

interface ASCPaginatedResponse<T> {
  data: T[];
  links: { next?: string };
}

async function fetchAllPages<T>(path: string): Promise<T[]> {
  const results: T[] = [];
  let url: string | undefined = path;

  while (url) {
    const fullUrl = url.startsWith("http") ? url : `${BASE_URL}${url}`;
    const token = await mintToken();
    const res = await fetch(fullUrl, {
      headers: { Authorization: `Bearer ${token}` },
    });

    if (!res.ok) {
      const body = await res.text();
      throw new Error(`ASC GET ${url} failed (${res.status}): ${body}`);
    }

    const body: ASCPaginatedResponse<T> = await res.json();
    results.push(...body.data);
    url = body.links.next;
  }

  return results;
}

export interface ASCUserInvitation {
  id: string;
  type: "userInvitations";
  attributes: {
    email: string;
    firstName: string;
    lastName: string;
    roles: string[];
    expirationDate: string;
  };
}

export interface ASCUser {
  id: string;
  type: "users";
  attributes: {
    username: string;
    firstName: string;
    lastName: string;
    roles: string[];
  };
}

export async function listInvitations(): Promise<ASCUserInvitation[]> {
  return fetchAllPages<ASCUserInvitation>("/v1/userInvitations?limit=200");
}

export async function listUsers(): Promise<ASCUser[]> {
  return fetchAllPages<ASCUser>("/v1/users?limit=200");
}

export interface ASCBetaGroup {
  id: string;
  type: "betaGroups";
  attributes: {
    name: string;
    isInternalGroup: boolean;
    publicLinkEnabled: boolean;
  };
}

export async function listBetaGroups(): Promise<ASCBetaGroup[]> {
  const { appId } = getConfig();
  return fetchAllPages<ASCBetaGroup>(`/v1/apps/${appId}/betaGroups?limit=200`);
}

export async function addTesterToBetaGroup(
  betaGroupId: string,
  betaTesterId: string,
): Promise<void> {
  const res = await ascFetch(
    `/v1/betaGroups/${betaGroupId}/relationships/betaTesters`,
    {
      method: "POST",
      body: JSON.stringify({
        data: [{ type: "betaTesters", id: betaTesterId }],
      }),
    },
  );
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`ASC addTesterToBetaGroup failed (${res.status}): ${body}`);
  }
}

export async function removeFromBetaGroup(
  betaGroupId: string,
  betaTesterId: string,
): Promise<void> {
  const res = await ascFetch(
    `/v1/betaGroups/${betaGroupId}/relationships/betaTesters`,
    {
      method: "DELETE",
      body: JSON.stringify({
        data: [{ type: "betaTesters", id: betaTesterId }],
      }),
    },
  );
  if (!res.ok && res.status !== 404) {
    const body = await res.text();
    throw new Error(`ASC removeFromBetaGroup failed (${res.status}): ${body}`);
  }
}

export async function getBetaTesterByEmail(email: string): Promise<{ id: string } | undefined> {
  const res = await ascFetch(`/v1/betaTesters?filter[email]=${encodeURIComponent(email)}`);
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`ASC getBetaTesterByEmail failed (${res.status}): ${body}`);
  }
  const body: ASCPaginatedResponse<{ id: string }> = await res.json();
  return body.data[0];
}
