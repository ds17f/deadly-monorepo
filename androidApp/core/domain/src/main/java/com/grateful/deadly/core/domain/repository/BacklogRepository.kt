package com.grateful.deadly.core.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * The user's local-first backlog ("Up Next") — an ordered list of show ids.
 *
 * Per ADR-0010 (Amendment 2026-06-14) this is the user's default mutable list
 * in the unified list+pointer model: local-authoritative, mutated by explicit
 * user actions and by the advance coordinator popping the head. Returns show
 * ids; callers resolve `Show` via [ShowRepository]. Sync (slice 4) ships the
 * add/pop/move event, not the list.
 */
interface BacklogRepository {

    /** Live backlog in play order (head first). */
    fun observeShowIds(): Flow<List<String>>

    /** Current backlog in play order (head first). */
    suspend fun getShowIds(): List<String>

    /** Next show id to play, without consuming it (used at advance *announce*). */
    suspend fun peekHeadId(): String?

    /** Consume and return the head (used at advance *commit*); null when empty. */
    suspend fun popHead(): String?

    /** Append a show to the bottom; no-op shape if already present (moves to bottom). */
    suspend fun addToBottom(showId: String)

    suspend fun contains(showId: String): Boolean

    /** Remove a single show from the backlog. */
    suspend fun remove(showId: String)

    /** Rewrite the order to exactly [orderedShowIds] (drag-to-reorder). */
    suspend fun reorder(orderedShowIds: List<String>)

    /** Empty the backlog. */
    suspend fun clear()

    suspend fun count(): Int
}
