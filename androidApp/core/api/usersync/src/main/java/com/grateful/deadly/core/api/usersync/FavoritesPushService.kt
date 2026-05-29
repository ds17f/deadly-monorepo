package com.grateful.deadly.core.api.usersync

/**
 * Bridges the local sync_outbox to the server for favorite shows.
 * Issue 3a of PLANS/mobile-server-sync.md.
 *
 * - FavoritesRepository calls enqueueAndPush(showId) after every local
 *   add/remove. That writes to sync_outbox and kicks off a background flush.
 * - The dev "Push pending favorites" button calls flushPending() directly.
 */
interface FavoritesPushService {
    fun enqueueAndPush(showId: String)
    suspend fun flushPending(): List<PushResult>
    suspend fun pendingCount(): Int
}

data class PushResult(
    val refId: String,
    val operation: String,   // "PUT" / "DELETE" / "NOOP"
    val success: Boolean,
    val error: String?,
)
