package com.grateful.deadly.core.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent show-queue row (ADR-0010).
 *
 * One row per upcoming show. Ordering is by [position] (0-based, ascending);
 * the head of the queue is the lowest position. Local-only — never synced and
 * never a Favorite.
 */
@Entity(
    tableName = "play_queue",
    indices = [Index(value = ["position"])]
)
data class PlayQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val showId: String,

    /** Null = resolve to the recommended recording at play time. */
    val recordingId: String? = null,

    /** 0-based ordering; head of queue = MIN(position). */
    val position: Int,

    /** Non-null only for a re-queued interrupted show (resume target track). */
    val resumeTrackIndex: Int? = null,

    /** Paired with [resumeTrackIndex]. */
    val resumePositionMs: Long? = null,

    val addedAt: Long = System.currentTimeMillis()
)
