package com.grateful.deadly.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grateful.deadly.core.database.entities.ShowPlayerTagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShowPlayerTagDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tag: ShowPlayerTagEntity)

    @Query("SELECT * FROM show_player_tags WHERE showId = :showId ORDER BY playerName ASC")
    suspend fun getTagsForShow(showId: String): List<ShowPlayerTagEntity>

    @Query("SELECT * FROM show_player_tags WHERE showId = :showId ORDER BY playerName ASC")
    fun getTagsForShowFlow(showId: String): Flow<List<ShowPlayerTagEntity>>

    @Query("SELECT * FROM show_player_tags WHERE playerName = :playerName AND isStandout = 1 ORDER BY createdAt DESC")
    suspend fun getStandoutShowsForPlayer(playerName: String): List<ShowPlayerTagEntity>

    @Query("DELETE FROM show_player_tags WHERE showId = :showId AND playerName = :playerName")
    suspend fun removeTag(showId: String, playerName: String)

    @Query("DELETE FROM show_player_tags WHERE showId = :showId")
    suspend fun removeTagsForShow(showId: String)

    @Query("SELECT COUNT(*) FROM show_player_tags WHERE showId = :showId")
    suspend fun getTagCountForShow(showId: String): Int

    @Query("SELECT DISTINCT playerName FROM show_player_tags WHERE isStandout = 1 ORDER BY playerName ASC")
    suspend fun getAllStandoutPlayerNames(): List<String>

    @Query("SELECT * FROM show_player_tags")
    suspend fun getAll(): List<ShowPlayerTagEntity>
}
