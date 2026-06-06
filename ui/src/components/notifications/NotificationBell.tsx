"use client";

import { useState, useRef, useEffect, useCallback } from "react";
import {
  loadStore,
  saveStore,
  fetchNotifications,
  mergeStore,
  activeMessages,
  dismissedMessages,
  unreadCount,
  markAllSeen,
  dismiss,
  type CachedNotification,
} from "@/lib/notifications";

// Re-fetch the feed at most this often while the tab stays focused.
const POLL_MS = 5 * 60 * 1000;

function timeAgo(createdAt: number): string {
  const secs = Math.floor(Date.now() / 1000 - createdAt);
  if (secs < 60) return "just now";
  const mins = Math.floor(secs / 60);
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  const days = Math.floor(hrs / 24);
  if (days < 7) return `${days}d ago`;
  return new Date(createdAt * 1000).toLocaleDateString("en-US", { month: "short", day: "numeric" });
}

export default function NotificationBell() {
  const [store, setStore] = useState(loadStore);
  const [open, setOpen] = useState(false);
  const [showArchive, setShowArchive] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  const lastFetch = useRef(0);

  const refresh = useCallback(async () => {
    lastFetch.current = Date.now();
    try {
      setStore((prev) => {
        // Fetch against the latest cursor, then merge into whatever's current.
        fetchNotifications(prev.cursor)
          .then((res) =>
            setStore((cur) => {
              const next = mergeStore(cur, res);
              saveStore(next);
              return next;
            }),
          )
          .catch(() => {});
        return prev;
      });
    } catch {
      /* offline — keep the cache */
    }
  }, []);

  // Initial load + poll while focused.
  useEffect(() => {
    refresh();
    const onFocus = () => {
      if (Date.now() - lastFetch.current > 30_000) refresh();
    };
    const interval = setInterval(() => {
      if (document.visibilityState === "visible") refresh();
    }, POLL_MS);
    window.addEventListener("focus", onFocus);
    document.addEventListener("visibilitychange", onFocus);
    return () => {
      clearInterval(interval);
      window.removeEventListener("focus", onFocus);
      document.removeEventListener("visibilitychange", onFocus);
    };
  }, [refresh]);

  // Close on outside click.
  useEffect(() => {
    if (!open) return;
    const onClick = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", onClick);
    return () => document.removeEventListener("mousedown", onClick);
  }, [open]);

  const toggleOpen = () => {
    setOpen((wasOpen) => {
      const next = !wasOpen;
      // Opening clears the unread badge.
      if (next) {
        setStore((cur) => {
          const seen = markAllSeen(cur);
          saveStore(seen);
          return seen;
        });
      }
      return next;
    });
  };

  const onDismiss = (id: number) => {
    setStore((cur) => {
      const next = dismiss(cur, id);
      saveStore(next);
      return next;
    });
  };

  const unread = unreadCount(store);
  const active = activeMessages(store);
  const archive = dismissedMessages(store);
  const list = showArchive ? archive : active;

  return (
    <div className="relative" ref={menuRef}>
      <button
        onClick={toggleOpen}
        aria-label="Notifications"
        className="relative flex h-8 w-8 items-center justify-center rounded-md text-white/70 transition hover:bg-white/10 hover:text-white"
      >
        <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={1.8}
            d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"
          />
        </svg>
        {unread > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-deadly-red px-1 text-[10px] font-bold leading-none text-white">
            {unread > 9 ? "9+" : unread}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 z-50 mt-2 w-80 max-w-[calc(100vw-1rem)] overflow-hidden rounded-lg border border-zinc-800 bg-deadly-surface shadow-xl">
          <div className="flex items-center justify-between border-b border-zinc-800 px-4 py-2.5">
            <span className="text-sm font-medium text-zinc-100">
              {showArchive ? "Dismissed" : "Notifications"}
            </span>
            <button
              onClick={() => setShowArchive((v) => !v)}
              className="text-xs text-zinc-400 hover:text-zinc-200"
            >
              {showArchive ? "← Back" : archive.length > 0 ? "View dismissed" : ""}
            </button>
          </div>

          <div className="max-h-[60vh] overflow-y-auto">
            {list.length === 0 ? (
              <p className="px-4 py-8 text-center text-sm text-zinc-500">
                {showArchive ? "Nothing dismissed." : "You're all caught up."}
              </p>
            ) : (
              list.map((m) => (
                <NotificationItem
                  key={m.id}
                  message={m}
                  archived={showArchive}
                  onDismiss={() => onDismiss(m.id)}
                />
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function NotificationItem({
  message,
  archived,
  onDismiss,
}: {
  message: CachedNotification;
  archived: boolean;
  onDismiss: () => void;
}) {
  const accent = message.level === "warn" ? "bg-yellow-500" : "bg-deadly-accent";
  return (
    <div className="flex gap-3 border-b border-zinc-800/60 px-4 py-3 last:border-b-0 hover:bg-zinc-800/30">
      <span className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${accent}`} />
      <div className="min-w-0 flex-1">
        <div className="flex items-baseline justify-between gap-2">
          <p className="truncate text-sm font-medium text-zinc-100">{message.title}</p>
          <span className="shrink-0 text-[11px] text-zinc-500">{timeAgo(message.created_at)}</span>
        </div>
        <p className="mt-0.5 whitespace-pre-wrap break-words text-sm text-zinc-400">{message.body}</p>
      </div>
      {!archived && (
        <button
          onClick={onDismiss}
          aria-label="Dismiss"
          className="self-start text-zinc-600 transition hover:text-zinc-300"
        >
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      )}
    </div>
  );
}
