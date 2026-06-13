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
import { useAuth } from "@/contexts/AuthContext";
import {
  loadStore,
  saveStore,
  fetchNotifications,
  mergeStore,
  mergeSyncState,
  unsyncedState,
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
  fetchNotificationState,
  pushNotificationState,
  pushNotificationStateBulk,
} from "@/lib/userDataApi";
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
  const { user } = useAuth();
  const userId = user?.id;
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
        // For a signed-in user, pull the feed and the read/dismiss overlay
        // together so the unread set is correct on first paint — a message
        // already handled on another device never flashes unread (ADR-0015,
        // "state-before-surface").
        const [res, serverState] = await Promise.all([
          fetchNotifications(prev.cursor),
          userId ? fetchNotificationState().catch(() => null) : Promise.resolve(null),
        ]);
        const existing = new Set(Object.keys(prev.messages).map(Number));
        let next = mergeStore(prev, res);
        if (serverState) next = mergeSyncState(next, serverState);
        apply(next);

        // Offline catch-up: flush any local state the server hasn't recorded
        // (an eager push that failed while offline). No-op once converged.
        if (userId && serverState) {
          for (const row of unsyncedState(next, serverState)) {
            pushNotificationState(row.notificationId, {
              seenAt: row.seenAt ?? undefined,
              dismissedAt: row.dismissedAt ?? undefined,
            }).catch(() => {});
          }
        }

        // Genuinely new, eligible messages from this fetch (any reason, incl.
        // cold start) — the basis for `notification_received` analytics.
        const fresh = res.messages
          .filter((m) => isEligible(m) && !existing.has(m.id))
          .sort((a, b) => b.created_at - a.created_at)
          .map((m) => next.messages[m.id])
          .filter((m): m is CachedNotification => m != null);
        for (const m of fresh) trackNotificationReceived(m, reason);

        // Toast only for new arrivals on a delta — never the cold-start backlog
        // (decision C) and never a message already seen/dismissed elsewhere
        // (the overlay merge above). Surfaces the newest one; tap opens inbox.
        const toastable = fresh.filter((m) => m.seen_at == null && m.dismissed_at == null);
        if (prev.cursor > 0 && toastable.length > 0) {
          const newest = toastable[0];
          const label =
            toastable.length === 1 ? newest.title : `${toastable.length} new messages`;
          trackNotificationToastShown(newest.id, toastable.length);
          showToast(`New: ${label}`, () => {
            trackNotificationToastTap(newest.id);
            router.push("/notifications");
          });
        }
      } catch {
        /* offline — keep the cache */
      }
    },
    [apply, router, showToast, userId],
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

  // Each mutation is optimistic-local-first, then eagerly pushed to the server
  // for signed-in users (mirrors toggleFavorite) so other devices converge on
  // their next focus-refresh. Failures are caught by the focus catch-up flush.
  const nowSec = () => Math.floor(Date.now() / 1000);

  const markRead = useCallback((id: number) => {
    apply(markReadStore(storeRef.current, id));
    if (userId) pushNotificationState(id, { seenAt: nowSec() }).catch(() => {});
  }, [apply, userId]);
  const markAllRead = useCallback(() => {
    trackNotificationMarkAllRead(unreadCount(storeRef.current));
    apply(markAllSeen(storeRef.current));
    if (userId) pushNotificationStateBulk({ seenAt: nowSec() }).catch(() => {});
  }, [apply, userId]);
  const archive = useCallback((id: number) => {
    const m = storeRef.current.messages[id];
    if (m) trackNotificationArchive(m);
    apply(dismissStore(storeRef.current, id));
    if (userId) pushNotificationState(id, { dismissedAt: nowSec() }).catch(() => {});
  }, [apply, userId]);
  const archiveAll = useCallback(() => {
    trackNotificationArchiveAll(activeMessages(storeRef.current).length);
    apply(archiveAllStore(storeRef.current));
    if (userId) pushNotificationStateBulk({ dismissedAt: nowSec() }).catch(() => {});
  }, [apply, userId]);

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
