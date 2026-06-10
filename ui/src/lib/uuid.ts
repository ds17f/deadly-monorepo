/**
 * Generate a UUID that works outside secure contexts.
 *
 * `crypto.randomUUID()` only exists in a *secure context* — HTTPS or
 * `localhost`. Over a plain-HTTP LAN origin (e.g. `http://192.168.x.x:8080`,
 * used when reaching the dev/beta web app from another device) it is
 * `undefined`, which previously crashed login: `ConnectProvider` called
 * `crypto.randomUUID()` to mint its device id and threw
 * `TypeError: crypto.randomUUID is not a function`.
 *
 * Falls back to a `getRandomValues`-based UUIDv4, then to a non-crypto string
 * as a last resort (only ids, never secrets).
 */
export function randomUUID(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  if (typeof crypto !== "undefined" && typeof crypto.getRandomValues === "function") {
    const b = crypto.getRandomValues(new Uint8Array(16));
    b[6] = (b[6] & 0x0f) | 0x40; // version 4
    b[8] = (b[8] & 0x3f) | 0x80; // variant 10
    const hex = Array.from(b, (x) => x.toString(16).padStart(2, "0")).join("");
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}-${Math.random().toString(16).slice(2)}`;
}
