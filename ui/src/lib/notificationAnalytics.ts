// The notifications analytics vocabulary in one place — thin wrappers over
// track() so call sites stay terse and every `notification_*` event name/prop
// is defined once. Mirrors the server allowlist in api/src/db/analytics.ts and
// the iOS/Android equivalents.
//
// Every message-scoped event carries `id` so the admin dashboard can aggregate
// engagement per notification.

import { track } from "@/lib/analytics";
import type { CachedNotification } from "@/lib/notifications";

export function trackNotificationReceived(m: CachedNotification, reason: string): void {
  track("notification_received", {
    id: m.id,
    category: m.category,
    level: m.level,
    reason,
  });
}

export function trackNotificationImpression(m: CachedNotification): void {
  track("notification_impression", { id: m.id, category: m.category, level: m.level });
}

export function trackNotificationOpen(m: CachedNotification): void {
  track("notification_open", {
    id: m.id,
    category: m.category,
    level: m.level,
    was_unread: m.seen_at == null,
  });
}

export function trackNotificationLinkTap(id: number, url: string): void {
  track("notification_link_tap", { id, url });
}

export function trackNotificationArchive(m: CachedNotification): void {
  track("notification_archive", { id: m.id, category: m.category });
}

export function trackNotificationToastShown(id: number, count: number): void {
  track("notification_toast_shown", { id, count });
}

export function trackNotificationToastTap(id: number): void {
  track("notification_toast_tap", { id });
}

export function trackNotificationMarkAllRead(count: number): void {
  track("notification_mark_all_read", { count });
}

export function trackNotificationArchiveAll(count: number): void {
  track("notification_archive_all", { count });
}

export function trackNotificationCommunityTap(): void {
  track("notification_community_tap");
}
