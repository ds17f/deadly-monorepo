"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";
import { useRouter } from "next/navigation";
import { useToast } from "@/components/ui/ToastProvider";
import {
  loadStore,
  saveStore,
  fetchNotifications,
  mergeStore,
  activeMessages,
  dismissedMessages,
  unreadCount,
  markRead as markReadStore,
  markAllSeen,
  dismiss as dismissStore,
  archiveAll as archiveAllStore,
  isEligible,
  type CachedNotification,
} from "@/lib/notifications";

// App-wide owner of the notifications store, polling, and the new-message
// toast. The always-mounted bell and the /notifications page both read/write
// through this one provider so the badge and the list never drift (decision
// B: bell + real inbox page share one in-memory store).
interface NotificationsContextValue {
  unread: number;
  active: CachedNotification[];
  archived: CachedNotification[];
  refresh: () => void;
  markRead: (id: number) => void;
  markAllRead: () => void;
  archive: (id: number) => void;
  archiveAll: () => void;
}

const NotificationsContext = createContext<NotificationsContextValue | null>(null);

export function useNotifications(): NotificationsContextValue {
  const ctx = useContext(NotificationsContext);
  if (!ctx) throw new Error("useNotifications must be used within NotificationsProvider");
  return ctx;
}

export default function NotificationsProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const [store, setStore] = useState(loadStore);
  const router = useRouter();
  const { showToast } = useToast();
  const lastFetch = useRef(0);
  // Mirror the latest store for the async fetch (avoids stale closures).
  const storeRef = useRef(store);
  storeRef.current = store;

  const apply = useCallback((next: ReturnType<typeof loadStore>) => {
    saveStore(next);
    setStore(next);
  }, []);

  const refresh = useCallback(async () => {
    lastFetch.current = Date.now();
    const prev = storeRef.current;
    try {
      const res = await fetchNotifications(prev.cursor);
      const existing = new Set(Object.keys(prev.messages).map(Number));
      apply(mergeStore(prev, res));

      // Toast only for genuinely new, eligible, unread arrivals — and never on
      // a cold start (decision C). Surfaces the newest one; tap opens the inbox.
      if (prev.cursor > 0) {
        const fresh = res.messages
          .filter((m) => isEligible(m) && !existing.has(m.id))
          .sort((a, b) => b.created_at - a.created_at);
        if (fresh.length > 0) {
          const label = fresh.length === 1 ? fresh[0].title : `${fresh.length} new messages`;
          showToast(`New: ${label}`, () => router.push("/notifications"));
        }
      }
    } catch {
      /* offline — keep the cache */
    }
  }, [apply, router, showToast]);

  // Initial load + poll on focus/visibility only (decision H — no idle timer).
  useEffect(() => {
    refresh();
    const onFocus = () => {
      if (Date.now() - lastFetch.current > 30_000) refresh();
    };
    window.addEventListener("focus", onFocus);
    document.addEventListener("visibilitychange", onFocus);
    return () => {
      window.removeEventListener("focus", onFocus);
      document.removeEventListener("visibilitychange", onFocus);
    };
  }, [refresh]);

  const markRead = useCallback((id: number) => apply(markReadStore(storeRef.current, id)), [apply]);
  const markAllRead = useCallback(() => apply(markAllSeen(storeRef.current)), [apply]);
  const archive = useCallback((id: number) => apply(dismissStore(storeRef.current, id)), [apply]);
  const archiveAll = useCallback(() => apply(archiveAllStore(storeRef.current)), [apply]);

  const value: NotificationsContextValue = {
    unread: unreadCount(store),
    active: activeMessages(store),
    archived: dismissedMessages(store),
    refresh,
    markRead,
    markAllRead,
    archive,
    archiveAll,
  };

  return (
    <NotificationsContext.Provider value={value}>
      {children}
    </NotificationsContext.Provider>
  );
}
