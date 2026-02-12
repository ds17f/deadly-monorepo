package com.deadly.v2.core.player.service

import android.content.Context
import android.content.Intent
import com.deadly.v2.core.model.Show
import com.deadly.v2.core.model.Recording
import com.deadly.v2.core.model.Track
import com.deadly.v2.core.player.util.ArchiveUrlUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * V2 Share Service for sharing show and recording information via system share intents
 */
@Singleton
class ShareService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    /**
     * Share a show with recording information
     * @param show The show to share
     * @param recording The current recording being played
     */
    fun shareShow(show: Show, recording: Recording) {
        val message = buildShowShareMessage(show, recording)
        val shareIntent = createShareIntent("Check out this Grateful Dead show!", message)
        context.startActivity(shareIntent)
    }
    
    /**
     * Share a specific track from a recording
     * @param show The show containing the track
     * @param recording The recording containing the track
     * @param track The specific track to share
     * @param currentPositionSeconds Optional current playback position in seconds
     */
    fun shareTrack(show: Show, recording: Recording, track: Track, currentPositionSeconds: Long? = null) {
        val message = buildTrackShareMessage(show, recording, track, currentPositionSeconds)
        val shareIntent = createShareIntent("Check out this Grateful Dead track!", message)
        context.startActivity(shareIntent)
    }
    
    /**
     * Build a formatted message for sharing a show
     */
    private fun buildShowShareMessage(show: Show, recording: Recording): String {
        val url = ArchiveUrlUtil.getRecordingUrl(recording)
        
        return buildString {
            appendLine("🌹⚡💀 Grateful Dead 💀⚡🌹")
            appendLine()
            appendLine("📅 ${show.date}")
            appendLine("📍 ${show.venue.name}")
            if (show.venue.displayLocation.isNotEmpty()) {
                appendLine("🌎 ${show.venue.displayLocation}")
            }
            appendLine()
            
            // Add recording info
            appendLine("🎧 Source: ${recording.sourceType.displayName}")
            
            if (show.hasRating) {
                appendLine("⭐ Rating: ${show.displayRating}")
            }
            
            appendLine()
            appendLine("🔗 Listen on Archive.org:")
            append(url)
        }
    }
    
    /**
     * Build a formatted message for sharing a track
     */
    private fun buildTrackShareMessage(
        show: Show, 
        recording: Recording, 
        track: Track, 
        currentPositionSeconds: Long?
    ): String {
        val url = if (currentPositionSeconds != null && currentPositionSeconds > 0) {
            ArchiveUrlUtil.getTrackUrlWithTime(recording, track, currentPositionSeconds)
        } else {
            ArchiveUrlUtil.getTrackUrl(recording, track)
        }
        
        val trackTitle = track.title ?: track.name
        
        return buildString {
            appendLine("🌹⚡💀 Grateful Dead 💀⚡🌹")
            appendLine()
            appendLine("🎵 $trackTitle")
            appendLine()
            appendLine("📅 ${show.date}")
            appendLine("📍 ${show.venue.name}")
            if (show.venue.displayLocation.isNotEmpty()) {
                appendLine("🌎 ${show.venue.displayLocation}")
            }
            
            // Track info
            if (track.trackNumber != null) {
                appendLine("🔢 Track ${track.trackNumber}")
            }
            if (!track.duration.isNullOrBlank()) {
                appendLine("⏱️ Duration: ${track.duration}")
            }
            
            appendLine()
            
            // Add recording info
            appendLine("🎧 Source: ${recording.sourceType.displayName}")
            
            if (show.hasRating) {
                appendLine("⭐ Rating: ${show.displayRating}")
            }
            
            if (currentPositionSeconds != null && currentPositionSeconds > 0) {
                val minutes = currentPositionSeconds / 60
                val seconds = currentPositionSeconds % 60
                appendLine("▶️ Starting at: ${minutes}:${seconds.toString().padStart(2, '0')}")
            }
            
            appendLine()
            appendLine("🔗 Listen on Archive.org:")
            append(url)
        }
    }
    
    /**
     * Create a system share intent
     */
    private fun createShareIntent(subject: String, message: String): Intent {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, message)
            type = "text/plain"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        
        return Intent.createChooser(shareIntent, "Share via").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
