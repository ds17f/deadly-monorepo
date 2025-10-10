package com.deadly.v2.core.api.library

import com.deadly.v2.core.model.*
import kotlinx.coroutines.flow.StateFlow

/**
 * V2 Library Service - Clean service interface for library functionality
 * 
 * Following V2 architecture patterns with service-oriented design,
 * this interface defines all library operations with reactive StateFlow
 * for UI state management and proper Result handling for operations.
 */
interface LibraryService {
    
    /**
     * Load library shows into service state
     */
    suspend fun loadLibraryShows(): Result<Unit>
    
    /**
     * Get current library shows as reactive state
     */
    fun getCurrentShows(): StateFlow<List<LibraryShow>>
    
    /**
     * Get current library statistics
     */
    fun getLibraryStats(): StateFlow<LibraryStats>
    
    /**
     * Add a show to the user's library
     */
    suspend fun addToLibrary(showId: String): Result<Unit>
    
    /**
     * Remove a show from the user's library
     */
    suspend fun removeFromLibrary(showId: String): Result<Unit>
    
    /**
     * Clear all shows from the user's library
     */
    suspend fun clearLibrary(): Result<Unit>
    
    /**
     * Check if a show is in the user's library (reactive)
     */
    fun isShowInLibrary(showId: String): StateFlow<Boolean>
    
    /**
     * Pin a show for priority display
     */
    suspend fun pinShow(showId: String): Result<Unit>
    
    /**
     * Unpin a previously pinned show
     */
    suspend fun unpinShow(showId: String): Result<Unit>
    
    /**
     * Check if a show is pinned (reactive)
     */
    fun isShowPinned(showId: String): StateFlow<Boolean>
    
    /**
     * Download a show
     */
    suspend fun downloadShow(showId: String): Result<Unit>
    
    /**
     * Cancel show downloads
     */
    suspend fun cancelShowDownloads(showId: String): Result<Unit>
    
    /**
     * Get download status for a show (reactive)
     */
    fun getDownloadStatus(showId: String): StateFlow<LibraryDownloadStatus>
    
    /**
     * Share a show
     */
    suspend fun shareShow(showId: String): Result<Unit>
    
    /**
     * Populate library with test data for development
     * Only implemented in stub - no-op in real implementations
     */
    suspend fun populateTestData(): Result<Unit> {
        return Result.success(Unit)
    }
}