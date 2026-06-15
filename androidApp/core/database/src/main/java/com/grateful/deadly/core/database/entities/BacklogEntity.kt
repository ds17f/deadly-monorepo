package com.grateful.deadly.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Backlog ("Up Next") database entity — the user's local-first play-next list.
 *
 * One row per show (showId PK): a show appears in the backlog at most once.
 * Ordering is by [position] ascending; add-to-bottom assigns max(position)+1.
 * "Pop the head" advances playback to the lowest-position row.
 *
 * Per ADR-0010 (Amendment 2026-06-14): this is the user's default mutable list
 * in the unified list+pointer model. It is local-authoritative; sync ships the
 * add/pop/move *event* via the generic sync_outbox (Favorites pattern), not a
 * whole-list snapshot — the [deletedAt] tombstone supports that, mirroring the
 * other synced tables. See PLANS/show-queue-v2.md.
 */
@Entity(
    tableName = "backlog",
    indices = [
        Index(value = ["position"])
    ]
)
@Serializable
data class BacklogEntity(
    @PrimaryKey
    val showId: String,

    /** Sort key, ascending. Head of the backlog = lowest position. */
    val position: Long,

    /** When this show was added to the backlog (epoch millis). */
    val addedAt: Long,

    /** LWW comparator for per-action sync (slice 4); bumped on every mutation. */
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = 0,

    // Sync support (see PLANS/mobile-server-sync.md); set on remove for tombstoning.
    val deletedAt: Long? = null
)
