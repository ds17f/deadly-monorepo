package com.deadly.v2.core.api.player

import com.deadly.v2.core.model.CurrentTrackInfo
import com.deadly.v2.core.model.PlaybackStatus
import com.deadly.v2.core.model.QueueInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * V2 Player Service Interface
 * 
 * Business logic interface for V2 Player functionality.
 * Provides reactive state streams and playback control commands.
 * 
 * Implementation delegates to V2 MediaControllerRepository for state synchronization.
 */
interface PlayerService {
    
    /**
     * Whether audio is currently playing
     */
    val isPlaying: StateFlow<Boolean>
    
    /**
     * Unified playback position state with computed progress
     * Contains currentPosition, duration, and computed progress as cohesive unit
     */
    val playbackStatus: StateFlow<PlaybackStatus>
    
    /**
     * Comprehensive current track information
     * Contains all metadata, playback state, and navigation data
     */
    val currentTrackInfo: StateFlow<CurrentTrackInfo?>
    
    /**
     * Queue information for navigation decisions
     * Contains currentIndex, totalTracks, and computed navigation properties
     */
    val queueInfo: StateFlow<QueueInfo>
    
    /**
     * Toggle between play and pause states
     */
    suspend fun togglePlayPause()
    
    /**
     * Skip to next track
     */
    suspend fun seekToNext()
    
    /**
     * Skip to previous track
     */
    suspend fun seekToPrevious()
    
    /**
     * Seek to specific position in current track
     */
    suspend fun seekToPosition(positionMs: Long)
    
    /**
     * Format duration milliseconds to MM:SS string
     */
    fun formatDuration(durationMs: Long): String
    
    /**
     * Format position milliseconds to MM:SS string
     */
    fun formatPosition(positionMs: Long): String
    
    /**
     * Get debug information about current MediaMetadata for inspection
     */
    suspend fun getDebugMetadata(): Map<String, String?>
    
    /**
     * Share currently playing track with current playback position
     */
    suspend fun shareCurrentTrack()
    
    /**
     * Share current show and recording
     */
    suspend fun shareCurrentShow()
}