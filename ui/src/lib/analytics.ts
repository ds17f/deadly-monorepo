// Anonymous, first-party analytics client for the web app — the browser
// counterpart of the mobile AnalyticsService. Buffers events and flushes
// batches to POST /api/analytics (the same ingestion the apps use), so web
// listening / favoriting lands in the same analytics_events table and feeds
// trending (/api/popular), the admin dashboard (as platform "web"), and
// later Connect.
//
// No PII, no cookies: a random install id lives in localStorage; a session id
// lasts the tab's lifetime. The analytics key is NOT sent from the browser
// (a bundle can't keep a secret) — Caddy injects X-Analytics-Key on
// /api/analytics in production; in dev the API accepts keyless.

import { randomUUID } from "@/lib/uuid";

const IID_KEY = "deadly_iid";
const OPT_OUT_KEY = "deadly_analytics_off";
const ENDPOINT = "/api/analytics";
const FLUSH_INTERVAL_MS = 30_000;
const MAX_BUFFER = 50;

const PLATFORM = "web";
// Web has no app store version; the data version is the closest meaningful
// build marker and is already exposed to the client. Capped at the server's
// 20-char limit.
const APP_VERSION = (process.env.NEXT_PUBLIC_DATA_VERSION ?? "web").slice(0, 20);

interface AnalyticsEvent {
  event: string;
  ts: number;
  iid: string;
  sid: string;
  platform: string;
  app_version: string;
  props?: Record<string, unknown>;
}

let sessionId = "";
let buffer: AnalyticsEvent[] = [];
let wired = false;

const uuid = randomUUID;

// Stable per-browser anonymous id. Distinct from mobile IIDs by design — a
// web-only listener is a real, separate user (just not an app install).
function installId(): string {
  try {
    let id = localStorage.getItem(IID_KEY);
    if (!id) {
      id = uuid();
      localStorage.setItem(IID_KEY, id);
    }
    return id;
  } catch {
    return "anon";
  }
}

function enabled(): boolean {
  if (typeof window === "undefined") return false;
  try {
    return localStorage.getItem(OPT_OUT_KEY) !== "1";
  } catch {
    return true;
  }
}

function ensureWired(): void {
  if (wired || typeof window === "undefined") return;
  wired = true;
  sessionId = uuid();
  setInterval(() => flush(), FLUSH_INTERVAL_MS);
  // Flush on tab hide / unload so the last events of a session aren't lost.
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "hidden") flush(true);
  });
  window.addEventListener("pagehide", () => flush(true));
}

export function track(event: string, props?: Record<string, unknown>): void {
  if (typeof window === "undefined" || !enabled()) return;
  ensureWired();
  buffer.push({
    event,
    ts: Date.now(),
    iid: installId(),
    sid: sessionId,
    platform: PLATFORM,
    app_version: APP_VERSION,
    props: props && Object.keys(props).length ? props : undefined,
  });
  if (buffer.length >= MAX_BUFFER) flush();
}

// Send the buffered batch. `useBeacon` (tab-hide / unload) uses sendBeacon so
// it survives the page going away; otherwise a keepalive fetch. Fire-and-
// forget — analytics must never throw into the app or block anything.
export function flush(useBeacon = false): void {
  if (typeof window === "undefined" || buffer.length === 0) return;
  const events = buffer;
  buffer = [];
  const body = JSON.stringify({ events });
  try {
    if (useBeacon && navigator.sendBeacon) {
      navigator.sendBeacon(ENDPOINT, new Blob([body], { type: "application/json" }));
      return;
    }
    fetch(ENDPOINT, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body,
      keepalive: true,
    }).catch(() => {
      /* fire-and-forget */
    });
  } catch {
    /* swallow */
  }
}
