package com.deadly.v2.core.model

import kotlinx.serialization.Serializable

/**
 * Playback Status Model
 * 
 * Represents the current playback position state as a cohesive unit.
 * Combines position and duration with computed progress to eliminate
 * fragmented StateFlow interfaces across V2 services.
 * 
 * This model captures the tightly-coupled relationship between current
 * position, duration, and progress - they all change together and represent
 * the same conceptual unit of playback state.
 */
@Serializable
data class PlaybackStatus(
    /**
     * Current playback position in milliseconds
     * Updates frequently during playback (~100-200ms intervals)
     */
    val currentPosition: Long,
    
    /**
     * Track duration in milliseconds
     * Static for each track, but included here for atomic updates
     * with position changes
     */
    val duration: Long
) {
    /**
     * Computed progress (0.0 to 1.0) for progress bars and sliders
     * 
     * Calculated from currentPosition / duration with safe division.
     * This eliminates the need for a separate progress StateFlow across services.
     */
    val progress: Float 
        get() = if (duration > 0) currentPosition.toFloat() / duration else 0f
    
    /**
     * Whether the track has any meaningful duration
     * Useful for UI logic that depends on valid duration
     */
    val hasValidDuration: Boolean
        get() = duration > 0
    
    /**
     * Whether playback has started (position > 0)
     * Useful for distinguishing between "not started" vs "at beginning"
     */
    val hasStarted: Boolean
        get() = currentPosition > 0
    
    companion object {
        /**
         * Default/empty playback status for initial states
         */
        val EMPTY = PlaybackStatus(
            currentPosition = 0L,
            duration = 0L
        )
    }
}