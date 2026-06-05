package com.grateful.deadly.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grateful.deadly.core.database.entities.FavoriteSongEntity
import kotlinx.coroutines.flow.Flow

/**
 * Favorite-songs DAO. Identity is (showId, trackTitle) — matches the server.
 * recordingId rides along on the row but does NOT participate in matching.
 * UI reads filter `deletedAt IS NULL`; sync paths use *IncludingTombstones.
 * Soft-delete semantics mirror FavoritesDao. See PLANS/mobile-server-sync.md.
 */
@Dao
interface FavoriteSongDao {

    // ── Mutation ───────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: FavoriteSongEntity): Long

    /**
     * Resurrect a tombstoned row by natural key. Updates recordingId to
     * whatever the caller passed (so the favorites screen has the most
     * recently chosen recording for navigation). Returns rows updated.
     */
    @Query("""
        UPDATE favorite_songs
           SET trackNumber = :trackNumber, recordingId = :recordingId,
               updatedAt = :now, deletedAt = NULL
         WHERE showId = :showId AND trackTitle = :trackTitle
    """)
    suspend fun resurrect(
        showId: String, trackTitle: String,
        trackNumber: Int?, recordingId: String?, now: Long
    ): Int

    /** Soft-delete: row stays as a tombstone. */
    @Query("""
        UPDATE favorite_songs
           SET deletedAt = :now, updatedAt = :now
         WHERE showId = :showId AND trackTitle = :trackTitle
    """)
    suspend fun softDelete(showId: String, trackTitle: String, now: Long)

    /** Hard delete used by show-review wipe; not part of sync flow. */
    @Query("DELETE FROM favorite_songs WHERE showId = :showId")
    suspend fun deleteForShow(showId: String)

    // ── Sync apply ─────────────────────────────────────────────────────

    /** Upsert from a sync pull — writes deletedAt verbatim so server tombstones propagate. */
    @Query("""
        UPDATE favorite_songs
           SET trackNumber = :trackNumber, recordingId = :recordingId,
               createdAt = :createdAt, updatedAt = :updatedAt, deletedAt = :deletedAt
         WHERE id = :id
    """)
    suspend fun applyFromSyncUpdate(
        id: Long,
        trackNumber: Int?, recordingId: String?,
        createdAt: Long, updatedAt: Long, deletedAt: Long?
    ): Int

    // ── Query ──────────────────────────────────────────────────────────

    @Query("""
        SELECT * FROM favorite_songs
         WHERE showId = :showId AND trackTitle = :trackTitle
    """)
    suspend fun findByKeyIncludingTombstones(
        showId: String, trackTitle: String
    ): FavoriteSongEntity?

    @Query("SELECT * FROM favorite_songs WHERE id = :id")
    suspend fun findByLocalIdIncludingTombstones(id: Long): FavoriteSongEntity?

    @Query("""
        SELECT EXISTS(SELECT 1 FROM favorite_songs
                       WHERE showId = :showId AND trackTitle = :trackTitle
                         AND deletedAt IS NULL)
    """)
    suspend fun isFavorite(showId: String, trackTitle: String): Boolean

    @Query("""
        SELECT EXISTS(SELECT 1 FROM favorite_songs
                       WHERE showId = :showId AND trackTitle = :trackTitle
                         AND deletedAt IS NULL)
    """)
    fun isFavoriteFlow(showId: String, trackTitle: String): Flow<Boolean>

    @Query("SELECT trackTitle FROM favorite_songs WHERE showId = :showId AND deletedAt IS NULL")
    fun getFavoriteTitlesForShowFlow(showId: String): Flow<List<String>>

    @Query("SELECT * FROM favorite_songs WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    suspend fun getAllFavorites(): List<FavoriteSongEntity>

    @Query("SELECT * FROM favorite_songs WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun getAllFavoritesFlow(): Flow<List<FavoriteSongEntity>>
}
