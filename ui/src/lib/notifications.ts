// In-app messaging — web client logic.
//
// The server is a dumb publisher; ALL seen/dismissed state lives here in
// localStorage (per browser). We keep a cache of fetched messages plus a
// cursor (the last message id we've pulled), and fetch deltas on foreground.
// See PLANS/in-app-messaging.md.

export type NotificationLevel = "info" | "warn";
export type NotificationCategory = "general" | "release" | "feature" | "outage";
export type NotificationPlatform = "ios" | "android" | "web";

/** Wire shape from GET /api/notifications. */
export interface NotificationWire {
  id: number;
  title: string;
  body: string;
  level: NotificationLevel;
  category?: NotificationCategory;
  min_version?: string | null;
  max_version?: string | null;
  platforms?: NotificationPlatform[] | null;
  created_at: number;
  expires_at: number | null;
}

/** Cached message + local-only state. */
export interface CachedNotification extends NotificationWire {
  seen_at: number | null;
  dismissed_at: number | null;
}

/**
 * Client-side targeting (decision E). The web app is continuously deployed, so
 * app-version bounds are N/A here — only platform targeting applies. A message
 * is eligible on web unless it explicitly targets platforms that exclude `web`.
 */
export function isEligible(m: NotificationWire): boolean {
  if (m.platforms && m.platforms.length > 0 && !m.platforms.includes("web")) {
    return false;
  }
  return true;
}

interface NotifStore {
  cursor: number; // high-water id pulled so far; 0 = brand-new client
  messages: Record<number, CachedNotification>;
}

const STORAGE_KEY = "deadly.notifications.v1";
// Drop dismissed messages from the local cache after this long.
const DISMISSED_TTL_MS = 90 * 24 * 60 * 60 * 1000;

const emptyStore = (): NotifStore => ({ cursor: 0, messages: {} });

export function loadStore(): NotifStore {
  if (typeof window === "undefined") return emptyStore();
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return emptyStore();
    const parsed = JSON.parse(raw) as NotifStore;
    if (typeof parsed.cursor !== "number" || typeof parsed.messages !== "object") {
      return emptyStore();
    }
    return parsed;
  } catch {
    return emptyStore();
  }
}

export function saveStore(store: NotifStore): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(store));
  } catch {
    /* quota / private mode — non-fatal */
  }
}

export async function fetchNotifications(
  cursor: number,
): Promise<{ messages: NotificationWire[]; cursor: number }> {
  const qs = cursor > 0 ? `?since=${cursor}` : "";
  const res = await fetch(`/api/notifications${qs}`, { credentials: "include" });
  if (!res.ok) throw new Error(`notifications fetch failed (${res.status})`);
  return res.json();
}

/**
 * Merge a fetch result into the store and prune. v2 (decision G): the
 * cold-start batch arrives **unread** like any delta — the badge shows the
 * active backlog. Targeting (eligibility) + expiry keep it relevant, and
 * "Mark all read" handles volume. Local seen/dismissed state is preserved
 * across re-fetches.
 */
export function mergeStore(
  store: NotifStore,
  fetched: { messages: NotificationWire[]; cursor: number },
): NotifStore {
  const now = Date.now();
  const messages = { ...store.messages };

  for (const m of fetched.messages) {
    const existing = messages[m.id];
    messages[m.id] = {
      ...m,
      // Preserve local state across re-fetches; new arrivals start unread.
      seen_at: existing?.seen_at ?? null,
      dismissed_at: existing?.dismissed_at ?? null,
    };
  }

  // Prune: expired messages and long-dismissed ones.
  for (const idStr of Object.keys(messages)) {
    const m = messages[Number(idStr)];
    const expired = m.expires_at != null && m.expires_at * 1000 < now;
    const staleDismissed = m.dismissed_at != null && now - m.dismissed_at > DISMISSED_TTL_MS;
    if (expired || staleDismissed) delete messages[Number(idStr)];
  }

  return { cursor: Math.max(store.cursor, fetched.cursor), messages };
}

/** Active inbox: eligible, not archived, newest first. */
export function activeMessages(store: NotifStore): CachedNotification[] {
  return Object.values(store.messages)
    .filter((m) => isEligible(m) && m.dismissed_at == null)
    .sort((a, b) => b.created_at - a.created_at);
}

/** Archived (dismissed) view: eligible, newest first. */
export function dismissedMessages(store: NotifStore): CachedNotification[] {
  return Object.values(store.messages)
    .filter((m) => isEligible(m) && m.dismissed_at != null)
    .sort((a, b) => b.created_at - a.created_at);
}

/**
 * Unread badge count: eligible, active, and not yet read. Persistent — only
 * read/archive/mark-all-read drop it (decision A); merely opening the inbox
 * no longer clears it.
 */
export function unreadCount(store: NotifStore): number {
  return Object.values(store.messages).filter(
    (m) => isEligible(m) && m.dismissed_at == null && m.seen_at == null,
  ).length;
}

/** Mark a single message read ("tap to read" — opening its detail). */
export function markRead(store: NotifStore, id: number): NotifStore {
  const m = store.messages[id];
  if (!m || m.seen_at != null) return store;
  return {
    ...store,
    messages: { ...store.messages, [id]: { ...m, seen_at: Date.now() } },
  };
}

export function markAllSeen(store: NotifStore): NotifStore {
  const now = Date.now();
  const messages = { ...store.messages };
  for (const idStr of Object.keys(messages)) {
    const m = messages[Number(idStr)];
    if (m.seen_at == null) messages[Number(idStr)] = { ...m, seen_at: now };
  }
  return { ...store, messages };
}

/** Archive (dismiss) a message: removes it from the active inbox. */
export function dismiss(store: NotifStore, id: number): NotifStore {
  const m = store.messages[id];
  if (!m) return store;
  return {
    ...store,
    messages: { ...store.messages, [id]: { ...m, dismissed_at: Date.now() } },
  };
}

/** Archive every active (eligible, not-yet-archived) message at once. */
export function archiveAll(store: NotifStore): NotifStore {
  const now = Date.now();
  const messages = { ...store.messages };
  for (const m of Object.values(store.messages)) {
    if (isEligible(m) && m.dismissed_at == null) {
      messages[m.id] = { ...m, dismissed_at: now };
    }
  }
  return { ...store, messages };
}
