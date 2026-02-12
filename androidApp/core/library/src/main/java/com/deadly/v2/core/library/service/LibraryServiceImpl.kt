package com.deadly.v2.core.library.service

import android.util.Log
import com.deadly.v2.core.api.library.LibraryService
import com.deadly.v2.core.domain.repository.ShowRepository
import com.deadly.v2.core.media.repository.MediaControllerRepository
import com.deadly.v2.core.model.*
import com.deadly.v2.core.library.repository.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * LibraryServiceImpl - PURE V2 implementation with zero v1 dependencies
 * 
 * Phase 3 Implementation:
 * ✅ Pure V2 database entities (LibraryShowEntity)
 * ✅ Pure V2 database access (LibraryDao) 
 * ✅ Pure V2 repository layer (LibraryRepository)
 * ✅ Direct delegation architecture following V2 patterns
 * 
 * Eliminates all v1 dependencies and implements clean V2 service architecture.
 */
@Singleton
class LibraryServiceImpl @Inject constructor(
    private val showRepository: ShowRepository,
    private val mediaControllerRepository: MediaControllerRepository,
    private val libraryRepository: LibraryRepository,
    private val shareService: ShareService,
    @Named("LibraryApplicationScope") private val coroutineScope: CoroutineScope
) : LibraryService {
    
    companion object {
        private const val TAG = "LibraryServiceImpl"
    }
    
    // Real reactive StateFlows backed by V2 database
    private val _currentShows: StateFlow<List<LibraryShow>> = libraryRepository
        .getLibraryShowsFlow()
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        
    private val _libraryStats: StateFlow<LibraryStats> = libraryRepository
        .getLibraryStatsFlow()
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LibraryStats(0, 0, 0, 0)
        )
    
    init {
        Log.d(TAG, "LibraryServiceImpl initialized with PURE V2 architecture")
        Log.d(TAG, "Dependencies: ShowRepository=${showRepository::class.simpleName}, LibraryRepository=${libraryRepository::class.simpleName}")
    }
    
    // ✅ PURE V2 IMPLEMENTATION: Load library shows (reactive via database StateFlow)
    override suspend fun loadLibraryShows(): Result<Unit> {
        Log.d(TAG, "loadLibraryShows() - PURE V2 implementation with reactive database flows")
        // StateFlow automatically loads data reactively from V2 database
        return Result.success(Unit)
    }
    
    // ✅ PURE V2 IMPLEMENTATION: Get current shows from V2 database StateFlow
    override fun getCurrentShows(): StateFlow<List<LibraryShow>> {
        Log.d(TAG, "getCurrentShows() - PURE V2 implementation returning V2 database StateFlow")
        return _currentShows
    }
    
    // ✅ PURE V2 IMPLEMENTATION: Get library statistics from V2 database StateFlow
    override fun getLibraryStats(): StateFlow<LibraryStats> {
        Log.d(TAG, "getLibraryStats() - PURE V2 implementation returning V2 database StateFlow")
        return _libraryStats
    }
    
    // ✅ PURE V2 IMPLEMENTATION: Add show to library using V2 database
    override suspend fun addToLibrary(showId: String): Result<Unit> {
        Log.d(TAG, "addToLibrary('$showId') - PURE V2 implementation using V2 LibraryRepository")
        return libraryRepository.addShowToLibrary(showId)
    }
    
    // ✅ PURE V2 IMPLEMENTATION: Remove show from library using V2 database
    override suspend fun removeFromLibrary(showId: String): Result<Unit> {
        Log.d(TAG, "removeFromLibrary('$showId') - PURE V2 implementation using V2 LibraryRepository")
        return libraryRepository.removeShowFromLibrary(showId)
    }
    
    // ✅ PURE V2 IMPLEMENTATION: Clear entire library using V2 database
    override suspend fun clearLibrary(): Result<Unit> {
        Log.d(TAG, "clearLibrary() - PURE V2 implementation using V2 LibraryRepository")
        return libraryRepository.clearLibrary()
    }
    
    // ✅ PURE V2 IMPLEMENTATION: Check if show is in library using V2 database
    override fun isShowInLibrary(showId: String): StateFlow<Boolean> {
        Log.d(TAG, "isShowInLibrary('$showId') - PURE V2 implementation returning V2 database StateFlow")
        return libraryRepository.isShowInLibraryFlow(showId)
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false
            )
    }
    
    // ✅ PURE V2 IMPLEMENTATION: Pin show using V2 database
    override suspend fun pinShow(showId: String): Result<Unit> {
        Log.d(TAG, "pinShow('$showId') - PURE V2 implementation using V2 LibraryRepository")
        return libraryRepository.pinShow(showId)
    }
    
    // ✅ PURE V2 IMPLEMENTATION: Unpin show using V2 database
    override suspend fun unpinShow(showId: String): Result<Unit> {
        Log.d(TAG, "unpinShow('$showId') - PURE V2 implementation using V2 LibraryRepository")
        return libraryRepository.unpinShow(showId)
    }
    
    // ✅ PURE V2 IMPLEMENTATION: Check if show is pinned using V2 database
    override fun isShowPinned(showId: String): StateFlow<Boolean> {
        Log.d(TAG, "isShowPinned('$showId') - PURE V2 implementation returning V2 database StateFlow")
        return libraryRepository.isShowPinnedFlow(showId)
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false
            )
    }
    
    // TODO: Download integration - will implement when V2 download service is available
    override suspend fun downloadShow(showId: String): Result<Unit> {
        Log.d(TAG, "downloadShow('$showId') - TODO: V2 download service integration")
        return Result.success(Unit)
    }
    
    override suspend fun cancelShowDownloads(showId: String): Result<Unit> {
        Log.d(TAG, "cancelShowDownloads('$showId') - TODO: V2 download service integration")
        return Result.success(Unit)
    }
    
    override fun getDownloadStatus(showId: String): StateFlow<LibraryDownloadStatus> {
        Log.d(TAG, "getDownloadStatus('$showId') - TODO: V2 download service integration")
        return kotlinx.coroutines.flow.MutableStateFlow(LibraryDownloadStatus.NOT_DOWNLOADED)
    }
    
    // ✅ PURE V2 IMPLEMENTATION: Share show using V2 ShareService
    override suspend fun shareShow(showId: String): Result<Unit> {
        Log.d(TAG, "shareShow('$showId') - PURE V2 implementation using V2 ShareService")
        
        return try {
            // Get show data from repository
            val show = showRepository.getShowById(showId)
            if (show == null) {
                Log.w(TAG, "Show not found for sharing: $showId")
                return Result.failure(Exception("Show not found: $showId"))
            }
            
            // Get best recording for the show
            val recording = showRepository.getBestRecordingForShow(showId)
            if (recording == null) {
                Log.w(TAG, "No recording found for sharing show: $showId")
                return Result.failure(Exception("No recording found for show: $showId"))
            }
            
            Log.d(TAG, "Sharing show: ${show.displayTitle} with recording: ${recording.identifier}")
            shareService.shareShow(show, recording)
            
            Log.d(TAG, "Successfully shared show: ${show.displayTitle}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing show '$showId'", e)
            Result.failure(e)
        }
    }
}