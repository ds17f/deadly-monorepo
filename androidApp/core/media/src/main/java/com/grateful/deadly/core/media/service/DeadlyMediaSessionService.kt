package com.grateful.deadly.core.media.service

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.grateful.deadly.core.media.browse.BrowseMediaId
import com.grateful.deadly.core.media.browse.BrowseTreeProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MediaLibraryService with browse tree and metadata hydration.
 *
 * Extends MediaLibraryService (superset of MediaSessionService) to expose
 * a browsable content tree for Android Auto while keeping all existing
 * playback, retry, and metadata hydration behaviour.
 */
@UnstableApi
@AndroidEntryPoint
class DeadlyMediaSessionService : MediaLibraryService() {

    companion object {
        private const val TAG = "DeadlyMediaSessionService"
    }

    @Inject
    lateinit var metadataHydratorService: MetadataHydratorService

    @Inject
    lateinit var browseTreeProvider: BrowseTreeProvider

    @Inject
    lateinit var cacheDataSourceFactory: CacheDataSource.Factory

    private lateinit var exoPlayer: ExoPlayer
    private var mediaSession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Simple queue state tracking
    private var currentRecordingId: String? = null
    private var currentFormat: String? = null

    // Retry logic state
    private var retryCount = 0
    private val maxRetries = 3 // immediate, 1s, 2s

