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
    /** Enqueue a favorite-song change by local row id. */
    fun enqueueAndPushFavoriteSong(localId: Long)
    /** Enqueue a recent-show play (refId is the showId). Issue 4. */
    fun enqueueAndPushRecent(showId: String)
    /** Enqueue a review change (refId is the showId). The flusher reads the
     *  review row + its player tags at push time; a tombstone becomes DELETE. */
    fun enqueueAndPushReview(showId: String)
    /** Enqueue a recording-preference change (refId is the showId). The flusher
     *  reads the recording_preferences row at push time; an absent/tombstoned
     *  row becomes a DELETE. */
    fun enqueueAndPushRecordingPref(showId: String)
    /** Enqueue all local favorites (shows + songs) + top recents, then flush.
     *  Backs the one-time startup backfill and a manual "Sync now". */
    suspend fun enqueueAllLocalAndFlush(): List<PushResult>
    suspend fun flushPending(): List<PushResult>
    suspend fun pendingCount(): Int
}

data class PushResult(
    val kind: String,
    val refId: String,
    val operation: String,   // "PUT" / "DELETE" / "NOOP"
    val success: Boolean,
    val error: String?,
)
