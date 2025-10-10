package com.deadly.v2.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.deadly.v2.core.database.entities.ShowSearchEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for ShowSearchEntity FTS operations.
 * 
 * Provides full-text search capabilities using Room's FTS4 integration
 * with unicode61 tokenizer configured to preserve dashes in tokens.
 * Handles both search operations and FTS table management.
 */
@Dao
interface ShowSearchDao {
    
    /**
     * Insert or update search record for a show
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(showSearch: ShowSearchEntity)
    
    /**
     * Insert multiple search records efficiently
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(showSearches: List<ShowSearchEntity>)
    
    /**
     * Full-text search query with MATCH operator
     * Returns show IDs with FTS4 relevance ordering
     */
    @Query("SELECT showId FROM show_search WHERE show_search MATCH :query")
    suspend fun searchShows(query: String): List<String>
    
    /**
     * Reactive search results as Flow for live updates
     * Returns show IDs with FTS4 relevance ordering
     */
    @Query("SELECT showId FROM show_search WHERE show_search MATCH :query")
    fun searchShowsFlow(query: String): Flow<List<String>>
    
    /**
     * Get total count of indexed shows
     */
    @Query("SELECT COUNT(*) FROM show_search")
    suspend fun getIndexedShowCount(): Int
    
    /**
     * Clear all FTS data (for rebuilding index)
     */
    @Query("DELETE FROM show_search")
    suspend fun clearAllSearchData()
    
    /**
     * Check if a show is already indexed
     */
    @Query("SELECT COUNT(*) FROM show_search WHERE showId = :showId")
    suspend fun isShowIndexed(showId: String): Int
    
    /**
     * Remove specific show from search index
     */
    @Query("DELETE FROM show_search WHERE showId = :showId")
    suspend fun removeShowFromIndex(showId: String)
}