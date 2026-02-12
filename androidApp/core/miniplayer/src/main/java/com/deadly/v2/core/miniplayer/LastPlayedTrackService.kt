package com.deadly.v2.core.miniplayer

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.media3.common.MediaMetadata
import com.deadly.v2.core.media.repository.MediaControllerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * V2 Last Played Track Persistence Service
 * 
 * Internal service (no API interface) for persisting and restoring last played track state.
 * Critical for maintaining user's playback position across app restarts.
 * 
 * Combines both persistence and monitoring in a single focused service.
 */
@Singleton
class LastPlayedTrackService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaControllerRepository: MediaControllerRepository
) {
    
    companion object {
        private const val TAG = "LastPlayedTrackService"
        private const val PREFS_NAME = "v2_last_played_track"
        private const val KEY_SHOW_ID = "show_id"
        private const val KEY_RECORDING_ID = "recording_id"
        private const val KEY_TRACK_INDEX = "track_index"
        private const val KEY_POSITION_MS = "position_ms"
        private const val KEY_TRACK_TITLE = "track_title"
        private const val KEY_TRACK_FILENAME = "track_filename"
        private const val KEY_SELECTED_FORMAT = "selected_format"
        private const val KEY_SHOW_DATE = "show_date"
        private const val KEY_VENUE = "venue"
        private const val KEY_LOCATION = "location"
        private const val KEY_LAST_SAVED = "last_saved"
    }
    
    /**
     * Complete last played track state for restoration
     */
    data class LastPlayedTrack(
        val showId: String,              // For playlist navigation
        val recordingId: String,         // For playback restoration
        val trackIndex: Int,
        val positionMs: Long,
        val trackTitle: String,
        val trackFilename: String,
        val selectedFormat: String,      // Store V2 format selection
        val showDate: String,            // For MiniPlayer display
        val venue: String?,              // For MiniPlayer display
        val location: String?,           // For MiniPlayer display
        val lastSavedTime: Long
    )
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val saveScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Save current track state to SharedPreferences
     * Called automatically during playback every 10 seconds
     */
    fun saveCurrentTrack(
        showId: String,
        recordingId: String,
        trackIndex: Int,
        positionMs: Long,
        trackTitle: String,
        trackFilename: String,
        selectedFormat: String,
        showDate: String = "1970-01-01",
        venue: String? = null,
        location: String? = null
    ) {
        Log.d(TAG, "Saving last played track: $trackTitle at ${positionMs}ms")
        
        prefs.edit()
            .putString(KEY_SHOW_ID, showId)
            .putString(KEY_RECORDING_ID, recordingId)
            .putInt(KEY_TRACK_INDEX, trackIndex)
            .putLong(KEY_POSITION_MS, positionMs)
            .putString(KEY_TRACK_TITLE, trackTitle)
            .putString(KEY_TRACK_FILENAME, trackFilename)
            .putString(KEY_SELECTED_FORMAT, selectedFormat)
            .putString(KEY_SHOW_DATE, showDate)
            .putString(KEY_VENUE, venue)
            .putString(KEY_LOCATION, location)
            .putLong(KEY_LAST_SAVED, System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Load last played track info from SharedPreferences
     * Returns null if no valid saved state exists
     */
    fun getLastPlayedTrack(): LastPlayedTrack? {
        val showId = prefs.getString(KEY_SHOW_ID, null)
        val recordingId = prefs.getString(KEY_RECORDING_ID, null)
        val trackIndex = prefs.getInt(KEY_TRACK_INDEX, -1)
        val positionMs = prefs.getLong(KEY_POSITION_MS, 0L)
        val trackTitle = prefs.getString(KEY_TRACK_TITLE, null)
        val trackFilename = prefs.getString(KEY_TRACK_FILENAME, null)
        val selectedFormat = prefs.getString(KEY_SELECTED_FORMAT, null)
        val lastSaved = prefs.getLong(KEY_LAST_SAVED, 0L)
        
        return if (showId != null && recordingId != null && trackIndex >= 0 &&
                   trackTitle != null && trackFilename != null && selectedFormat != null) {
            LastPlayedTrack(
                showId = showId,
                recordingId = recordingId,
                trackIndex = trackIndex,
                positionMs = positionMs,
                trackTitle = trackTitle,
                trackFilename = trackFilename,
                selectedFormat = selectedFormat,
                showDate = prefs.getString(KEY_SHOW_DATE, "1970-01-01") ?: "1970-01-01",
                venue = prefs.getString(KEY_VENUE, null),
                location = prefs.getString(KEY_LOCATION, null),
                lastSavedTime = lastSaved
            )
        } else {
            Log.d(TAG, "No valid last played track found")
            null
        }
    }
    
    /**
     * Restore last played track on app start - makes MiniPlayer appear
     * CRITICAL: This method ensures users never lose their playback position
     */
    suspend fun restoreLastPlayedTrack() {
        try {
            val lastTrack = getLastPlayedTrack()
            if (lastTrack == null) {
                Log.d(TAG, "No last played track to restore")
                return
            }
            
            Log.d(TAG, "Restoring last played track: ${lastTrack.trackTitle}")
            
            // Load track into MediaController with exact position (without auto-playing)
            mediaControllerRepository.playTrack(
                trackIndex = lastTrack.trackIndex,
                recordingId = lastTrack.recordingId,
                format = lastTrack.selectedFormat,
                showId = lastTrack.showId,
                showDate = lastTrack.showDate,
                venue = lastTrack.venue,
                location = lastTrack.location,
                position = lastTrack.positionMs,
                autoPlay = false
            )
            
            Log.d(TAG, "Successfully restored last played track")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore last played track", e)
        }
    }
    
    /**
     * Start monitoring playback state for automatic persistence
     * Combines persistence + monitoring in single service (no separate monitor needed)
     * 
     * Auto-saves track state every 10 seconds during playback - CRITICAL for reliability
     */
    fun startMonitoring() {
        Log.d(TAG, "Starting playback state monitoring")
        
        saveScope.launch {
            combine(
                mediaControllerRepository.isPlaying,
                mediaControllerRepository.currentPosition,
                mediaControllerRepository.currentShowId,
                mediaControllerRepository.currentRecordingId,
                mediaControllerRepository.currentTrackIndex,
                mediaControllerRepository.currentTrack
            ) { values ->
                val isPlaying = values[0] as Boolean
                val position = values[1] as Long
                val showId = values[2] as String?
                val recordingId = values[3] as String?
                val trackIndex = values[4] as Int
                val trackMetadata = values[5] as MediaMetadata?
                
                // Auto-save every 10 seconds during playback - ensures we never lose position
                if (isPlaying && showId != null && recordingId != null && trackMetadata != null) {
                    
                    saveCurrentTrack(
                        showId = showId,
                        recordingId = recordingId,
                        trackIndex = trackIndex,
                        positionMs = position,
                        trackTitle = trackMetadata.title?.toString() ?: "Unknown Track",
                        trackFilename = extractFilename(trackMetadata),
                        selectedFormat = extractFormat(trackMetadata)
                    )
                }
            }.collect { /* State saved above */ }
        }
    }
    
    /**
     * Clear saved track state
     */
    fun clearLastPlayedTrack() {
        Log.d(TAG, "Clearing last played track")
        prefs.edit().clear().apply()
    }
    
    /**
     * Extract filename from MediaMetadata
     */
    private fun extractFilename(metadata: MediaMetadata): String {
        return metadata.extras?.getString("filename") ?: ""
    }
    
    /**
     * Extract format from MediaMetadata
     */
    private fun extractFormat(metadata: MediaMetadata): String {
        return metadata.extras?.getString("format") ?: "MP3"
    }
}