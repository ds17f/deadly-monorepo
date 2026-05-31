package com.grateful.deadly.core.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per pending server push. Unique on (kind, refId) — re-enqueue
 * of the same row collapses to a single entry.
 * See PLANS/mobile-server-sync.md.
 */
@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["kind", "refId"], unique = true),
        Index(value = ["kind"]),
    ]
)
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val kind: String,
    val refId: String,
    val createdAt: Long,
    val lastAttemptAt: Long? = null,
    val attemptCount: Int = 0,
    val lastError: String? = null,
) {
    companion object {
        const val KIND_FAVORITE_SHOW = "favorite_show"
        /** refId is the local favorite_songs row id (Long.toString()). The
         *  flusher reads showId/trackTitle off the row at push time. */
        const val KIND_FAVORITE_SONG = "favorite_song"
        /** refId is the showId. Announces a play; the server stamps the time. */
        const val KIND_RECENT = "recent"
    }
}
