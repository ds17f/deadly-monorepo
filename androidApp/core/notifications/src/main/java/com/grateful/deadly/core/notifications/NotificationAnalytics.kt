package com.grateful.deadly.core.notifications

import com.grateful.deadly.core.database.AnalyticsService

/**
 * The notifications analytics vocabulary in one place. Extension functions on
 * [AnalyticsService] so call sites (coordinator, view model, screen) stay terse
 * and every `notification_*` event name/prop is defined once. Mirrors the
 * server allowlist in api/src/db/analytics.ts and the iOS/web equivalents.
 *
 * Every message-scoped event carries `id` so the admin dashboard can aggregate
 * engagement per notification.
 */

fun AnalyticsService.trackNotificationReceived(m: CachedNotification, reason: String) =
    track(
        "notification_received",
        mapOf("id" to m.id, "category" to m.category, "level" to m.level, "reason" to reason),
    )

fun AnalyticsService.trackNotificationImpression(m: CachedNotification) =
    track(
        "notification_impression",
        mapOf("id" to m.id, "category" to m.category, "level" to m.level),
    )

fun AnalyticsService.trackNotificationOpen(m: CachedNotification) =
    track(
        "notification_open",
        mapOf(
            "id" to m.id,
            "category" to m.category,
            "level" to m.level,
            "was_unread" to (m.seenAt == null),
        ),
    )

fun AnalyticsService.trackNotificationLinkTap(id: Long, url: String) =
    track("notification_link_tap", mapOf("id" to id, "url" to url))

fun AnalyticsService.trackNotificationArchive(m: CachedNotification) =
    track("notification_archive", mapOf("id" to m.id, "category" to m.category))

fun AnalyticsService.trackNotificationToastShown(arrival: NewArrival) =
    track("notification_toast_shown", mapOf("id" to arrival.key, "count" to arrival.count))

fun AnalyticsService.trackNotificationToastTap(arrival: NewArrival) =
    track("notification_toast_tap", mapOf("id" to arrival.key))

fun AnalyticsService.trackNotificationMarkAllRead(count: Int) =
    track("notification_mark_all_read", mapOf("count" to count))

fun AnalyticsService.trackNotificationArchiveAll(count: Int) =
    track("notification_archive_all", mapOf("count" to count))

fun AnalyticsService.trackNotificationCommunityTap() =
    track("notification_community_tap")
