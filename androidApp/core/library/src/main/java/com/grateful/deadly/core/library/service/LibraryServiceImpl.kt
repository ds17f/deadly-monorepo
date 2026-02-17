package com.grateful.deadly.core.library.service

import android.util.Log
import com.grateful.deadly.core.api.library.LibraryService
import com.grateful.deadly.core.domain.repository.ShowRepository
import com.grateful.deadly.core.media.download.MediaDownloadManager
import com.grateful.deadly.core.media.repository.MediaControllerRepository
import com.grateful.deadly.core.model.*
import com.grateful.deadly.core.library.repository.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * LibraryServiceImpl - Production implementation
 * 
 * Phase 3 Implementation:
 * ✅ Database entities (LibraryShowEntity)
 * ✅ Database access (LibraryDao) 
 * ✅ Repository layer (LibraryRepository)
 * ✅ Direct delegation architecture
 * 
 * 
 */
@Singleton
class LibraryServiceImpl @Inject constructor(
    private val showRepository: ShowRepository,
    private val mediaControllerRepository: MediaControllerRepository,
    private val libraryRepository: LibraryRepository,
    private val shareService: ShareService,
    private val mediaDownloadManager: MediaDownloadManager,
    @Named("LibraryApplicationScope") private val coroutineScope: CoroutineScope
) : LibraryService {
    
    companion object {
        private const val TAG = "LibraryServiceImpl"
    }
    
    // Real reactive StateFlows backed by database
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
        Log.d(TAG, "LibraryServiceImpl initialized with database architecture")
        Log.d(TAG, "Dependencies: ShowRepository=${showRepository::class.simpleName}, LibraryRepository=${libraryRepository::class.simpleName}")
    }
    
    // Load library shows (reactive via database StateFlow)
    override suspend fun loadLibraryShows(): Result<Unit> {
        Log.d(TAG, "loadLibraryShows() - loading from database")
        // StateFlow automatically loads data reactively from database
        return Result.success(Unit)
    }
    
    // Get current shows from database StateFlow
    override fun getCurrentShows(): StateFlow<List<LibraryShow>> {
        Log.d(TAG, "getCurrentShows() - returning database StateFlow")
        return _currentShows
    }
    
    // Get library statistics from database StateFlow
    override fun getLibraryStats(): StateFlow<LibraryStats> {
        Log.d(TAG, "getLibraryStats() - returning database StateFlow")
        return _libraryStats
    }
    
    // Add show to library
    override suspend fun addToLibrary(showId: String): Result<Unit> {
        Log.d(TAG, "addToLibrary('$showId') - using LibraryRepository")
        return libraryRepository.addShowToLibrary(showId)
    }
    
    // Remove show from library
    override suspend fun removeFromLibrary(showId: String): Result<Unit> {
        Log.d(TAG, "removeFromLibrary('$showId') - using LibraryRepository")
        return libraryRepository.removeShowFromLibrary(showId)
    }
    
    // Clear entire library
    override suspend fun clearLibrary(): Result<Unit> {
        Log.d(TAG, "clearLibrary() - using LibraryRepository")
        return libraryRepository.clearLibrary()
    }
    
    // Check if show is in library
    override fun isShowInLibrary(showId: String): StateFlow<Boolean> {
        Log.d(TAG, "isShowInLibrary('$showId') - returning database StateFlow")
        return libraryRepository.isShowInLibraryFlow(showId)
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false
            )
    }
    
    // Pin show
    override suspend fun pinShow(showId: String): Result<Unit> {
        Log.d(TAG, "pinShow('$showId') - using LibraryRepository")
        return libraryRepository.pinShow(showId)
    }
    
    // Unpin show
    override suspend fun unpinShow(showId: String): Result<Unit> {
        Log.d(TAG, "unpinShow('$showId') - using LibraryRepository")
        return libraryRepository.unpinShow(showId)
    }
    
    // Check if show is pinned
    override fun isShowPinned(showId: String): StateFlow<Boolean> {
        Log.d(TAG, "isShowPinned('$showId') - returning database StateFlow")
        return libraryRepository.isShowPinnedFlow(showId)
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false
            )
    }
    
    override suspend fun downloadShow(showId: String, recordingId: String?): Result<Unit> {
        Log.d(TAG, "downloadShow('$showId', recording=$recordingId)")
        // Auto-add to library if not already present
        if (!libraryRepository.isShowInLibrary(showId)) {
            libraryRepository.addShowToLibrary(showId)
        }
        return mediaDownloadManager.downloadShow(showId, recordingId)
    }

    override suspend fun cancelShowDownloads(showId: String): Result<Unit> {
        Log.d(TAG, "cancelShowDownloads('$showId')")
        return try {
            mediaDownloadManager.removeShowDownloads(showId)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling downloads for $showId", e)
            Result.failure(e)
        }
    }

    override fun getDownloadStatus(showId: String): StateFlow<LibraryDownloadStatus> {
        return mediaDownloadManager.observeShowDownloadProgress(showId)
            .map { it.status }
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = mediaDownloadManager.getShowDownloadStatus(showId)
            )
    }
    
    // Share show using ShareService
    override suspend fun shareShow(showId: String): Result<Unit> {
        Log.d(TAG, "shareShow('$showId') - using ShareService")
        
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