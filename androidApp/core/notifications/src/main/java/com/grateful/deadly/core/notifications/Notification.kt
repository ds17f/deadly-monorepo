package com.grateful.deadly.core.notifications

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * In-app messaging — Android consume models.
 *
 * The server is a dumb publisher; ALL seen/dismissed state lives on the client
 * (here, persisted as a JSON blob in SharedPreferences). We keep a cache of
 * fetched messages plus a cursor (the last message id pulled) and fetch deltas
 * on foreground. Faithful port of `ui/src/lib/notifications.ts`.
 * See PLANS/in-app-messaging.md.
 */

/** This client's platform, for targeting (decision E). */
const val PLATFORM = "android"

/** Wire shape from GET /api/notifications (snake_case on the wire). */
@Serializable
data class NotificationWire(
    val id: Long,
    val title: String,
    val body: String,
    val level: String = "info", // "info" | "warn" (severity/color)
    val category: String = "general", // general | release | feature | outage
    @SerialName("min_version") val minVersion: String? = null,
    @SerialName("max_version") val maxVersion: String? = null,
    val platforms: List<String>? = null, // null/empty = all platforms
    @SerialName("created_at") val createdAt: Long,
    @SerialName("expires_at") val expiresAt: Long? = null,
)

/** Server response envelope: messages after the cursor + the new high-water id. */
@Serializable
data class NotificationFetchResult(
    val messages: List<NotificationWire> = emptyList(),
    val cursor: Long = 0,
    /**
     * Authoritative set of currently-active ids — clients prune any cached
     * message not in it (the only signal of a server-side retire). Null for
     * older servers (= don't prune on that basis). ADR-0015.
     */
    val activeIds: List<Long>? = null,
)

/**
 * Per-user read/dismiss overlay row (ADR-0015). Wire timestamps are unix
 * SECONDS (the user-data API convention); the local store keeps milliseconds,
 * so the coordinator converts at the boundary. camelCase matches the server's
 * authed `/api/user/notifications/state` payload.
 */
@Serializable
data class NotificationStateRow(
    val notificationId: Long,
    val seenAt: Long? = null,
    val dismissedAt: Long? = null,
)

/** A cached message plus local-only state. Never sent back to the server. */
@Serializable
data class CachedNotification(
    val id: Long,
    val title: String,
    val body: String,
    val level: String,
    val category: String = "general",
    val minVersion: String? = null,
    val maxVersion: String? = null,
    val platforms: List<String>? = null,
    val createdAt: Long,
    val expiresAt: Long?,
    val seenAt: Long? = null,
    val dismissedAt: Long? = null,
)

/**
 * Client-side targeting (decision E): a message is eligible only if this
 * platform is in its platform list (or it has none) AND this app's version is
 * within the message's [minVersion, maxVersion] range (nulls = unbounded).
 */
fun CachedNotification.isEligible(appVersion: String): Boolean {
    val plats = platforms
    if (!plats.isNullOrEmpty() && PLATFORM !in plats) return false
    if (minVersion != null && compareVersions(appVersion, minVersion) < 0) return false
    if (maxVersion != null && compareVersions(appVersion, maxVersion) > 0) return false
    return true
}

/** Active inbox: eligible, not dismissed, not expired, newest first. */
fun List<CachedNotification>.active(
    appVersion: String,
    now: Long = System.currentTimeMillis(),
): List<CachedNotification> =
    filter { it.isEligible(appVersion) && it.dismissedAt == null && !it.isExpired(now) }
        .sortedByDescending { it.createdAt }

/** Archived (dismissed) view: eligible, newest first. */
fun List<CachedNotification>.dismissed(appVersion: String): List<CachedNotification> =
    filter { it.isEligible(appVersion) && it.dismissedAt != null }
        .sortedByDescending { it.createdAt }

/**
 * Unread badge count: eligible, active, not yet seen. Persistent — only
 * read/archive/mark-all-read clear it (decision A).
 */
fun List<CachedNotification>.unreadCount(
    appVersion: String,
    now: Long = System.currentTimeMillis(),
): Int =
    count { it.isEligible(appVersion) && it.dismissedAt == null && it.seenAt == null && !it.isExpired(now) }

private fun CachedNotification.isExpired(now: Long): Boolean =
    expiresAt != null && expiresAt * 1000 < now

/**
 * Compare two dotted version strings numerically (e.g. "2.10.0" > "2.9.5").
 * Non-numeric/suffix parts are ignored; missing segments count as 0. Lenient by
 * design — a malformed bound shouldn't crash the feed.
 */
fun compareVersions(a: String, b: String): Int {
    val pa = a.split(".")
    val pb = b.split(".")
    val n = maxOf(pa.size, pb.size)
    for (i in 0 until n) {
        val x = pa.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
        val y = pb.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
        if (x != y) return x.compareTo(y)
    }
    return 0
}
