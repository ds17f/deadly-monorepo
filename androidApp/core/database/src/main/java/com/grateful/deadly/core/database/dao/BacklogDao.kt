package com.grateful.deadly.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.grateful.deadly.core.database.entities.BacklogEntity
import kotlinx.coroutines.flow.Flow

/**
 * Backlog ("Up Next") data access. Ordering is by `position` ascending;
 * the head is the lowest-position live (non-tombstoned) row.
 *
 * Slice 1 keeps this purely local — no UI, no sync, no advance wiring. The
 * `deletedAt` tombstone is honored here (live queries filter it out) so the
 * later per-action sync (slice 4) can push removes without a schema change.
 */
@Dao
interface BacklogDao {

    /** Live backlog in play order (tombstones excluded). */
    @Query("SELECT * FROM backlog WHERE deletedAt IS NULL ORDER BY position ASC")
    fun observeBacklog(): Flow<List<BacklogEntity>>

    @Query("SELECT * FROM backlog WHERE deletedAt IS NULL ORDER BY position ASC")
    suspend fun getBacklog(): List<BacklogEntity>

    /** Head of the backlog (next to play), or null when empty. */
    @Query("SELECT * FROM backlog WHERE deletedAt IS NULL ORDER BY position ASC LIMIT 1")
    suspend fun peekHead(): BacklogEntity?

    @Query("SELECT * FROM backlog WHERE showId = :showId")
    suspend fun getById(showId: String): BacklogEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM backlog WHERE showId = :showId AND deletedAt IS NULL)")
    suspend fun contains(showId: String): Boolean

    /** Largest position currently in use (live or tombstoned), or null when empty. */
    @Query("SELECT MAX(position) FROM backlog")
    suspend fun maxPosition(): Long?

    @Query("SELECT COUNT(*) FROM backlog WHERE deletedAt IS NULL")
    suspend fun count(): Int

    /** Insert or revive a row (re-adding a tombstoned show clears its tombstone). */
    @Upsert
    suspend fun upsert(entity: BacklogEntity)

    /** Tombstone a show (sync-friendly remove). */
    @Query("UPDATE backlog SET deletedAt = :deletedAt WHERE showId = :showId")
    suspend fun tombstone(showId: String, deletedAt: Long)

    @Query("UPDATE backlog SET position = :position WHERE showId = :showId")
    suspend fun setPosition(showId: String, position: Long)

    /** Tombstone every live row (Clear). */
    @Query("UPDATE backlog SET deletedAt = :deletedAt WHERE deletedAt IS NULL")
    suspend fun tombstoneAll(deletedAt: Long)

    /** Hard delete — used by tests/cleanup, not the user-facing remove. */
    @Query("DELETE FROM backlog WHERE showId = :showId")
    suspend fun hardDelete(showId: String)
}
