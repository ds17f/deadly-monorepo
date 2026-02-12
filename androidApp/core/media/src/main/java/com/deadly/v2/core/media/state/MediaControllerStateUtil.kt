package com.deadly.v2.core.media.state

import android.util.Log
import androidx.media3.common.MediaMetadata
import com.deadly.v2.core.media.repository.MediaControllerRepository
import com.deadly.v2.core.model.CurrentTrackInfo
import com.deadly.v2.core.model.QueueInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared utility for MediaController state combination and transformation
 * 
 * Eliminates duplication across V2 services by providing common MediaController
 * state observation and CurrentTrackInfo creation patterns.
 * 
 * FOUNDATION FIRST: Built on solid Phase 0 MediaController threading foundation.
 * All MediaController operations properly handle main thread requirements.
 */
@Singleton
class MediaControllerStateUtil @Inject constructor(
    private val mediaControllerRepository: MediaControllerRepository
) {
    companion object {
        private const val TAG = "MediaControllerStateUtil"
    }
    
    /**
     * Create a StateFlow of CurrentTrackInfo combining all MediaController state
     * 
     * This is the common pattern used by PlaylistServiceImpl and similar services.
     * Combines MediaController metadata, playback state, position, and duration into
     * a single reactive CurrentTrackInfo stream.
     * 
     * @param coroutineScope The coroutine scope for StateFlow lifecycle
     * @return StateFlow of CurrentTrackInfo that updates when any MediaController state changes
     */
    fun createCurrentTrackInfoStateFlow(
        coroutineScope: CoroutineScope
    ): StateFlow<CurrentTrackInfo?> {
        Log.d(TAG, "Creating CurrentTrackInfo StateFlow with comprehensive MediaController state combination")
        
        return combine(
            mediaControllerRepository.currentTrack,
            mediaControllerRepository.currentRecordingId,
            mediaControllerRepository.currentShowId,
            mediaControllerRepository.playbackState,
            mediaControllerRepository.currentPosition,
            mediaControllerRepository.duration
        ) { values ->
            val mediaMetadata = values[0] as MediaMetadata?
            val recordingId = values[1] as String?
            val showId = values[2] as String?
            val playbackState = values[3] as com.deadly.v2.core.model.PlaybackState
            val currentPosition = values[4] as Long
            val duration = values[5] as Long
            
            Log.v(TAG, "MediaController state change: metadata=${mediaMetadata != null}, recordingId=$recordingId, showId=$showId, playbackState=$playbackState")
            
            if (mediaMetadata == null || recordingId == null) {
                Log.v(TAG, "CurrentTrackInfo is null - missing metadata or recordingId")
                null
            } else {
                val trackInfo = createCurrentTrackInfo(
                    metadata = mediaMetadata,
                    recordingId = recordingId,
                    showId = showId,
                    playbackState = playbackState,
                    position = currentPosition,
                    duration = duration
                )
                
                Log.v(TAG, "Created CurrentTrackInfo: ${trackInfo.songTitle} (${trackInfo.recordingId})")
                trackInfo
            }
        }.stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    }
    
    /**
     * Create QueueInfo StateFlow for queue navigation and UI decisions
     * 
     * Combines MediaController queue state into rich QueueInfo object with computed properties.
     * Services can observe queue information and make business logic decisions.
     * 
     * @param coroutineScope The coroutine scope for StateFlow lifecycle
     * @return StateFlow of QueueInfo that updates when queue state changes
     */
    fun createQueueInfoStateFlow(
        coroutineScope: CoroutineScope
    ): StateFlow<QueueInfo> {
        Log.d(TAG, "Creating QueueInfo StateFlow with MediaController queue state")
        
        return combine(
            mediaControllerRepository.currentTrackIndex,
            mediaControllerRepository.mediaItemCount
        ) { currentIndex, totalTracks ->
            val queueInfo = QueueInfo(
                currentIndex = currentIndex,
                totalTracks = totalTracks
            )
            
            Log.d(TAG, "📊 QUEUE STATE: ${currentIndex + 1}/${totalTracks} (hasNext=${queueInfo.hasNext}, hasPrevious=${queueInfo.hasPrevious})")
            queueInfo
        }.stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = QueueInfo.EMPTY
        )
    }
    
    /**
     * Create CurrentTrackInfo from MediaMetadata with comprehensive data extraction
     * 
     * Combines the approaches from both PlaylistServiceImpl and MiniPlayerServiceImpl:
     * - Extracts rich metadata from MediaMetadata extras (MiniPlayerServiceImpl approach)
     * - Includes live playback state, position, duration (PlaylistServiceImpl approach)
     * - Handles show info parsing from album metadata (PlaylistServiceImpl approach)
     * 
     * @param metadata MediaMetadata from MediaController
     * @param recordingId Current recording ID from MediaController
     * @param showId Current show ID from MediaController  
     * @param playbackState Current comprehensive playback state from MediaController
     * @param position Current playback position from MediaController
     * @param duration Current track duration from MediaController
     * @return CurrentTrackInfo with all available data populated
     */
    fun createCurrentTrackInfo(
        metadata: MediaMetadata,
        recordingId: String?,
        showId: String?,
        playbackState: com.deadly.v2.core.model.PlaybackState,
        position: Long,
        duration: Long
    ): CurrentTrackInfo {
        Log.d(TAG, "Creating CurrentTrackInfo from MediaMetadata for recording: $recordingId")
        
        // Extract basic track info with user-friendly fallbacks
        val title = metadata.title?.toString()?.takeIf { it.isNotBlank() } ?: run {
            Log.w(TAG, "MISSING DATA: track title missing from MediaMetadata")
            "Unknown Track"
        }
        val artist = metadata.artist?.toString()?.takeIf { it.isNotBlank() } ?: run {
            Log.w(TAG, "MISSING DATA: artist missing from MediaMetadata")
            "Unknown Artist"
        }
        val album = metadata.albumTitle?.toString()?.takeIf { it.isNotBlank() } ?: run {
            Log.w(TAG, "MISSING DATA: album missing from MediaMetadata")
            "Unknown Album"
        }
        val trackNumber = metadata.trackNumber
        
        // Extract rich metadata from extras - log missing data, don't fabricate
        val extras = metadata.extras
        val trackUrl = extras?.getString("trackUrl") ?: run {
            Log.e(TAG, "MISSING DATA: trackUrl not found in MediaMetadata extras for recording $recordingId - MediaControllerRepository bug!")
            ""
        }
        val filename = extras?.getString("filename") ?: run {
            Log.w(TAG, "MISSING DATA: filename not found in MediaMetadata extras - track highlighting will fail")
            ""
        }
        val venue = extras?.getString("venue")?.takeIf { it.isNotBlank() } ?: run {
            Log.w(TAG, "MISSING DATA: venue missing from MediaMetadata extras")
            "Unknown Venue"
        }
        val location = extras?.getString("location")?.takeIf { it.isNotBlank() } // Can be null - location is optional
        val showDate = extras?.getString("showDate")?.takeIf { it.isNotBlank() } ?: run {
            Log.w(TAG, "MISSING DATA: showDate missing from MediaMetadata extras")
            "Unknown Date"
        }
        val format = extras?.getString("format")?.takeIf { it.isNotBlank() } ?: run {
            Log.v(TAG, "Format not specified in MediaMetadata extras")
            "Unknown Format"
        }
        
        // No album parsing - use direct metadata only
        
        // Build CurrentTrackInfo with clear logging for missing critical data
        val recordingId = recordingId ?: run {
            Log.e(TAG, "MISSING DATA: recordingId is null - this will break playlist functionality!")
            ""
        }
        
        val showId = showId ?: run {
            Log.w(TAG, "MISSING DATA: showId is null")
            ""
        }

        val trackInfo = CurrentTrackInfo(
            trackUrl = trackUrl,
            recordingId = recordingId,
            showId = showId,
            showDate = showDate,
            venue = venue,
            location = location,
            songTitle = title,
            artist = artist,
            album = album,
            trackNumber = trackNumber,
            filename = filename,
            format = format,
            playbackState = playbackState,
            position = position,
            duration = duration
        )
        
        Log.d(TAG, "CurrentTrackInfo created successfully - Title: ${trackInfo.songTitle}, Recording: ${trackInfo.recordingId}, PlaybackState: ${trackInfo.playbackState}")
        return trackInfo
    }
    
    
    /**
     * Get debug information about current MediaController state
     * 
     * Provides detailed state information for troubleshooting state combination issues.
     * Uses the MediaControllerRepository debug capabilities from Phase 0.
     * 
     * @return Debug information string
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("=== MediaControllerStateUtil Debug Info ===")
            appendLine("MediaControllerRepository Debug Info:")
            appendLine(mediaControllerRepository.getDebugInfo())
            appendLine("=== End MediaControllerStateUtil Debug Info ===")
        }
    }
}