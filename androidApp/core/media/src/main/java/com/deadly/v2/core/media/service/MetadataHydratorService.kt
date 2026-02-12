package com.deadly.v2.core.media.service

import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.deadly.v2.core.media.repository.MediaControllerRepository
import com.deadly.v2.core.domain.repository.ShowRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * V2 Metadata Hydration Service
 * 
 * Responsible for enriching MediaItems restored by MediaSession with fresh metadata
 * from the database. Uses embedded MediaId to identify show/recording context and
 * looks up current venue, date, and other display information.
 * 
 * Core Strategy:
 * - MediaSession handles queue/position persistence (built-in)
 * - This service handles metadata enrichment from database (on-demand)
 * - No duplicate persistence - single source of truth architecture
 */
@Singleton
class MetadataHydratorService @Inject constructor(
    private val showRepository: ShowRepository,
    private val mediaControllerRepository: MediaControllerRepository
) {
    
    companion object {
        private const val TAG = "MetadataHydratorService"
        private const val MEDIA_ID_SEPARATOR = "|"
    }
    
    private val hydratorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * Hydrate the current queue with fresh metadata from database
     * Called after MediaSession restoration to enrich restored MediaItems
     */
    suspend fun hydrateCurrentQueue() {
        Log.d(TAG, "Starting queue hydration...")
        
        try {
            // Get current MediaItems from MediaController
            val mediaItems = mediaControllerRepository.getCurrentMediaItems()
            if (mediaItems.isEmpty()) {
                Log.d(TAG, "No MediaItems in queue to hydrate")
                return
            }
            
            Log.d(TAG, "Found ${mediaItems.size} MediaItems to hydrate")
            
            // Hydrate each MediaItem with fresh metadata
            val hydratedItems = mediaItems.map { mediaItem ->
                hydrateMediaItem(mediaItem)
            }
            
            // Count how many were actually hydrated
            val hydratedCount = hydratedItems.count { item ->
                item.mediaMetadata.extras?.getBoolean("isHydrated", false) == true
            }
            
            Log.d(TAG, "Successfully hydrated $hydratedCount of ${mediaItems.size} MediaItems")
            
            // Update MediaController queue with hydrated items
            mediaControllerRepository.updateQueueWithHydratedItems(hydratedItems)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during queue hydration", e)
        }
    }
    
    /**
     * Hydrate a single MediaItem with fresh metadata from database
     * Uses MediaId to identify the show/recording and enriches extras
     */
    suspend fun hydrateMediaItem(mediaItem: MediaItem): MediaItem {
        try {
            val mediaIdData = parseMediaId(mediaItem.mediaId ?: "")
            if (mediaIdData == null) {
                Log.w(TAG, "Cannot parse MediaId: ${mediaItem.mediaId}")
                return mediaItem // Return unchanged if can't parse
            }
            
            val (showId, recordingId, trackIndex) = mediaIdData
            Log.d(TAG, "Hydrating MediaItem: show=$showId, recording=$recordingId, track=$trackIndex")
            
            // Get fresh show data from database
            val show = showRepository.getShowById(showId)
            if (show == null) {
                Log.w(TAG, "Show not found in database: $showId")
                return mediaItem // Return unchanged if show not found
            }
            
            // Get recordings for this show and find the specific one
            val recordings = showRepository.getRecordingsForShow(showId)
            val recording = recordings.find { it.identifier == recordingId }
            if (recording == null) {
                Log.w(TAG, "Recording not found: $recordingId in show $showId")
                return mediaItem // Return unchanged if recording not found  
            }
            
            // Build enhanced MediaItem with fresh metadata
            return mediaItem.buildUpon()
                .setMediaMetadata(
                    mediaItem.mediaMetadata.buildUpon()
                        .setAlbumTitle(
                            // Format: "Apr 3, 1990 - The Omni" or just show date if no venue
                            if (!show.venue.name.isNullOrBlank()) {
                                "${formatShowDate(show.date)} - ${show.venue.name}"
                            } else {
                                formatShowDate(show.date)
                            }
                        )
                        .setExtras(Bundle().apply {
                            // Preserve existing extras
                            mediaItem.mediaMetadata.extras?.let { existingExtras ->
                                putAll(existingExtras)
                            }
                            
                            // Update with fresh database values
                            putString("showId", show.id)
                            putString("recordingId", recording.identifier) 
                            putString("showDate", show.date)
                            putString("venue", show.venue.name)
                            putString("location", show.location.displayText)
                            
                            // Add computed values
                            putString("hydratedAt", System.currentTimeMillis().toString())
                            putBoolean("isHydrated", true)
                        })
                        .build()
                )
                .build()
                
        } catch (e: Exception) {
            Log.e(TAG, "Error hydrating MediaItem: ${mediaItem.mediaId}", e)
            return mediaItem // Return unchanged on error
        }
    }
    
    /**
     * Parse MediaId to extract show/recording/track identifiers
     * Format: "showId|recordingId|trackIndex"
     * Returns null if parsing fails
     */
    private fun parseMediaId(mediaId: String): Triple<String, String, Int>? {
        return try {
            val parts = mediaId.split(MEDIA_ID_SEPARATOR)
            if (parts.size != 3) {
                Log.w(TAG, "Invalid MediaId format: $mediaId (expected 3 parts)")
                return null
            }
            
            val showId = parts[0].trim()
            val recordingId = parts[1].trim() 
            val trackIndex = parts[2].trim().toInt()
            
            if (showId.isEmpty() || recordingId.isEmpty() || trackIndex < 0) {
                Log.w(TAG, "Invalid MediaId values: showId='$showId', recordingId='$recordingId', trackIndex=$trackIndex")
                return null
            }
            
            Triple(showId, recordingId, trackIndex)
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse MediaId: $mediaId", e)
            null
        }
    }
    
    /**
     * Format show date from YYYY-MM-DD to readable format
     * Copied from MediaControllerRepository pattern
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
    
    /**
     * Start monitoring for hydration opportunities
     * Currently a placeholder - could be used to re-hydrate on database updates
     */
    fun startMonitoring() {
        Log.d(TAG, "Starting hydration monitoring")
        // Future: Could monitor for database changes and re-hydrate affected MediaItems
    }
    
    /**
     * Check if a MediaItem has been hydrated with fresh metadata
     */
    fun isHydrated(mediaItem: MediaItem): Boolean {
        return mediaItem.mediaMetadata.extras?.getBoolean("isHydrated", false) ?: false
    }
}