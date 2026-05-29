package com.grateful.deadly.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grateful.deadly.core.database.entities.SyncOutboxEntity

@Dao
interface SyncOutboxDao {
    /** Idempotent enqueue. If a pending row for (kind, refId) exists, this no-ops. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(entry: SyncOutboxEntity): Long

    @Query("SELECT * FROM sync_outbox WHERE kind = :kind ORDER BY createdAt ASC")
    suspend fun fetchPending(kind: String): List<SyncOutboxEntity>

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("""
        UPDATE sync_outbox
           SET lastAttemptAt = :now, attemptCount = attemptCount + 1, lastError = :error
         WHERE id = :id
    """)
    suspend fun recordFailure(id: Long, now: Long, error: String)

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE kind = :kind")
    suspend fun pendingCount(kind: String): Int
}
