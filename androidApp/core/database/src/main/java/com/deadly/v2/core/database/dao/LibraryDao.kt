package com.deadly.v2.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.deadly.v2.core.database.entities.LibraryShowEntity
import kotlinx.coroutines.flow.Flow

/**
 * V2 Library DAO - Pure V2 database access for library operations
 * 
 * Provides reactive Flow-based queries and full CRUD operations
 * for user's library management with pin support and sorting.
 */
@Dao
interface LibraryDao {
    
    // Core CRUD operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToLibrary(libraryShow: LibraryShowEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addMultipleToLibrary(libraryShows: List<LibraryShowEntity>)
    
    @Delete
    suspend fun removeFromLibrary(libraryShow: LibraryShowEntity)
    
    @Query("DELETE FROM library_shows WHERE showId = :showId")
    suspend fun removeFromLibraryById(showId: String)
    
    @Update
    suspend fun updateLibraryShow(libraryShow: LibraryShowEntity)
    
    // Reactive queries for UI
    @Query("SELECT * FROM library_shows ORDER BY isPinned DESC, addedToLibraryAt DESC")
    fun getAllLibraryShowsFlow(): Flow<List<LibraryShowEntity>>
    
    @Query("SELECT * FROM library_shows WHERE isPinned = 1 ORDER BY addedToLibraryAt DESC")
    fun getPinnedLibraryShowsFlow(): Flow<List<LibraryShowEntity>>
    
    @Query("SELECT * FROM library_shows ORDER BY isPinned DESC, addedToLibraryAt DESC")
    suspend fun getAllLibraryShows(): List<LibraryShowEntity>
    
    // Individual show queries
    @Query("SELECT * FROM library_shows WHERE showId = :showId")
    suspend fun getLibraryShowById(showId: String): LibraryShowEntity?
    
    @Query("SELECT * FROM library_shows WHERE showId = :showId")
    fun getLibraryShowByIdFlow(showId: String): Flow<LibraryShowEntity?>
    
    // Status checks
    @Query("SELECT EXISTS(SELECT 1 FROM library_shows WHERE showId = :showId)")
    suspend fun isShowInLibrary(showId: String): Boolean
    
    @Query("SELECT EXISTS(SELECT 1 FROM library_shows WHERE showId = :showId)")
    fun isShowInLibraryFlow(showId: String): Flow<Boolean>
    
    @Query("SELECT EXISTS(SELECT 1 FROM library_shows WHERE showId = :showId AND isPinned = 1)")
    suspend fun isShowPinned(showId: String): Boolean
    
    @Query("SELECT EXISTS(SELECT 1 FROM library_shows WHERE showId = :showId AND isPinned = 1)")
    fun isShowPinnedFlow(showId: String): Flow<Boolean>
    
    // Pin management
    @Query("UPDATE library_shows SET isPinned = :isPinned WHERE showId = :showId")
    suspend fun updatePinStatus(showId: String, isPinned: Boolean)
    
    @Query("UPDATE library_shows SET libraryNotes = :notes WHERE showId = :showId")
    suspend fun updateLibraryNotes(showId: String, notes: String?)
    
    // Statistics
    @Query("SELECT COUNT(*) FROM library_shows")
    suspend fun getLibraryShowCount(): Int
    
    @Query("SELECT COUNT(*) FROM library_shows WHERE isPinned = 1")
    fun getPinnedShowCountFlow(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM library_shows")
    fun getLibraryShowCountFlow(): Flow<Int>
    
    // Bulk operations
    @Query("DELETE FROM library_shows")
    suspend fun clearLibrary()
    
    @Query("UPDATE library_shows SET isPinned = 0")
    suspend fun unpinAllShows()
}