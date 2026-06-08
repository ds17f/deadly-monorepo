"use client";

import Link from "next/link";
import { useNotifications } from "./NotificationsProvider";

// Header bell + persistent unread badge. v2: tapping navigates to the full
// /notifications inbox page (no dropdown) — consistent with the mobile
// "real screen". The badge persists until messages are read/archived; opening
// the page no longer clears it. See PLANS/in-app-messaging.md.
export default function NotificationBell() {
  const { unread } = useNotifications();

  return (
    <Link
      href="/notifications"
      aria-label={unread > 0 ? `Notifications, ${unread} unread` : "Notifications"}
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
    </Link>
  );
}
