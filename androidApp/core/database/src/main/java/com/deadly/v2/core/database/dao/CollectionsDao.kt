package com.deadly.v2.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.deadly.v2.core.database.entities.DeadCollectionEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Dead Collections
 * 
 * Provides access to curated Grateful Dead collections stored in the database.
 * Collections are imported from collections.json during database initialization.
 */
@Dao
interface CollectionsDao {
    
    /**
     * Get all collections
     */
    @Query("SELECT * FROM dead_collections ORDER BY name ASC")
    suspend fun getAllCollections(): List<DeadCollectionEntity>
    
    /**
     * Get collections as Flow for reactive UI
     */
    @Query("SELECT * FROM dead_collections ORDER BY name ASC")
    fun getAllCollectionsFlow(): Flow<List<DeadCollectionEntity>>
    
    /**
     * Get collection by ID
     */
    @Query("SELECT * FROM dead_collections WHERE id = :id")
    suspend fun getCollectionById(id: String): DeadCollectionEntity?
    
    /**
     * Get featured collections (top 6 by show count)
     */
    @Query("SELECT * FROM dead_collections ORDER BY totalShows DESC LIMIT 6")
    suspend fun getFeaturedCollections(): List<DeadCollectionEntity>
    
    /**
     * Get featured collections as Flow
     */
    @Query("SELECT * FROM dead_collections ORDER BY totalShows DESC LIMIT 6")
    fun getFeaturedCollectionsFlow(): Flow<List<DeadCollectionEntity>>
    
    /**
     * Search collections by name or description
     */
    @Query("""
        SELECT * FROM dead_collections 
        WHERE name LIKE '%' || :query || '%' 
        OR description LIKE '%' || :query || '%'
        ORDER BY name ASC
    """)
    suspend fun searchCollections(query: String): List<DeadCollectionEntity>
    
    /**
     * Get collections by tag
     */
    @Query("SELECT * FROM dead_collections WHERE primaryTag = :tag ORDER BY name ASC")
    suspend fun getCollectionsByTag(tag: String): List<DeadCollectionEntity>
    
    /**
     * Insert collection
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: DeadCollectionEntity)
    
    /**
     * Insert multiple collections
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollections(collections: List<DeadCollectionEntity>)
    
    /**
     * Update collection
     */
    @Update
    suspend fun updateCollection(collection: DeadCollectionEntity)
    
    /**
     * Delete collection by ID
     */
    @Query("DELETE FROM dead_collections WHERE id = :id")
    suspend fun deleteCollectionById(id: String)
    
    /**
     * Delete all collections
     */
    @Query("DELETE FROM dead_collections")
    suspend fun deleteAllCollections()
    
    /**
     * Count total collections
     */
    @Query("SELECT COUNT(*) FROM dead_collections")
    suspend fun getCollectionCount(): Int
    
    /**
     * Get collections with show counts greater than minimum
     */
    @Query("SELECT * FROM dead_collections WHERE totalShows >= :minShows ORDER BY totalShows DESC")
    suspend fun getCollectionsWithMinimumShows(minShows: Int): List<DeadCollectionEntity>
    
    /**
     * Get collections containing a specific show (efficient JSON query)
     */
    @Query("""
        SELECT * FROM dead_collections 
        WHERE showIdsJson LIKE '%' || :showId || '%'
        ORDER BY name ASC
    """)
    suspend fun getCollectionsContainingShow(showId: String): List<DeadCollectionEntity>
}