package com.deadly.v2.core.model

/**
 * Queue Information Model
 * 
 * Provides observable queue state information that services can use to make
 * business logic decisions about navigation, UI state, and playback behavior.
 * 
 * This follows the same pattern as CurrentTrackInfo - providing rich information
 * rather than pre-computed boolean decisions, allowing services flexibility
 * in how they interpret and use queue state.
 */
data class QueueInfo(
    val currentIndex: Int,
    val totalTracks: Int,
    val isEmpty: Boolean = totalTracks == 0
) {
    /**
     * True if currently at the first track in the queue
     */
    val isFirstTrack: Boolean get() = currentIndex == 0
    
    /**
     * True if currently at the last track in the queue  
     */
    val isLastTrack: Boolean get() = currentIndex == totalTracks - 1
    
    /**
     * True if there is a next track available in the queue
     */
    val hasNext: Boolean get() = !isEmpty && !isLastTrack
    
    /**
     * True if there is a previous track available in the queue
     */
    val hasPrevious: Boolean get() = !isEmpty && !isFirstTrack
    
    /**
     * Progress through the queue as a percentage (0.0 to 1.0)
     */
    val queueProgress: Float get() = if (totalTracks > 0) currentIndex.toFloat() / totalTracks else 0f
    
    companion object {
        /**
         * Empty queue state
         */
        val EMPTY = QueueInfo(currentIndex = 0, totalTracks = 0)
    }
}