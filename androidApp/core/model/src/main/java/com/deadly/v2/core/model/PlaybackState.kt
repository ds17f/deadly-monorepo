package com.deadly.v2.core.model

import kotlinx.serialization.Serializable

/**
 * Complete media playback state representation
 * 
 * Represents all possible ExoPlayer states to provide precise UI feedback.
 * Eliminates boolean-based state checking in favor of comprehensive enum.
 * 
 * This enum directly maps to ExoPlayer states plus computed combinations:
 * - IDLE: Player.STATE_IDLE
 * - LOADING: Player.STATE_IDLE -> Player.STATE_BUFFERING (during setMediaItems/prepare)
 * - BUFFERING: Player.STATE_BUFFERING (network loading)
 * - READY: Player.STATE_READY + !isPlaying
 * - PLAYING: Player.STATE_READY + isPlaying  
 * - ENDED: Player.STATE_ENDED
 */
@Serializable
enum class PlaybackState {
    /**
     * No media loaded, player inactive
     * UI: Show default play button, no progress
     */
    IDLE,
    
    /**
     * Media items being prepared (setMediaItems + prepare in progress)
     * UI: Show loading spinner, disable controls
     */
    LOADING,
    
    /**
     * Network loading URL content, can't play yet
     * UI: Show buffering indicator on progress bar
     */
    BUFFERING,
    
    /**
     * Ready to play but currently paused
     * UI: Show play button, enable all controls
     */
    READY,
    
    /**
     * Currently playing audio
     * UI: Show pause button, update progress bar
     */
    PLAYING,
    
    /**
     * Playback finished, at end of track
     * UI: Show replay button, progress at 100%
     */
    ENDED;
    
    /**
     * Whether the player is in a loading/buffering state
     * Useful for showing loading indicators
     */
    val isLoading: Boolean
        get() = this in listOf(LOADING, BUFFERING)
    
    /**
     * Whether the player is ready for user interaction
     * Useful for enabling/disabling controls
     */
    val isReady: Boolean
        get() = this in listOf(READY, PLAYING, ENDED)
    
    /**
     * Whether the player is actively playing audio
     * Direct replacement for old `isPlaying` boolean
     */
    val isPlaying: Boolean
        get() = this == PLAYING
    
    /**
     * Whether the player can accept play commands
     * Useful for play button state
     */
    val canPlay: Boolean
        get() = this in listOf(READY, ENDED)
    
    /**
     * Whether the player can accept pause commands
     * Useful for pause button state  
     */
    val canPause: Boolean
        get() = this == PLAYING
    
    companion object {
        /**
         * Default initial state
         */
        val DEFAULT = IDLE
    }
}