    // Search result cache for Android Auto browse
    private var cachedSearchResults: List<MediaItem> = emptyList()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "[MEDIA] DeadlyMediaSessionService onCreate started")

        // Initialize ExoPlayer with cache-aware data source for offline playback
        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Add player listeners
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateString = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> {
                        resetRetryCount()
                        "READY"
                    }
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN($playbackState)"
                }
                Log.d(TAG, "[PLAYER] Playback state: $stateString")
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "[AUDIO] isPlaying=$isPlaying")
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "[ERROR] Player error: ${error.message}", error)
                handlePlayerError(error)
            }
        })

        // Create a PendingIntent so tapping the media notification opens the app
        val sessionActivityIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent().setPackage(packageName)
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this, 0, sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Create MediaLibrarySession (callback in constructor, not via setter)
        mediaSession = MediaLibrarySession.Builder(this, exoPlayer, LibrarySessionCallback())
            .setSessionActivity(sessionActivityPendingIntent)
            .setId("DeadlySession")
            .setBitmapLoader(WaveformFilteringBitmapLoader(this))
            .build()

        Log.d(TAG, "[MEDIA] DeadlyMediaSessionService onCreate completed")

        // Restore last played session so AA reconnects see a loaded player
        restoreLastPlayedSession()
    }

    private fun restoreLastPlayedSession() {
        if (exoPlayer.mediaItemCount > 0) {
            Log.d(TAG, "[RESTORE] Player already has items, skipping restore")
            return
        }

        serviceScope.launch {
            try {
                val prefs = getSharedPreferences("last_played_track", MODE_PRIVATE)
                val showId = prefs.getString("show_id", null) ?: return@launch
                val recordingId = prefs.getString("recording_id", null) ?: return@launch
                val format = prefs.getString("selected_format", null) ?: return@launch
                val trackIndex = prefs.getInt("track_index", 0)
                val positionMs = prefs.getLong("position_ms", 0L)

                Log.d(TAG, "[RESTORE] Restoring: show=$showId rec=$recordingId fmt=$format idx=$trackIndex pos=${positionMs}ms")

                val tracks = browseTreeProvider.resolveRecordingToPlayableTracks(showId, recordingId, format)
                if (tracks.isEmpty()) {
                    Log.w(TAG, "[RESTORE] No tracks resolved, falling back to browse tree")
                    return@launch
                }

                val safeIndex = trackIndex.coerceIn(0, tracks.size - 1)
                launch(Dispatchers.Main) {
                    exoPlayer.setMediaItems(tracks, safeIndex, positionMs)
                    exoPlayer.playWhenReady = false
                    exoPlayer.prepare()
                    Log.d(TAG, "[RESTORE] Session restored: ${tracks.size} tracks, index=$safeIndex, pos=${positionMs}ms")
                }
            } catch (e: Exception) {
                Log.e(TAG, "[RESTORE] Failed to restore session", e)
            }
        }
    }

    private fun buildRecentMediaItem(): MediaItem? {
        val prefs = getSharedPreferences("last_played_track", MODE_PRIVATE)
        val showId = prefs.getString("show_id", null) ?: return null
        val recordingId = prefs.getString("recording_id", null) ?: return null
        val trackIndex = prefs.getInt("track_index", 0)
        val trackTitle = prefs.getString("track_title", null) ?: return null
        val format = prefs.getString("selected_format", null) ?: return null
        val showDate = prefs.getString("show_date", null) ?: ""
        val venue = prefs.getString("venue", null) ?: ""

        val mediaId = BrowseMediaId.track(showId, recordingId, trackIndex)
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(trackTitle)
                    .setArtist("$showDate - $venue")
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setExtras(Bundle().apply {
                        putString("showId", showId)
                        putString("recordingId", recordingId)
                        putString("format", format)
                    })
                    .build()
            )
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        Log.d(TAG, "[MEDIA] Client requesting session: ${controllerInfo.packageName}")

        // MediaSession will automatically restore queue/position
        // Schedule metadata hydration after restoration completes
        serviceScope.launch {
            delay(2000) // Give MediaSession time to restore state
            Log.d(TAG, "[MEDIA] Triggering metadata hydration after restoration")
            try {
                metadataHydratorService.hydrateCurrentQueue()
                Log.d(TAG, "[MEDIA] Metadata hydration completed")
            } catch (e: Exception) {
                Log.e(TAG, "Metadata hydration failed", e)
            }
        }

        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Save exact playback position before stopping (synchronous commit)
        val currentItem = exoPlayer.currentMediaItem
        if (currentItem != null) {
            val extras = currentItem.mediaMetadata.extras
            getSharedPreferences("last_played_track", MODE_PRIVATE).edit()
                .putString("show_id", extras?.getString("showId"))
                .putString("recording_id", extras?.getString("recordingId"))
                .putInt("track_index", exoPlayer.currentMediaItemIndex)
                .putLong("position_ms", exoPlayer.currentPosition)
                .putString("track_title", currentItem.mediaMetadata.title?.toString())
                .putString("track_filename", extras?.getString("filename"))
                .putString("selected_format", extras?.getString("format"))
                .putString("show_date", extras?.getString("showDate"))
                .putString("venue", extras?.getString("venue"))
                .putString("location", extras?.getString("location"))
                .putLong("last_saved", System.currentTimeMillis())
                .commit()
            Log.d(TAG, "Saved playback position ${exoPlayer.currentPosition}ms before task removal")
        }

        exoPlayer.stop()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy()")
        mediaSession?.run {
            release()
            mediaSession = null
        }
        exoPlayer.release()
        super.onDestroy()
    }

    // ── Error retry ─────────────────────────────────────────────────────

    /**
     * Handle player errors with exponential backoff retry logic.
     * Retry pattern: immediate → 1 s → 2 s → fail
     */
    private fun handlePlayerError(error: androidx.media3.common.PlaybackException) {
        val isRetryable =
            error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
            error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
            error.message?.contains("Source error") == true

        if (!isRetryable) {
            Log.e(TAG, "Non-retryable error: ${error.errorCode} - ${error.message}")
            retryCount = 0
            return
        }

        if (retryCount >= maxRetries) {
            Log.e(TAG, "Max retries ($maxRetries) exceeded - giving up")
            retryCount = 0
            return
        }

        val delayMs = when (retryCount) {
            0 -> 0L
            1 -> 1000L
            2 -> 2000L
            else -> 0L
        }

        retryCount++
        Log.w(TAG, "Retryable error (attempt $retryCount/$maxRetries) - retrying in ${delayMs}ms")

        serviceScope.launch {
            delay(delayMs)
            try {
                val currentIndex = exoPlayer.currentMediaItemIndex
                val wasPlaying = exoPlayer.playWhenReady
                val currentPosition = exoPlayer.currentPosition

                Log.d(TAG, "Retry $retryCount: index=$currentIndex, pos=${currentPosition}ms, wasPlaying=$wasPlaying")

                exoPlayer.seekTo(currentIndex, maxOf(0L, currentPosition))
                if (wasPlaying) exoPlayer.play()
                exoPlayer.prepare()
            } catch (retryError: Exception) {
                Log.e(TAG, "Retry attempt $retryCount failed", retryError)
            }
        }
    }

    private fun resetRetryCount() {
        if (retryCount > 0) {
            Log.d(TAG, "Playback recovered - resetting retry count")
            retryCount = 0
        }
    }

    // ── Library session callback (browse tree + search) ─────────────────

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val isRecent = params?.isRecent ?: false
            val mediaId = if (isRecent) BrowseMediaId.RECENT_ROOT else BrowseMediaId.ROOT
            val title = if (isRecent) "Recent" else "Grateful Dead"

            val rootItem = MediaItem.Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setIsPlayable(false)
                        .setIsBrowsable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = serviceScope.future {
            try {
                if (parentId == BrowseMediaId.RECENT_ROOT) {
                    val recentItem = buildRecentMediaItem()
                    val children = if (recentItem != null) listOf(recentItem) else emptyList()
                    return@future LibraryResult.ofItemList(children, params)
                }

                val children = browseTreeProvider.getChildren(parentId, page, pageSize)
                LibraryResult.ofItemList(children, params)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting children for $parentId", e)
                LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN)
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> = serviceScope.future {
            try {
                val item = browseTreeProvider.getItem(mediaId)
                if (item != null) {
                    LibraryResult.ofItem(item, null)
                } else {
                    LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting item $mediaId", e)
                LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN)
            }
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            serviceScope.launch {
                try {
                    val results = browseTreeProvider.search(query)
                    cachedSearchResults = results
                    session.notifySearchResultChanged(browser, query, results.size, params)
                } catch (e: Exception) {
                    Log.e(TAG, "Search failed for '$query'", e)
                    cachedSearchResults = emptyList()
                    session.notifySearchResultChanged(browser, query, 0, params)
                }
            }
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val start = page * pageSize
            val paged = cachedSearchResults.drop(start).take(pageSize)
            return Futures.immediateFuture(LibraryResult.ofItemList(paged, params))
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = serviceScope.future {
            if (mediaItems.isEmpty()) {
                return@future MediaSession.MediaItemsWithStartPosition(
                    mediaItems, startIndex, startPositionMs
                )
            }

            val item = mediaItems[startIndex.coerceIn(0, mediaItems.size - 1)]
            val mediaId = item.mediaId

            try {
                when {
                    BrowseMediaId.isShow(mediaId) -> {
                        val tracks = browseTreeProvider.resolveShowToPlayableTracks(
                            BrowseMediaId.parseShowId(mediaId)
                        )
                        if (tracks.isNotEmpty()) {
                            MediaSession.MediaItemsWithStartPosition(tracks, 0, startPositionMs)
                        } else {
                            MediaSession.MediaItemsWithStartPosition(
                                mediaItems, startIndex, startPositionMs
                            )
                        }
                    }
                    BrowseMediaId.isTrack(mediaId) -> {
                        val trackId = BrowseMediaId.parseTrack(mediaId)
                        if (trackId != null) {
                            val extraFormat = item.mediaMetadata.extras?.getString("format")
                            val tracks = if (extraFormat != null) {
                                browseTreeProvider.resolveRecordingToPlayableTracks(
                                    trackId.showId, trackId.recordingId, extraFormat
                                )
                            } else {
                                browseTreeProvider.resolveShowToPlayableTracks(
                                    trackId.showId
                                )
                            }
                            if (tracks.isNotEmpty()) {
                                MediaSession.MediaItemsWithStartPosition(
                                    tracks,
                                    trackId.index.coerceIn(0, tracks.size - 1),
                                    startPositionMs
                                )
                            } else {
                                MediaSession.MediaItemsWithStartPosition(
                                    mediaItems, startIndex, startPositionMs
                                )
                            }
                        } else {
                            MediaSession.MediaItemsWithStartPosition(
                                mediaItems, startIndex, startPositionMs
                            )
                        }
                    }
                    else -> MediaSession.MediaItemsWithStartPosition(
                        mediaItems, startIndex, startPositionMs
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving media items", e)
                MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
            }
        }
    }
}
