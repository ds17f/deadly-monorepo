// In-app messaging — web client logic.
//
// The server is a dumb publisher; ALL seen/dismissed state lives here in
// localStorage (per browser). We keep a cache of fetched messages plus a
// cursor (the last message id we've pulled), and fetch deltas on foreground.
// See PLANS/in-app-messaging.md.

export type NotificationLevel = "info" | "warn";

/** Wire shape from GET /api/notifications. */
export interface NotificationWire {
  id: number;
  title: string;
  body: string;
  level: NotificationLevel;
  created_at: number;
  expires_at: number | null;
}

/** Cached message + local-only state. */
export interface CachedNotification extends NotificationWire {
  seen_at: number | null;
  dismissed_at: number | null;
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
 * Merge a fetch result into the store and prune. On a cold start (the client
 * had no cursor yet) the batch is marked seen-not-dismissed so a fresh user
 * isn't slammed with unread badges — the messages appear in the inbox but
 * don't nag. Subsequent deltas arrive unseen (they raise the badge).
 */
export function mergeStore(
  store: NotifStore,
  fetched: { messages: NotificationWire[]; cursor: number },
): NotifStore {
  const coldStart = store.cursor === 0;
  const now = Date.now();
  const messages = { ...store.messages };

  for (const m of fetched.messages) {
    const existing = messages[m.id];
    messages[m.id] = {
      ...m,
      // Preserve local state across re-fetches; default for new arrivals.
      seen_at: existing?.seen_at ?? (coldStart ? now : null),
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

/** Active inbox: not dismissed, newest first. */
export function activeMessages(store: NotifStore): CachedNotification[] {
  return Object.values(store.messages)
    .filter((m) => m.dismissed_at == null)
    .sort((a, b) => b.created_at - a.created_at);
}

/** Dismissed archive, newest first. */
export function dismissedMessages(store: NotifStore): CachedNotification[] {
  return Object.values(store.messages)
    .filter((m) => m.dismissed_at != null)
    .sort((a, b) => b.created_at - a.created_at);
}

/** Unread badge count: active and not yet seen. */
export function unreadCount(store: NotifStore): number {
  return Object.values(store.messages).filter(
    (m) => m.dismissed_at == null && m.seen_at == null,
  ).length;
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

export function dismiss(store: NotifStore, id: number): NotifStore {
  const m = store.messages[id];
  if (!m) return store;
  return {
    ...store,
    messages: { ...store.messages, [id]: { ...m, dismissed_at: Date.now() } },
  };
}
