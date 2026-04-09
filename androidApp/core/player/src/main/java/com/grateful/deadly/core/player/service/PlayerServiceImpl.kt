package com.grateful.deadly.core.player.service

import android.util.Log
import androidx.media3.common.MediaMetadata
import com.grateful.deadly.core.api.player.PlayerService
import com.grateful.deadly.core.connect.ConnectService
import com.grateful.deadly.core.media.repository.MediaControllerRepository
import com.grateful.deadly.core.media.state.MediaControllerStateUtil
import com.grateful.deadly.core.media.service.MetadataHydratorService
import com.grateful.deadly.core.domain.repository.ShowRepository
import com.grateful.deadly.core.model.Show
import com.grateful.deadly.core.model.Recording
import com.grateful.deadly.core.model.Track
import com.grateful.deadly.core.model.CurrentTrackInfo
import com.grateful.deadly.core.model.PlaybackStatus
import com.grateful.deadly.core.model.QueueInfo
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Player Service Implementation with Metadata Hydration
 * 
 * Real implementation of PlayerService that delegates to MediaControllerRepository.
 * Provides perfect synchronization with Media3 playback state through direct StateFlow delegation.
 * Uses MetadataHydratorService for on-demand metadata enrichment from database.
 * 
 * Key Innovation: Hydrates metadata on-demand when accessed, ensuring fresh show/venue info.
 */
