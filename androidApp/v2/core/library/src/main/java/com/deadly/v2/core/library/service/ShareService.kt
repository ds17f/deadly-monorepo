package com.deadly.v2.core.library.service

import android.content.Context
import android.content.Intent
import com.deadly.v2.core.model.Show
import com.deadly.v2.core.model.Recording
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * V2 Library ShareService for sharing shows from library
 * 
 * Simplified sharing service focused on library show sharing functionality.
 * Creates formatted messages with show details and Archive.org URLs.
 */
@Singleton
class ShareService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    /**
     * Share a show with recording information
     * @param show The show to share
     * @param recording The recording to share
     */
    fun shareShow(show: Show, recording: Recording) {
        val message = buildShowShareMessage(show, recording)
        val shareIntent = createShareIntent("Check out this Grateful Dead show!", message)
        context.startActivity(shareIntent)
    }
    
    /**
     * Build a formatted message for sharing a show
     */
    private fun buildShowShareMessage(show: Show, recording: Recording): String {
        val url = "https://archive.org/details/${recording.identifier}"
        
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