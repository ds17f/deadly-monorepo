"use client";

import { useEffect, useState } from "react";
import { useNotifications } from "@/components/notifications/NotificationsProvider";
import { linkify } from "@/lib/linkify";
import { SUBREDDIT_HANDLE, SUBREDDIT_URL } from "@/lib/community";
import type { CachedNotification, NotificationCategory } from "@/lib/notifications";
import {
  trackNotificationOpen,
  trackNotificationImpression,
  trackNotificationLinkTap,
  trackNotificationCommunityTap,
} from "@/lib/notificationAnalytics";

// Ids already counted as an impression this session (module scope so it
// survives re-renders), deduping re-renders/scrolls into one impression.
const impressed = new Set<number>();

/** Fires exactly one `notification_impression` the first time a row renders. */
function Impression({ message }: { message: CachedNotification }) {
  useEffect(() => {
    if (!impressed.has(message.id)) {
      impressed.add(message.id);
      trackNotificationImpression(message);
    }
  }, [message.id]); // eslint-disable-line react-hooks/exhaustive-deps
  return null;
}

/** Track a click on a link inside linkified body text (delegated). */
function onBodyClick(id: number, e: React.MouseEvent<HTMLParagraphElement>) {
  const anchor = (e.target as HTMLElement).closest("a");
  if (anchor) trackNotificationLinkTap(id, anchor.getAttribute("href") ?? "");
}

// Monochrome outline glyphs (heroicons-style, currentColor) — they inherit the
// muted row text color so they read as quiet markers, not colorful stickers.
const CATEGORY_PATH: Record<NotificationCategory, string> = {
  general:
    "M10.34 15.84c-.688-.06-1.386-.09-2.09-.09H7.5a4.5 4.5 0 110-9h.75c.704 0 1.402-.03 2.09-.09m0 9.18c.253.962.584 1.892.985 2.783.247.55.06 1.21-.463 1.511l-.657.38c-.551.318-1.26.117-1.527-.461a20.845 20.845 0 01-1.44-4.282m3.102.069a18.03 18.03 0 01-.59-4.59c0-1.586.205-3.124.59-4.59m0 9.18a23.848 23.848 0 018.835 2.535M10.34 6.66a23.847 23.847 0 008.835-2.535m0 0A23.74 23.74 0 0018.795 3m.38 1.125a23.91 23.91 0 011.014 5.395m-1.014 8.855c-.118.38-.245.754-.38 1.125m.38-1.125a23.91 23.91 0 001.014-5.395m0-3.46c.495.413.811 1.035.811 1.73s-.316 1.317-.811 1.73m0-3.46a24.347 24.347 0 010 3.46",
  release:
    "M15.59 14.37a6 6 0 01-5.84 7.38v-4.8m5.84-2.58a14.98 14.98 0 006.16-12.12A14.98 14.98 0 009.631 8.41m5.96 5.96a14.926 14.926 0 01-5.841 2.58m-.119-8.54a6 6 0 00-7.381 5.84h4.8m2.581-5.84a14.927 14.927 0 00-2.58 5.84m2.699 2.7c-.103.021-.207.041-.311.06a15.09 15.09 0 01-2.448-2.448 14.9 14.9 0 01.06-.312m-2.24 2.39a4.493 4.493 0 00-1.757 4.306 4.493 4.493 0 004.306-1.758M16.5 9a1.5 1.5 0 11-3 0 1.5 1.5 0 013 0z",
  feature:
    "M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.456 2.456L21.75 6l-1.035.259a3.375 3.375 0 00-2.456 2.456zM16.894 20.567L16.5 21.75l-.394-1.183a2.25 2.25 0 00-1.423-1.423L13.5 18.75l1.183-.394a2.25 2.25 0 001.423-1.423l.394-1.183.394 1.183a2.25 2.25 0 001.423 1.423l1.183.394-1.183.394a2.25 2.25 0 00-1.423 1.423z",
  outage:
    "M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z",
};

function CategoryIcon({ category, className }: { category: NotificationCategory; className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden>
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.6} d={CATEGORY_PATH[category]} />
    </svg>
  );
}

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

/** First line / opening of the body, for the collapsed row preview. */
function preview(body: string): string {
  const firstLine = body.split("\n").find((l) => l.trim().length > 0) ?? "";
  return firstLine.length > 120 ? `${firstLine.slice(0, 120)}…` : firstLine;
}

