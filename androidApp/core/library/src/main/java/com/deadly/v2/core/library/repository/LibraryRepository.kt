package com.deadly.v2.core.library.repository

import android.util.Log
import com.deadly.v2.core.database.dao.LibraryDao
import com.deadly.v2.core.database.entities.LibraryShowEntity
import com.deadly.v2.core.domain.repository.ShowRepository
import com.deadly.v2.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.deadly.v2.core.model.V2Database

/**
 * V2 LibraryRepository - Pure V2 implementation integrating database and domain layers
 * 
 * Combines LibraryDao (library-specific data) with ShowRepository (show data)
 * to provide rich LibraryShow domain models for the service layer.
 * 
 * Follows V2 architecture patterns with reactive Flow-based operations.
 */
@Singleton
class LibraryRepository @Inject constructor(
    @V2Database private val libraryDao: LibraryDao,
    private val showRepository: ShowRepository
) {
    
    companion object {
        private const val TAG = "V2LibraryRepository"
    }
    
    /**
     * Get all library shows as reactive flow with complete show data
     */
    fun getLibraryShowsFlow(): Flow<List<LibraryShow>> {
        Log.d(TAG, "getLibraryShowsFlow() - combining library data with show data")
        
        return libraryDao.getAllLibraryShowsFlow()
            .combine(showRepository.getAllShowsFlow()) { libraryEntities, allShows ->
                Log.d(TAG, "Combining ${libraryEntities.size} library entries with ${allShows.size} shows")
                
                // Create map of showId -> Show for efficient lookup
                val showsMap = allShows.associateBy { it.id }
                
                // Convert to LibraryShows, filtering out shows that no longer exist
                libraryEntities.mapNotNull { libraryEntity ->
                    showsMap[libraryEntity.showId]?.let { show ->
                        LibraryShow(
                            show = show,
                            addedToLibraryAt = libraryEntity.addedToLibraryAt,
                            isPinned = libraryEntity.isPinned,
                            downloadStatus = LibraryDownloadStatus.NOT_DOWNLOADED // TODO: Add download integration
                        )
                    }
                }
            }
    }
    
    /**
     * Add show to library
     */
    suspend fun addShowToLibrary(showId: String): Result<Unit> {
        Log.d(TAG, "addShowToLibrary('$showId')")
        
        return try {
            // Verify show exists in ShowRepository
            val show = showRepository.getShowById(showId)
            if (show == null) {
                Log.w(TAG, "Show '$showId' not found in ShowRepository")
                return Result.failure(Exception("Show not found"))
            }
            
            // Create library entity
            val libraryEntity = LibraryShowEntity(
                showId = showId,
                addedToLibraryAt = System.currentTimeMillis(),
                isPinned = false
            )
            
            libraryDao.addToLibrary(libraryEntity)
            Log.d(TAG, "Successfully added show '$showId' to library")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error adding show '$showId' to library", e)
            Result.failure(e)
        }
    }
    
    /**
     * Remove show from library
     */
    suspend fun removeShowFromLibrary(showId: String): Result<Unit> {
        Log.d(TAG, "removeShowFromLibrary('$showId')")
        
        return try {
            libraryDao.removeFromLibraryById(showId)
            Log.d(TAG, "Successfully removed show '$showId' from library")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error removing show '$showId' from library", e)
            Result.failure(e)
        }
    }
    
    /**
     * Check if show is in library (reactive)
     */
    fun isShowInLibraryFlow(showId: String): Flow<Boolean> {
        return libraryDao.isShowInLibraryFlow(showId)
    }
    
    /**
     * Check if show is in library (one-time)
     */
    suspend fun isShowInLibrary(showId: String): Boolean {
        return libraryDao.isShowInLibrary(showId)
    }
    
    /**
     * Pin show
     */
    suspend fun pinShow(showId: String): Result<Unit> {
        Log.d(TAG, "pinShow('$showId')")
        
        return try {
            libraryDao.updatePinStatus(showId, true)
            Log.d(TAG, "Successfully pinned show '$showId'")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error pinning show '$showId'", e)
            Result.failure(e)
        }
    }
    
    /**
     * Unpin show
     */
    suspend fun unpinShow(showId: String): Result<Unit> {
        Log.d(TAG, "unpinShow('$showId')")
        
        return try {
            libraryDao.updatePinStatus(showId, false)
            Log.d(TAG, "Successfully unpinned show '$showId'")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error unpinning show '$showId'", e)
            Result.failure(e)
        }
    }
    
    /**
     * Check if show is pinned (reactive)
     */
    fun isShowPinnedFlow(showId: String): Flow<Boolean> {
        return libraryDao.isShowPinnedFlow(showId)
    }
    
    /**
     * Get library statistics
     */
    fun getLibraryStatsFlow(): Flow<LibraryStats> {
        return libraryDao.getLibraryShowCountFlow()
            .combine(libraryDao.getPinnedShowCountFlow()) { totalShows, pinnedShows ->
                LibraryStats(
                    totalShows = totalShows,
                    totalDownloaded = 0, // TODO: Add download integration
                    totalStorageUsed = 0L, // TODO: Add storage calculation
                    totalPinned = pinnedShows
                )
            }
    }
    
    /**
     * Clear entire library
     */
    suspend fun clearLibrary(): Result<Unit> {
        Log.d(TAG, "clearLibrary()")
        
        return try {
            libraryDao.clearLibrary()
            Log.d(TAG, "Successfully cleared library")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing library", e)
            Result.failure(e)
        }
    }
}