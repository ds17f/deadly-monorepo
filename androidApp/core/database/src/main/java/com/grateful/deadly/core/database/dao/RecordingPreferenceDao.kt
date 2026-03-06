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

    @Query("SELECT * FROM recording_preferences WHERE showId = :showId")
    suspend fun get(showId: String): RecordingPreferenceEntity?

    @Query("SELECT recordingId FROM recording_preferences WHERE showId = :showId")
    suspend fun getRecordingId(showId: String): String?

    @Query("SELECT recordingId FROM recording_preferences WHERE showId = :showId")
    fun getRecordingIdFlow(showId: String): Flow<String?>

    @Query("SELECT * FROM recording_preferences")
    suspend fun getAll(): List<RecordingPreferenceEntity>

    @Query("DELETE FROM recording_preferences WHERE showId = :showId")
    suspend fun delete(showId: String)

    @Query("DELETE FROM recording_preferences")
    suspend fun deleteAll()
}