export default function NotificationsPage() {
  const { active, archived, markRead, markAllRead, archive, archiveAll } = useNotifications();
  const [showArchive, setShowArchive] = useState(false);
  const [expanded, setExpanded] = useState<number | null>(null);

  const list = showArchive ? archived : active;
  const hasUnread = active.some((m) => m.seen_at == null);

  const onToggle = (m: CachedNotification) => {
    if (expanded === m.id) {
      setExpanded(null);
    } else {
      setExpanded(m.id);
      trackNotificationOpen(m);
      if (!showArchive && m.seen_at == null) markRead(m.id); // tap to read
    }
  };

  return (
    <div className="mx-auto max-w-2xl">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-bold">{showArchive ? "Archived" : "Notifications"}</h1>
        <button
          onClick={() => {
            setShowArchive((v) => !v);
            setExpanded(null);
          }}
          className="text-sm text-zinc-400 hover:text-zinc-200"
        >
          {showArchive ? "← Inbox" : archived.length > 0 ? `Archived (${archived.length})` : ""}
        </button>
      </div>

      {/* Bulk actions (inbox only) */}
      {!showArchive && active.length > 0 && (
        <div className="mb-3 flex gap-3 text-sm">
          <button
            onClick={markAllRead}
            disabled={!hasUnread}
            className="text-zinc-400 hover:text-zinc-200 disabled:opacity-40"
          >
            Mark all read
          </button>
          <span className="text-zinc-700">·</span>
          <button onClick={archiveAll} className="text-zinc-400 hover:text-zinc-200">
            Archive all
          </button>
        </div>
      )}

      <div className="divide-y divide-zinc-800 overflow-hidden rounded-lg border border-zinc-800 bg-deadly-surface">
        {list.length === 0 ? (
          <p className="px-4 py-10 text-center text-sm text-zinc-500">
            {showArchive ? "Nothing archived." : "You're all caught up."}
          </p>
        ) : (
          list.map((m) => {
            const unread = !showArchive && m.seen_at == null;
            const isOpen = expanded === m.id;
            return (
              <div key={m.id} className="px-4 py-3 transition hover:bg-zinc-800/30">
                <Impression message={m} />
                <button
                  onClick={() => onToggle(m)}
                  className="flex w-full items-start gap-3 text-left"
                >
                  <CategoryIcon
                    category={m.category ?? "general"}
                    className="mt-0.5 h-4 w-4 shrink-0 text-zinc-500"
                  />
                  {unread && (
                    <span className="mt-2 h-2 w-2 shrink-0 rounded-full bg-deadly-accent" aria-label="unread" />
                  )}
                  <span className="min-w-0 flex-1">
                    <span className="flex items-baseline justify-between gap-2">
                      <span className={`truncate text-sm ${unread ? "font-semibold text-zinc-100" : "text-zinc-300"}`}>
                        {m.title}
                      </span>
                      <span className="shrink-0 text-[11px] text-zinc-500">{timeAgo(m.created_at)}</span>
                    </span>
                    {!isOpen && (
                      <span className="mt-0.5 block truncate text-sm text-zinc-400">{preview(m.body)}</span>
                    )}
                  </span>
                </button>

                {isOpen && (
                  <div className="mt-2 pl-9">
                    <p
                      className="whitespace-pre-wrap break-words text-sm text-zinc-300"
                      onClick={(e) => onBodyClick(m.id, e)}
                    >
                      {linkify(m.body)}
                    </p>
                    {!showArchive && (
                      <button
                        onClick={() => {
                          archive(m.id);
                          setExpanded(null);
                        }}
                        className="mt-3 rounded bg-zinc-800 px-3 py-1 text-xs text-zinc-300 hover:bg-zinc-700"
                      >
                        Archive
                      </button>
                    )}
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>

      {/* Community footer (decision I) */}
      <div className="mt-6 text-center text-sm text-zinc-500">
        More at{" "}
        <a
          href={SUBREDDIT_URL}
          target="_blank"
          rel="noopener noreferrer"
          onClick={() => trackNotificationCommunityTap()}
          className="text-deadly-accent underline underline-offset-2 hover:text-white"
        >
          {SUBREDDIT_HANDLE}
        </a>{" "}
        →
      </div>
    </div>
  );
}
