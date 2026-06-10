package com.grateful.deadly.core.api.playqueue

import com.grateful.deadly.core.model.QueuedShow
import kotlinx.coroutines.flow.StateFlow

/**
 * Persistent show queue (ADR-0010).
 *
 * The queue is the single source of "what plays next" (Apple-Music "Playing
 * Next" model). It holds **upcoming shows only** — the currently-playing show
 * is a separate pointer owned by the playback layer. The unit is always a whole
 * show; track/playlist queuing is deferred (ROADMAP §8).
 *
 * Local-only: never synced, never a Favorite, excluded from Fan-Favorites
 * stats. Persistent across app kills; shrinks as shows are consumed.
 */
interface PlayQueueService {

    /** Upcoming shows, head first (next to play). Reactive. */
    val queue: StateFlow<List<QueuedShow>>

    /** Append a show to the bottom of the queue ("Add to Queue"). */
    suspend fun enqueue(showId: String, recordingId: String? = null)

    /**
     * Insert a show at the head of the queue. Used by the interrupt re-queue
     * ("Queue A" snackbar): the interrupted show jumps to next-up carrying a
     * resume snapshot so it resumes mid-set when reached.
     */
    suspend fun enqueueNext(
        showId: String,
        recordingId: String? = null,
        resumeTrackIndex: Int? = null,
        resumePositionMs: Long? = null
    )

    /** Remove a single entry by its stable id. */
    suspend fun remove(id: Long)

    /** Reorder: move the entry at [fromIndex] to [toIndex] (0-based). */
    suspend fun move(fromIndex: Int, toIndex: Int)

    /** Empty the queue. */
    suspend fun clear()

    /** Peek the next show without removing it. */
    suspend fun peekNext(): QueuedShow?

    /** Remove and return the head — used by end-of-show auto-advance. */
    suspend fun popNext(): QueuedShow?

    /** True if [showId] is already somewhere in the queue. */
    suspend fun contains(showId: String): Boolean
}
