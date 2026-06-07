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

/** Wire shape from GET /api/notifications (snake_case on the wire). */
@Serializable
data class NotificationWire(
    val id: Long,
    val title: String,
    val body: String,
    val level: String = "info", // "info" | "warn" (cosmetic)
    @SerialName("created_at") val createdAt: Long,
    @SerialName("expires_at") val expiresAt: Long? = null,
)

/** Server response envelope: messages after the cursor + the new high-water id. */
@Serializable
data class NotificationFetchResult(
    val messages: List<NotificationWire> = emptyList(),
    val cursor: Long = 0,
)

/** A cached message plus local-only state. Never sent back to the server. */
@Serializable
data class CachedNotification(
    val id: Long,
    val title: String,
    val body: String,
    val level: String,
    val createdAt: Long,
    val expiresAt: Long?,
    val seenAt: Long? = null,
    val dismissedAt: Long? = null,
)

/** Active inbox: not dismissed and not expired, newest first. */
fun List<CachedNotification>.active(now: Long = System.currentTimeMillis()): List<CachedNotification> =
    filter { it.dismissedAt == null && !it.isExpired(now) }
        .sortedByDescending { it.createdAt }

/** Dismissed archive, newest first. */
fun List<CachedNotification>.dismissed(): List<CachedNotification> =
    filter { it.dismissedAt != null }
        .sortedByDescending { it.createdAt }

/** Unread badge count: active and not yet seen. */
fun List<CachedNotification>.unreadCount(now: Long = System.currentTimeMillis()): Int =
    count { it.dismissedAt == null && it.seenAt == null && !it.isExpired(now) }

private fun CachedNotification.isExpired(now: Long): Boolean =
    expiresAt != null && expiresAt * 1000 < now
