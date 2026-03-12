package com.grateful.deadly.core.player.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.grateful.deadly.core.model.Show
import com.grateful.deadly.core.model.Recording
import com.grateful.deadly.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Share Service for sharing show and recording information via system share intents
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
     * Share a show with an optional image attached
     */
    fun shareShowWithImage(show: Show, recording: Recording, imageBitmap: Bitmap?) {
        if (imageBitmap == null) {
            shareShow(show, recording)
            return
        }
        val message = buildShowShareMessage(show, recording)
        val uri = saveBitmapToCache(imageBitmap)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, message)
            putExtra(Intent.EXTRA_SUBJECT, "Check out this Grateful Dead show!")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share via").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    /**
     * Share a track with an optional image attached
     */
    fun shareTrackWithImage(show: Show, recording: Recording, track: Track, imageBitmap: Bitmap?, currentPositionSeconds: Long? = null) {
        if (imageBitmap == null) {
            shareTrack(show, recording, track, currentPositionSeconds)
            return
        }
        val message = buildTrackShareMessage(show, recording, track, currentPositionSeconds)
        val uri = saveBitmapToCache(imageBitmap)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, message)
            putExtra(Intent.EXTRA_SUBJECT, "Check out this Grateful Dead track!")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share via").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    private fun saveBitmapToCache(bitmap: Bitmap): android.net.Uri {
        val shareDir = File(context.cacheDir, "share_images")
        shareDir.mkdirs()
        shareDir.listFiles()?.filter { it.name.startsWith("share_") }?.forEach { it.delete() }
        val file = File(shareDir, "share_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * Build a formatted message for sharing a show
     */
    private fun buildShowShareMessage(show: Show, recording: Recording): String {
        val url = "https://share.thedeadly.app/show/${show.id}/recording/${recording.identifier}"

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
            appendLine("🔗 Listen in The Deadly app:")
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
        val url = buildString {
            append("https://share.thedeadly.app/show/${show.id}/recording/${recording.identifier}")
            if (track.trackNumber != null) append("/track/${track.trackNumber}")
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
            appendLine("🔗 Listen in The Deadly app:")
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
