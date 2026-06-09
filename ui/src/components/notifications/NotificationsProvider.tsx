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
import {
  trackNotificationReceived,
  trackNotificationToastShown,
  trackNotificationToastTap,
  trackNotificationMarkAllRead,
  trackNotificationArchive,
  trackNotificationArchiveAll,
} from "@/lib/notificationAnalytics";

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

  const refresh = useCallback(
    async (reason: string) => {
      lastFetch.current = Date.now();
      const prev = storeRef.current;
      try {
        const res = await fetchNotifications(prev.cursor);
        const existing = new Set(Object.keys(prev.messages).map(Number));
        const next = mergeStore(prev, res);
        apply(next);

        // Genuinely new, eligible messages from this fetch (any reason, incl.
        // cold start) — the basis for `notification_received` analytics.
        const fresh = res.messages
          .filter((m) => isEligible(m) && !existing.has(m.id))
          .sort((a, b) => b.created_at - a.created_at)
          .map((m) => next.messages[m.id])
          .filter((m): m is CachedNotification => m != null);
        for (const m of fresh) trackNotificationReceived(m, reason);

        // Toast only for new arrivals on a delta — never the cold-start backlog
        // (decision C). Surfaces the newest one; tap opens the inbox.
        if (prev.cursor > 0 && fresh.length > 0) {
          const newest = fresh[0];
          const label = fresh.length === 1 ? newest.title : `${fresh.length} new messages`;
          trackNotificationToastShown(newest.id, fresh.length);
          showToast(`New: ${label}`, () => {
            trackNotificationToastTap(newest.id);
            router.push("/notifications");
          });
        }
      } catch {
        /* offline — keep the cache */
      }
    },
    [apply, router, showToast],
  );

  // Initial load + poll on focus/visibility only (decision H — no idle timer).
  useEffect(() => {
    refresh("cold_start");
    const onFocus = () => {
      if (Date.now() - lastFetch.current > 30_000) refresh("foreground");
    };
    window.addEventListener("focus", onFocus);
    document.addEventListener("visibilitychange", onFocus);
    return () => {
      window.removeEventListener("focus", onFocus);
      document.removeEventListener("visibilitychange", onFocus);
    };
  }, [refresh]);

  const markRead = useCallback((id: number) => apply(markReadStore(storeRef.current, id)), [apply]);
  const markAllRead = useCallback(() => {
    trackNotificationMarkAllRead(unreadCount(storeRef.current));
    apply(markAllSeen(storeRef.current));
  }, [apply]);
  const archive = useCallback((id: number) => {
    const m = storeRef.current.messages[id];
    if (m) trackNotificationArchive(m);
    apply(dismissStore(storeRef.current, id));
  }, [apply]);
  const archiveAll = useCallback(() => {
    trackNotificationArchiveAll(activeMessages(storeRef.current).length);
    apply(archiveAllStore(storeRef.current));
  }, [apply]);

  const value: NotificationsContextValue = {
    unread: unreadCount(store),
    active: activeMessages(store),
    archived: dismissedMessages(store),
    refresh: useCallback(() => refresh("refresh"), [refresh]),
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
