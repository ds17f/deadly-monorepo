package com.deadly.v2.feature.splash.model

/**
 * V2 database initialization progress tracking
 */
data class Progress(
    val phase: Phase,
    val totalShows: Int,
    val processedShows: Int,
    val currentShow: String,
    val totalVenues: Int = 0,
    val processedVenues: Int = 0,
    val totalRecordings: Int = 0,
    val processedRecordings: Int = 0,
    val currentRecording: String = "",
    val totalTracks: Int = 0,
    val processedTracks: Int = 0,
    val startTimeMs: Long = 0L,
    val error: String? = null
) {
    val progressPercentage: Float
        get() = when (phase) {
            Phase.IMPORTING_SHOWS -> if (totalShows > 0) (processedShows.toFloat() / totalShows) * 100f else 0f
            Phase.IMPORTING_RECORDINGS -> if (totalRecordings > 0) (processedRecordings.toFloat() / totalRecordings) * 100f else 0f
            Phase.COMPUTING_VENUES -> if (totalVenues > 0) (processedVenues.toFloat() / totalVenues) * 100f else 0f
            else -> 0f
        }
        
    val currentItem: String
        get() = when (phase) {
            Phase.IMPORTING_SHOWS -> currentShow
            Phase.IMPORTING_RECORDINGS -> currentRecording
            else -> currentShow // fallback
        }
        
    val isInProgress: Boolean
        get() = phase in listOf(Phase.CHECKING, Phase.EXTRACTING, Phase.IMPORTING_SHOWS, Phase.COMPUTING_VENUES, Phase.IMPORTING_RECORDINGS)
        
    /**
     * Get elapsed time since start in a human-readable format
     */
    fun getElapsedTimeString(currentTimeMs: Long = System.currentTimeMillis()): String {
        if (startTimeMs == 0L) return "00:00"
        
        val elapsedMs = currentTimeMs - startTimeMs
        val totalSeconds = elapsedMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        
        return String.format("%02d:%02d", minutes, seconds)
    }
}