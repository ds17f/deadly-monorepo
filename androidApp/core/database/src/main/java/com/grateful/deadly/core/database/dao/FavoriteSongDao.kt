package com.grateful.deadly.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grateful.deadly.core.database.entities.FavoriteSongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteSongDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: FavoriteSongEntity)

    @Query("DELETE FROM favorite_songs WHERE showId = :showId AND trackTitle = :trackTitle AND (recordingId = :recordingId OR (recordingId IS NULL AND :recordingId IS NULL))")
    suspend fun delete(showId: String, trackTitle: String, recordingId: String?)

    @Query("DELETE FROM favorite_songs WHERE showId = :showId")
    suspend fun deleteForShow(showId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_songs WHERE showId = :showId AND trackTitle = :trackTitle AND (recordingId = :recordingId OR (recordingId IS NULL AND :recordingId IS NULL)))")
    suspend fun isFavorite(showId: String, trackTitle: String, recordingId: String?): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_songs WHERE showId = :showId AND trackTitle = :trackTitle AND (recordingId = :recordingId OR (recordingId IS NULL AND :recordingId IS NULL)))")
    fun isFavoriteFlow(showId: String, trackTitle: String, recordingId: String?): Flow<Boolean>

    @Query("SELECT trackTitle FROM favorite_songs WHERE showId = :showId")
    fun getFavoriteTitlesForShowFlow(showId: String): Flow<List<String>>

    @Query("SELECT * FROM favorite_songs ORDER BY createdAt DESC")
    suspend fun getAllFavorites(): List<FavoriteSongEntity>

    @Query("SELECT * FROM favorite_songs ORDER BY createdAt DESC")
    fun getAllFavoritesFlow(): Flow<List<FavoriteSongEntity>>
}