@Singleton
class PlayerServiceImpl @Inject constructor(
    private val mediaControllerRepository: MediaControllerRepository,
    private val mediaControllerStateUtil: MediaControllerStateUtil,
    private val metadataHydratorService: MetadataHydratorService,
    private val showRepository: ShowRepository,
    private val shareService: ShareService,
    private val connectService: ConnectService,
) : PlayerService {
    
    companion object {
        private const val TAG = "PlayerServiceImpl"
        private const val PREVIOUS_TRACK_THRESHOLD_MS = 3000L // 3 seconds
    }
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    // When remote controlling, reflect server playing state; otherwise local state
    override val isPlaying: StateFlow<Boolean> = combine(
        mediaControllerRepository.isPlaying,
        connectService.connectState,
        connectService.isActiveDevice,
    ) { localPlaying, state, isActive ->
        if (state != null && state.showId != null && !isActive && state.activeDeviceId != null) {
            state.playing
        } else {
            localPlaying
        }
    }.stateIn(serviceScope, SharingStarted.Eagerly, false)
    
    override val playbackStatus: StateFlow<PlaybackStatus> = mediaControllerRepository.playbackStatus
    
    // DUPLICATION ELIMINATION: Central CurrentTrackInfo using shared utility
    // Instead of 6+ individual StateFlows extracting metadata pieces,
    // create one comprehensive CurrentTrackInfo and expose it directly
    override val currentTrackInfo: StateFlow<CurrentTrackInfo?> = mediaControllerStateUtil.createCurrentTrackInfoStateFlow(serviceScope)
    
    // Queue information for navigation decisions - direct delegation to MediaControllerStateUtil
    override val queueInfo: StateFlow<QueueInfo> = mediaControllerStateUtil.createQueueInfoStateFlow(serviceScope)
    
    /**
     * Format show date from YYYY-MM-DD to readable format
     */
    private fun formatShowDate(dateString: String): String {
        return try {
            val parts = dateString.split("-")
            if (parts.size == 3) {
                val year = parts[0]
                val month = parts[1].toInt()
                val day = parts[2].toInt()
                
                val monthNames = arrayOf(
                    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
                )
                
                "${monthNames[month - 1]} $day, $year"
            } else {
                dateString
            }
        } catch (e: Exception) {
            dateString
        }
    }
    
    override suspend fun togglePlayPause() {
        Log.d(TAG, "🕒🎵 [SERVICE] PlayerService togglePlayPause called at ${System.currentTimeMillis()}")
        try {
            val state = connectService.connectState.value
            val isActive = connectService.isActiveDevice.value
            val isRemoteControlling = state != null && state.activeDeviceId != null && !isActive
            val serverPlaying = state?.playing ?: false

            if (isRemoteControlling) {
                // Remote control: send command only, wait for server to confirm
                if (serverPlaying) {
                    Log.d(TAG, "togglePlayPause: remote -> sendPause")
                    connectService.sendPause()
                } else {
                    Log.d(TAG, "togglePlayPause: remote -> sendPlay")
                    connectService.sendPlay()
                }
            } else {
                // Active device or no active device: drive local audio optimistically + send command
                val wasPlaying = mediaControllerRepository.isPlaying.value
                Log.d(TAG, "togglePlayPause: local toggle (wasPlaying=$wasPlaying)")
                mediaControllerRepository.togglePlayPause()
                if (wasPlaying) {
                    connectService.sendPause()
                } else {
                    connectService.sendPlay()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "🕒🎵 [ERROR] PlayerService togglePlayPause failed at ${System.currentTimeMillis()}", e)
        }
    }
    
    override suspend fun seekToNext() {
        Log.d(TAG, "🕒🎵 [SERVICE] PlayerService seekToNext called at ${System.currentTimeMillis()}")
        try {
            val wasPlaying = mediaControllerRepository.isPlaying.value
            mediaControllerRepository.seekToNext()
            
            // Auto-play new track if we were paused
            if (!wasPlaying) {
                Log.d(TAG, "🕒🎵 [SERVICE] Was paused - starting playback of new track at ${System.currentTimeMillis()}")
                mediaControllerRepository.play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "🕒🎵 [ERROR] PlayerService seekToNext failed at ${System.currentTimeMillis()}", e)
        }
    }
    
    override suspend fun seekToPrevious() {
        Log.d(TAG, "🕒🎵 [SERVICE] PlayerService seekToPrevious called at ${System.currentTimeMillis()}")
        try {
            val currentPositionMs = mediaControllerRepository.currentPosition.value
            val wasPlaying = mediaControllerRepository.isPlaying.value
            
            if (currentPositionMs > PREVIOUS_TRACK_THRESHOLD_MS) {
                // Restart current track (seek to beginning)
                Log.d(TAG, "Position ${currentPositionMs}ms > ${PREVIOUS_TRACK_THRESHOLD_MS}ms, restarting track")
                mediaControllerRepository.seekToPosition(0L)
                
                // If paused, stay paused after restart (just reset position)
                // If playing, continue playing after restart
                if (wasPlaying) {
                    Log.d(TAG, "Was playing - continuing playback after restart")
                } else {
                    Log.d(TAG, "Was paused - staying paused after restart")
                }
            } else {
                // Check if there's actually a previous track available
                val queueInfo = queueInfo.value
                if (queueInfo.hasPrevious) {
                    // Go to previous track
                    Log.d(TAG, "Position ${currentPositionMs}ms <= ${PREVIOUS_TRACK_THRESHOLD_MS}ms, seeking to previous track")
                    mediaControllerRepository.seekToPrevious()
                    
                    // Auto-play new track if we were paused
                    if (!wasPlaying) {
                        Log.d(TAG, "Was paused - starting playback of previous track")
                        mediaControllerRepository.play()
                    }
                } else {
                    // First track in queue - restart current track even if < 3 seconds
                    Log.d(TAG, "Position ${currentPositionMs}ms <= ${PREVIOUS_TRACK_THRESHOLD_MS}ms, but no previous track - restarting current track")
                    mediaControllerRepository.seekToPosition(0L)
                    
                    // If paused, stay paused after restart
                    if (wasPlaying) {
                        Log.d(TAG, "Was playing - continuing playback after restart")
                    } else {
                        Log.d(TAG, "Was paused - staying paused after restart")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "🕒🎵 [ERROR] PlayerService seekToPrevious failed at ${System.currentTimeMillis()}", e)
        }
    }
    
    override suspend fun seekToPosition(positionMs: Long) {
        Log.d(TAG, "🕒🎵 [SERVICE] PlayerService seekToPosition(${positionMs}ms) called at ${System.currentTimeMillis()}")
        try {
            mediaControllerRepository.seekToPosition(positionMs)
        } catch (e: Exception) {
            Log.e(TAG, "🕒🎵 [ERROR] PlayerService seekToPosition failed at ${System.currentTimeMillis()}", e)
        }
    }
    
    override fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return "0:00"
        
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
    
    override fun formatPosition(positionMs: Long): String {
        return formatDuration(positionMs)
    }
    
    override suspend fun shareCurrentTrack() {
        Log.d(TAG, "Sharing current track")
        try {
            val currentMetadata = mediaControllerRepository.currentTrack.value
            if (currentMetadata == null) {
                Log.w(TAG, "No current track metadata available for sharing")
                return
            }
            
            val showId = currentMetadata.extras?.getString("showId")
            val recordingId = currentMetadata.extras?.getString("recordingId")
            
            if (showId.isNullOrBlank() || recordingId.isNullOrBlank()) {
                Log.w(TAG, "Missing showId or recordingId in metadata for sharing")
                return
            }
            
            // Get show and recording data from repository
            val show = showRepository.getShowById(showId)
            if (show == null) {
                Log.w(TAG, "Show not found for sharing: $showId")
                return
            }
            
            val recording = showRepository.getRecordingById(recordingId)
            if (recording == null) {
                Log.w(TAG, "Recording not found for sharing: $recordingId")
                return
            }
            
            val trackTitle = currentMetadata.title?.toString() ?: "Unknown Track"
            val trackNumber = currentMetadata.trackNumber?.let { if (it > 0) it else null }
            val duration = formatDuration(playbackStatus.value.duration)
            val track = Track(
                name = currentMetadata.extras?.getString("filename") ?: trackTitle,
                title = trackTitle,
                trackNumber = trackNumber,
                duration = duration,
                format = currentMetadata.extras?.getString("format") ?: "mp3"
            )
            
            // Get current position in seconds for time-based sharing
            val currentPositionSeconds = playbackStatus.value.currentPosition / 1000
            
            shareService.shareTrack(show, recording, track, currentPositionSeconds)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing current track", e)
        }
    }
    
    override suspend fun shareCurrentShow() {
        Log.d(TAG, "Sharing current show")
        try {
            val currentMetadata = mediaControllerRepository.currentTrack.value
            if (currentMetadata == null) {
                Log.w(TAG, "No current track metadata available for sharing")
                return
            }
            
            val showId = currentMetadata.extras?.getString("showId")
            val recordingId = currentMetadata.extras?.getString("recordingId")
            
            if (showId.isNullOrBlank() || recordingId.isNullOrBlank()) {
                Log.w(TAG, "Missing showId or recordingId in metadata for sharing")
                return
            }
            
            // Get show and recording data from repository
            val show = showRepository.getShowById(showId)
            if (show == null) {
                Log.w(TAG, "Show not found for sharing: $showId")
                return
            }
            
            val recording = showRepository.getRecordingById(recordingId)
            if (recording == null) {
                Log.w(TAG, "Recording not found for sharing: $recordingId")
                return
            }
            
            shareService.shareShow(show, recording)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing current show", e)
        }
    }
    
}