package com.grateful.deadly.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.grateful.deadly.core.database.entities.PlayQueueEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access for the persistent show queue (ADR-0010).
 *
 * Ordering is by [PlayQueueEntity.position] ascending; the head (next show) is
 * the lowest position. Append goes to [maxPosition] + 1; head-insert shifts all
 * rows down and inserts at 0.
 */
@Dao
interface PlayQueueDao {

    @Query("SELECT * FROM play_queue ORDER BY position ASC")
    fun observeQueue(): Flow<List<PlayQueueEntity>>

    @Query("SELECT * FROM play_queue ORDER BY position ASC")
    suspend fun getQueue(): List<PlayQueueEntity>

    @Query("SELECT * FROM play_queue ORDER BY position ASC LIMIT 1")
    suspend fun peekHead(): PlayQueueEntity?

    @Query("SELECT COALESCE(MAX(position), -1) FROM play_queue")
    suspend fun maxPosition(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM play_queue WHERE showId = :showId)")
    suspend fun containsShow(showId: String): Boolean

    @Insert
    suspend fun insert(entity: PlayQueueEntity): Long

    @Query("UPDATE play_queue SET position = position + 1")
    suspend fun shiftAllDown()

    @Query("DELETE FROM play_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Remove all entries for a show. Used when a show becomes current — playing
     *  any show pops it from the upcoming queue (ADR-0010 §1). */
    @Query("DELETE FROM play_queue WHERE showId = :showId")
    suspend fun deleteByShowId(showId: String)

    @Query("DELETE FROM play_queue")
    suspend fun clear()

    @Query("UPDATE play_queue SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    /** Rewrite positions to match [orderedIds]; index in the list becomes position. */
    @Transaction
    suspend fun reorder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> updatePosition(id, index) }
    }

    /** Append [entity] at the tail in one round-trip. */
    @Transaction
    suspend fun append(entity: PlayQueueEntity): Long {
        val next = maxPosition() + 1
        return insert(entity.copy(position = next))
    }

    /** Insert [entity] at the head (position 0), shifting existing rows down. */
    @Transaction
    suspend fun insertHead(entity: PlayQueueEntity): Long {
        shiftAllDown()
        return insert(entity.copy(position = 0))
    }
}
