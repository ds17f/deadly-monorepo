package com.grateful.deadly.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grateful.deadly.core.database.entities.RecordingPreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingPreferenceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preference: RecordingPreferenceEntity)

    /** Includes tombstones — for the sync push/apply paths. */
    @Query("SELECT * FROM recording_preferences WHERE showId = :showId")
    suspend fun get(showId: String): RecordingPreferenceEntity?

    @Query("SELECT recordingId FROM recording_preferences WHERE showId = :showId AND deletedAt IS NULL")
    suspend fun getRecordingId(showId: String): String?

    @Query("SELECT recordingId FROM recording_preferences WHERE showId = :showId AND deletedAt IS NULL")
    fun getRecordingIdFlow(showId: String): Flow<String?>

    /** Includes tombstones — for the one-time sync backfill. */
    @Query("SELECT * FROM recording_preferences")
    suspend fun getAll(): List<RecordingPreferenceEntity>

    /** Soft-delete so a clear can sync as a tombstone (last-write-wins). */
    @Query("UPDATE recording_preferences SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE showId = :showId")
    suspend fun softDelete(showId: String, deletedAt: Long, updatedAt: Long)

    @Query("DELETE FROM recording_preferences WHERE showId = :showId")
    suspend fun delete(showId: String)

    @Query("DELETE FROM recording_preferences")
    suspend fun deleteAll()
}
