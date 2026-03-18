package com.grateful.deadly.core.player.service

import android.content.Context
import android.content.Intent
import com.grateful.deadly.core.model.Show
import com.grateful.deadly.core.model.Recording
import com.grateful.deadly.core.model.Track
import com.grateful.deadly.core.database.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShareService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences
) {

    fun shareShow(show: Show, recording: Recording) {
        val url = "${appPreferences.shareBaseUrl}/shows/${show.id}/recording/${recording.identifier}"
        val shareIntent = createShareIntent(url)
        context.startActivity(shareIntent)
    }

    fun shareTrack(show: Show, recording: Recording, track: Track, currentPositionSeconds: Long? = null) {
        val url = buildString {
            append("${appPreferences.shareBaseUrl}/shows/${show.id}/recording/${recording.identifier}")
            if (track.trackNumber != null) append("/track/${track.trackNumber}")
        }
        val shareIntent = createShareIntent(url)
        context.startActivity(shareIntent)
    }

    private fun createShareIntent(message: String): Intent {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, message)
            type = "text/plain"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return Intent.createChooser(shareIntent, "Share via").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
