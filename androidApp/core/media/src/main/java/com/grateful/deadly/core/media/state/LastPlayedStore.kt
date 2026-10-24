package com.grateful.deadly.core.media.state

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persisted "last played" playback state.
 *
 * The only writer is [DeadlyMediaSessionService][com.grateful.deadly.core.media.service.DeadlyMediaSessionService],
 * which saves from its own player so the state is kept whether or not the
 * phone UI exists (e.g. an Android Auto session that starts the service
 * headless). Everything else only reads.
 *
 * Readers: DeadlyMediaSessionService (onCreate restore, onPlaybackResumption,
 * Recent root) and ConnectServiceImpl (compares [LastPlayed.lastSavedTime] with
 * the server state's age at the Connect handshake). The prefs file name and
 * keys persist across app updates; do not change them without a migration.
 */
@Singleton
class LastPlayedStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    companion object {
        const val PREFS_NAME = "last_played_track"
        const val KEY_SHOW_ID = "show_id"
        const val KEY_RECORDING_ID = "recording_id"
        const val KEY_TRACK_INDEX = "track_index"
        const val KEY_POSITION_MS = "position_ms"
        const val KEY_TRACK_TITLE = "track_title"
        const val KEY_TRACK_FILENAME = "track_filename"
        const val KEY_SELECTED_FORMAT = "selected_format"
        const val KEY_SHOW_DATE = "show_date"
        const val KEY_VENUE = "venue"
        const val KEY_LOCATION = "location"
        const val KEY_LAST_SAVED = "last_saved"

        private const val DEFAULT_SHOW_DATE = "1970-01-01"
        private const val DEFAULT_FORMAT = "MP3"
        private const val DEFAULT_TITLE = "Unknown Track"
    }

    /** Complete last played track state for restoration. */
    data class LastPlayed(
        val showId: String,
        val recordingId: String,
        val trackIndex: Int,
        val positionMs: Long,
        val trackTitle: String,
        val trackFilename: String,
        val selectedFormat: String,
        val showDate: String,
        val venue: String?,
        val location: String?,
        val lastSavedTime: Long
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Save the state of [item] at [trackIndex]/[positionMs].
     *
     * Returns the saved state, or null when the item does not carry a
     * showId/recordingId (nothing is written, so a valid earlier snapshot is kept).
     * Use [commit] = true only where the process may die right after the call
     * (onTaskRemoved / onDestroy); otherwise the write is async.
     */
    fun save(item: MediaItem, trackIndex: Int, positionMs: Long, commit: Boolean = false): LastPlayed? {
        val metadata = item.mediaMetadata
        val extras = metadata.extras
        val showId = extras?.getString("showId") ?: return null
        val recordingId = extras.getString("recordingId") ?: return null

        val state = LastPlayed(
            showId = showId,
            recordingId = recordingId,
            trackIndex = trackIndex,
            positionMs = positionMs.coerceAtLeast(0L),
            trackTitle = metadata.title?.toString() ?: DEFAULT_TITLE,
            trackFilename = extras.getString("filename") ?: "",
            selectedFormat = extras.getString("format") ?: DEFAULT_FORMAT,
            showDate = extras.getString("showDate") ?: DEFAULT_SHOW_DATE,
            venue = extras.getString("venue"),
            location = extras.getString("location"),
            lastSavedTime = System.currentTimeMillis()
        )

        val editor = prefs.edit()
            .putString(KEY_SHOW_ID, state.showId)
            .putString(KEY_RECORDING_ID, state.recordingId)
            .putInt(KEY_TRACK_INDEX, state.trackIndex)
            .putLong(KEY_POSITION_MS, state.positionMs)
            .putString(KEY_TRACK_TITLE, state.trackTitle)
            .putString(KEY_TRACK_FILENAME, state.trackFilename)
            .putString(KEY_SELECTED_FORMAT, state.selectedFormat)
            .putString(KEY_SHOW_DATE, state.showDate)
            .putString(KEY_VENUE, state.venue)
            .putString(KEY_LOCATION, state.location)
            .putLong(KEY_LAST_SAVED, state.lastSavedTime)
        if (commit) editor.commit() else editor.apply()
        return state
    }

    /** Load the saved state. Returns null if no valid saved state exists. */
    fun load(): LastPlayed? {
        val showId = prefs.getString(KEY_SHOW_ID, null)
        val recordingId = prefs.getString(KEY_RECORDING_ID, null)
        val trackIndex = prefs.getInt(KEY_TRACK_INDEX, -1)
        val trackTitle = prefs.getString(KEY_TRACK_TITLE, null)
        val trackFilename = prefs.getString(KEY_TRACK_FILENAME, null)
        val selectedFormat = prefs.getString(KEY_SELECTED_FORMAT, null)

        if (showId == null || recordingId == null || trackIndex < 0 ||
            trackTitle == null || trackFilename == null || selectedFormat == null) {
            return null
        }
        return LastPlayed(
            showId = showId,
            recordingId = recordingId,
            trackIndex = trackIndex,
            positionMs = prefs.getLong(KEY_POSITION_MS, 0L),
            trackTitle = trackTitle,
            trackFilename = trackFilename,
            selectedFormat = selectedFormat,
            showDate = prefs.getString(KEY_SHOW_DATE, DEFAULT_SHOW_DATE) ?: DEFAULT_SHOW_DATE,
            venue = prefs.getString(KEY_VENUE, null),
            location = prefs.getString(KEY_LOCATION, null),
            lastSavedTime = prefs.getLong(KEY_LAST_SAVED, 0L)
        )
    }
}
