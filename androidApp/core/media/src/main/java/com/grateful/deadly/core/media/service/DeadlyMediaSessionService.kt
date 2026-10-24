package com.grateful.deadly.core.media.service

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.grateful.deadly.core.database.AppPreferences
import com.grateful.deadly.core.media.setPlaybackMetadata
import com.grateful.deadly.core.media.browse.BrowseMediaId
import com.grateful.deadly.core.media.browse.BrowseTreeProvider
import com.grateful.deadly.core.media.equalizer.EqualizerRepository
import com.grateful.deadly.core.media.state.LastPlayedStore
import com.grateful.deadly.core.model.PlayerControlsStyle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * MediaLibraryService with browse tree and last-played persistence.
 *
 * Extends MediaLibraryService (superset of MediaSessionService) to expose
 * a browsable content tree for Android Auto while keeping all existing
 * playback and retry behaviour. It is also the single writer of the
 * last-played state ([LastPlayedStore]), so position is saved in headless
 * Android Auto sessions as well as phone sessions.
 */
@UnstableApi
@AndroidEntryPoint
class DeadlyMediaSessionService : MediaLibraryService() {

    companion object {
        private const val TAG = "DeadlyMediaSessionService"
        private const val TRANSIENT_LOSS_PAUSE_DELAY_MS = 1500L
        private const val DUCK_VOLUME = 0.2f

        // Custom session commands backing the ±10s notification buttons. Using
        // session commands (rather than player commands) keeps the buttons
        // visible even when the underlying COMMAND_SEEK_BACK/FORWARD aren't
        // currently available on the player (e.g., before media is loaded).
        private const val CUSTOM_ACTION_REWIND = "com.grateful.deadly.REWIND_10S"
        private const val CUSTOM_ACTION_FORWARD = "com.grateful.deadly.FORWARD_10S"

        // Last-played persistence: periodic save while playing, and the
        // minimum gap between [SAVE] log lines for the same track.
        private const val PERIODIC_SAVE_INTERVAL_MS = 10_000L
        private const val SAVE_LOG_INTERVAL_MS = 30_000L
    }

    @Inject
    lateinit var lastPlayedStore: LastPlayedStore

    @Inject
    lateinit var browseTreeProvider: BrowseTreeProvider

    @Inject
    lateinit var cacheDataSourceFactory: CacheDataSource.Factory

    @Inject
    lateinit var equalizerRepository: EqualizerRepository

    @Inject
    lateinit var appPreferences: AppPreferences

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var filteringPlayer: ControlStyleFilteringPlayer
    private var mediaSession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Simple queue state tracking
    private var currentRecordingId: String? = null
    private var currentFormat: String? = null

    // Retry logic state
    private var retryCount = 0
    private val maxRetries = 3 // immediate, 1s, 2s

    // Last-played persistence (the only writer of the last_played_track prefs).
    private var periodicSaveJob: Job? = null
    private var lastSaveLogKey: String? = null
    private var lastSaveLogAtMs = 0L
    // True while restoreLastPlayedSession applies the saved snapshot. The
    // transition events it causes must not save: the values are the same, but a
    // save would refresh last_saved and make an old snapshot look new.
    private var applyingRestore = false

    // Search result cache for Android Auto browse
    private var cachedSearchResults: List<MediaItem> = emptyList()

    // Audio focus management — we handle focus manually so that short
    // transient losses (QR-scanner beeps) only duck rather than pause.
    private lateinit var audioManager: AudioManager
    private var hasAudioFocus = false
    private var pausedByTransientLoss = false
    private var duckingForTransientLoss = false
    private val mainHandler = Handler(Looper.getMainLooper())

    @Suppress("NewApi") // field is only used inside Build.VERSION checks
    private var pendingFocusRequest: AudioFocusRequest? = null

    private val transientLossPauseRunnable = Runnable {
        if (duckingForTransientLoss && exoPlayer.isPlaying) {
            Log.d(TAG, "[AUDIO_FOCUS] Transient loss exceeded ${TRANSIENT_LOSS_PAUSE_DELAY_MS}ms, pausing")
            exoPlayer.volume = 1.0f
            duckingForTransientLoss = false
            pausedByTransientLoss = true
            exoPlayer.pause()
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "[AUDIO_FOCUS] Gained")
                mainHandler.removeCallbacks(transientLossPauseRunnable)
                exoPlayer.volume = 1.0f
                if (duckingForTransientLoss) {
                    duckingForTransientLoss = false
                    Log.d(TAG, "[AUDIO_FOCUS] Restored from duck (short transient)")
                }
                if (pausedByTransientLoss) {
                    exoPlayer.play()
                    pausedByTransientLoss = false
                    Log.d(TAG, "[AUDIO_FOCUS] Resuming after transient pause")
                }
                hasAudioFocus = true
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "[AUDIO_FOCUS] Permanent loss")
                mainHandler.removeCallbacks(transientLossPauseRunnable)
                duckingForTransientLoss = false
                pausedByTransientLoss = false
                hasAudioFocus = false
                exoPlayer.volume = 1.0f
                exoPlayer.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "[AUDIO_FOCUS] Transient loss — ducking, will pause if sustained")
                if (exoPlayer.isPlaying) {
                    exoPlayer.volume = DUCK_VOLUME
                    duckingForTransientLoss = true
                    mainHandler.removeCallbacks(transientLossPauseRunnable)
                    mainHandler.postDelayed(transientLossPauseRunnable, TRANSIENT_LOSS_PAUSE_DELAY_MS)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "[AUDIO_FOCUS] Transient loss (can duck)")
                exoPlayer.volume = DUCK_VOLUME
                duckingForTransientLoss = true
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "[MEDIA] DeadlyMediaSessionService onCreate started")

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        // Initialize ExoPlayer with cache-aware data source for offline playback.
        // handleAudioFocus = false: we manage focus ourselves so short transient
        // losses (QR-scanner beeps) only duck instead of pausing.
        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                false
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build()
        Log.d(TAG, "[LIFECYCLE] onCreate service=${hexId(this)} player=${hexId(exoPlayer)} pid=${android.os.Process.myPid()}")

        // Wrap ExoPlayer so we can filter which transport commands are exposed
        // to controllers (notification, lock screen, Android Auto) based on the
        // user's PlayerControlsStyle preference. See ControlStyleFilteringPlayer.
        filteringPlayer = ControlStyleFilteringPlayer(exoPlayer) {
            PlayerControlsStyle.fromString(appPreferences.playerControlsStyle.value)
        }

        // Initialize equalizer with ExoPlayer's audio session
        equalizerRepository.onAudioSessionReady(exoPlayer.audioSessionId)

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
                if (isPlaying && !hasAudioFocus) {
                    requestAudioFocus()
                }
                if (isPlaying) {
                    startPeriodicSave()
                } else {
                    stopPeriodicSave()
                    saveLastPlayed("paused")
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "[ERROR] Player error: ${error.message}", error)
                handlePlayerError(error)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                Log.d(TAG, "[PLAYER] playWhenReady=$playWhenReady reason=${playWhenReadyReasonName(reason)} " +
                    "idx=${exoPlayer.currentMediaItemIndex} pos=${exoPlayer.currentPosition}ms player=${hexId(exoPlayer)}")
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                Log.d(TAG, "[PLAYER] mediaItemTransition reason=${transitionReasonName(reason)} " +
                    "idx=${exoPlayer.currentMediaItemIndex}/${exoPlayer.mediaItemCount} id=${mediaItem?.mediaId}")
                saveLastPlayed("transition")
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                Log.d(TAG, "[PLAYER] positionDiscontinuity reason=${discontinuityReasonName(reason)} " +
                    "${oldPosition.mediaItemIndex}@${oldPosition.positionMs}ms -> " +
                    "${newPosition.mediaItemIndex}@${newPosition.positionMs}ms")
                saveLastPlayed("discontinuity")
            }
        })

        // Create a PendingIntent so tapping the media notification opens the app
        val sessionActivityIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent().setPackage(packageName)
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this, 0, sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Create MediaLibrarySession (callback in constructor, not via setter).
        //
        // setPeriodicPositionUpdateEnabled(false): workaround for an Android Auto
        // companion app bug (16.6.x and earlier) where every periodic position
        // update triggers the AA browse/queue UI to reset its scroll state,
        // making the in-car queue unusable while audio is playing. Disabling the
        // periodic push does not affect transport, metadata, or seek updates —
        // AA still polls the controller for current position when it needs it.
        // See androidx/media#2192.
        val initialStyle = PlayerControlsStyle.fromString(appPreferences.playerControlsStyle.value)
        mediaSession = MediaLibrarySession.Builder(this, filteringPlayer, LibrarySessionCallback())
            .setSessionActivity(sessionActivityPendingIntent)
            .setId("DeadlySession")
            .setBitmapLoader(WaveformFilteringBitmapLoader(this))
            .setPeriodicPositionUpdateEnabled(false)
            .setCustomLayout(buildCustomLayout(initialStyle))
            .build()

        Log.d(TAG, "[MEDIA] DeadlyMediaSessionService onCreate completed (style=$initialStyle)")

        // Observe style changes and re-publish to controllers without restarting playback.
        serviceScope.launch {
            appPreferences.playerControlsStyle.collectLatest { rawStyle ->
                val style = PlayerControlsStyle.fromString(rawStyle)
                withContext(Dispatchers.Main) {
                    filteringPlayer.notifyAvailableCommandsChanged()
                    mediaSession?.setCustomLayout(buildCustomLayout(style))
                    Log.d(TAG, "[MEDIA] Player controls style changed to $style")
                }
            }
        }

        // Metadata is part of each queued MediaItem, so re-publish the queue when
        // the preference changes. Replacing otherwise-identical items preserves
        // the current index, position, and playback state.
        serviceScope.launch {
            appPreferences.nowPlayingMetadataStyle.collectLatest { rawStyle ->
                withContext(Dispatchers.Main) {
                    reapplyNowPlayingMetadata(rawStyle == "SCROBBLING")
                }
            }
        }

        // Restore last played session so AA reconnects see a loaded player
        restoreLastPlayedSession()
    }

    private fun reapplyNowPlayingMetadata(scrobblingMetadata: Boolean) {
        if (exoPlayer.mediaItemCount == 0) return

        val updatedItems = (0 until exoPlayer.mediaItemCount).map { index ->
            val item = exoPlayer.getMediaItemAt(index)
            val metadata = item.mediaMetadata
            val showLabel = metadata.albumTitle ?: metadata.subtitle ?: metadata.artist
            if (showLabel == null) {
                item
            } else {
                item.buildUpon()
                    .setMediaMetadata(
                        metadata.buildUpon()
                            .setPlaybackMetadata(
                                metadata.title,
                                showLabel,
                                scrobblingMetadata,
                            )
                            .build()
                    )
                    .build()
            }
        }
        exoPlayer.replaceMediaItems(0, exoPlayer.mediaItemCount, updatedItems)
        Log.d(TAG, "[MEDIA] Now Playing metadata style changed; queue metadata refreshed")
    }

    private fun buildCustomLayout(style: PlayerControlsStyle): ImmutableList<CommandButton> {
        if (style == PlayerControlsStyle.SKIP_TRACK) {
            return ImmutableList.of()
        }
        val rewindExtras = Bundle().apply {
            putInt(DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX, 0)
        }
        val forwardExtras = Bundle().apply {
            putInt(DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX, 2)
        }
        val rewind = CommandButton.Builder()
            .setSessionCommand(SessionCommand(CUSTOM_ACTION_REWIND, Bundle.EMPTY))
            .setDisplayName("Rewind 10s")
            .setIconResId(com.grateful.deadly.core.media.R.drawable.ic_media_seek_back_10)
            .setExtras(rewindExtras)
            .build()
        val forward = CommandButton.Builder()
            .setSessionCommand(SessionCommand(CUSTOM_ACTION_FORWARD, Bundle.EMPTY))
            .setDisplayName("Forward 10s")
            .setIconResId(com.grateful.deadly.core.media.R.drawable.ic_media_seek_forward_10)
            .setExtras(forwardExtras)
            .build()
        return ImmutableList.of(rewind, forward)
    }

    /**
     * ForwardingPlayer that filters [Player.getAvailableCommands] based on the
     * user-selected [PlayerControlsStyle].
     *
     * Media3 controllers (notification, AA, lock screen) decide which transport
     * buttons to show by reading the player's available commands set. Removing
     * SEEK_BACK/FORWARD hides the ±15s buttons; removing the SEEK_TO_NEXT/PREV
     * pair hides the track-skip buttons. Style changes are pushed to listeners
     * via [notifyAvailableCommandsChanged].
     */
    private class ControlStyleFilteringPlayer(
        inner: Player,
        private val styleProvider: () -> PlayerControlsStyle,
    ) : ForwardingPlayer(inner) {
        private val listeners = mutableListOf<Player.Listener>()

        override fun addListener(listener: Player.Listener) {
            listeners += listener
            super.addListener(listener)
        }

        override fun removeListener(listener: Player.Listener) {
            listeners -= listener
            super.removeListener(listener)
        }

        override fun getAvailableCommands(): Player.Commands {
            val base = super.getAvailableCommands()
            return when (styleProvider()) {
                PlayerControlsStyle.SKIP_TRACK -> base.buildUpon()
                    .removeAll(Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD)
                    .build()
                PlayerControlsStyle.SKIP_SECONDS -> base.buildUpon()
                    .removeAll(
                        Player.COMMAND_SEEK_TO_NEXT,
                        Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    )
                    .build()
                PlayerControlsStyle.BOTH -> base
            }
        }

        /** Force controllers to re-query the available command set after a style change. */
        fun notifyAvailableCommandsChanged() {
            val cmds = availableCommands
            listeners.toList().forEach { it.onAvailableCommandsChanged(cmds) }
        }
    }

    /**
     * Load the last-played snapshot into an empty player (paused) so a controller
     * that connects later (Android Auto, the phone UI) sees a loaded session.
     *
     * This must never overwrite a queue someone else loaded. Track resolution
     * takes about 1s on IO, and in that window a controller can load a queue
     * (onSetMediaItems) or Media3 can apply onPlaybackResumption. So the guards
     * run twice: before resolving and again on Main right before applying.
     */
    private fun restoreLastPlayedSession() {
        if (exoPlayer.mediaItemCount > 0) {
            Log.d(TAG, "[RESTORE] trigger=service_onCreate skipped — player already has ${exoPlayer.mediaItemCount} items")
            return
        }
        val saved = lastPlayedStore.load()
        if (saved == null) {
            Log.d(TAG, "[RESTORE] trigger=service_onCreate skipped — no saved session")
            return
        }

        Log.d(TAG, "[RESTORE] trigger=service_onCreate resolving: show=${saved.showId} rec=${saved.recordingId} " +
            "fmt=${saved.selectedFormat} idx=${saved.trackIndex} pos=${saved.positionMs}ms " +
            "savedAgo=${savedAgeSeconds(saved.lastSavedTime)}s")

        serviceScope.launch {
            try {
                val tracks = browseTreeProvider.resolveRecordingToPlayableTracks(
                    saved.showId, saved.recordingId, saved.selectedFormat
                )
                if (tracks.isEmpty()) {
                    Log.w(TAG, "[RESTORE] No tracks resolved for ${saved.showId}/${saved.recordingId}, not restoring")
                    return@launch
                }

                val safeIndex = saved.trackIndex.coerceIn(0, tracks.size - 1)
                withContext(Dispatchers.Main) {
                    // Re-check on Main: a controller may have loaded a queue while
                    // tracks resolved. Never force playWhenReady=false over it.
                    if (exoPlayer.mediaItemCount > 0) {
                        Log.d(TAG, "[RESTORE] skipped at apply — a controller loaded a queue while resolving: " +
                            "items=${exoPlayer.mediaItemCount} idx=${exoPlayer.currentMediaItemIndex} " +
                            "playWhenReady=${exoPlayer.playWhenReady} id=${exoPlayer.currentMediaItem?.mediaId}")
                        return@withContext
                    }
                    applyingRestore = true
                    try {
                        exoPlayer.setMediaItems(tracks, safeIndex, saved.positionMs)
                        exoPlayer.playWhenReady = false
                        exoPlayer.prepare()
                    } finally {
                        applyingRestore = false
                    }
                    Log.d(TAG, "[RESTORE] Session restored: ${tracks.size} tracks, index=$safeIndex, pos=${saved.positionMs}ms")
                }
            } catch (e: Exception) {
                Log.e(TAG, "[RESTORE] Failed to restore session", e)
            }
        }
    }

    private fun buildRecentMediaItem(): MediaItem? {
        val saved = lastPlayedStore.load() ?: return null
        val showId = saved.showId
        val recordingId = saved.recordingId
        val trackIndex = saved.trackIndex
        val trackTitle = saved.trackTitle
        val format = saved.selectedFormat
        val showDate = saved.showDate
        val venue = saved.venue ?: ""
        val location = saved.location ?: ""

        val subtitle = buildString {
            append(showDate)
            append(" - ")
            append(venue)
            if (location.isNotBlank()) append(" \u2022 $location")
        }

        val mediaId = BrowseMediaId.track(showId, recordingId, trackIndex)
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setPlaybackMetadata(
                        trackTitle,
                        subtitle,
                        appPreferences.nowPlayingMetadataStyle.value == "SCROBBLING",
                    )
                    .setArtworkUri(
                        com.grateful.deadly.core.media.artwork.ArtworkProvider.buildUri(recordingId)
                    )
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
        Log.d(TAG, "[MEDIA] Client requesting session: ${describeController(controllerInfo)} " +
            "service=${hexId(this)} player=${hexId(exoPlayer)} items=${exoPlayer.mediaItemCount} " +
            "idx=${exoPlayer.currentMediaItemIndex} pos=${exoPlayer.currentPosition}ms playing=${exoPlayer.isPlaying}")

        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "[LIFECYCLE] onTaskRemoved service=${hexId(this)} player=${hexId(exoPlayer)} " +
            "items=${exoPlayer.mediaItemCount} playing=${exoPlayer.isPlaying}")
        // Save exact playback position before stopping (synchronous commit)
        saveLastPlayed("onTaskRemoved", commit = true)

        abandonAudioFocus()
        exoPlayer.stop()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.d(TAG, "[LIFECYCLE] onDestroy service=${hexId(this)} player=${hexId(exoPlayer)} " +
            "items=${exoPlayer.mediaItemCount} idx=${exoPlayer.currentMediaItemIndex} " +
            "pos=${exoPlayer.currentPosition}ms")
        stopPeriodicSave()
        // Final save before the player is released (synchronous commit).
        saveLastPlayed("onDestroy", commit = true)
        abandonAudioFocus()
        equalizerRepository.release()
        mediaSession?.run {
            release()
            mediaSession = null
        }
        exoPlayer.release()
        super.onDestroy()
    }

    // ── Last-played persistence ───────────────────────────────────────────

    /**
     * Save the player's current item/index/position to [LastPlayedStore].
     *
     * This is the single save path for the last-played state. It runs at the
     * service level so it also works when Android Auto starts the service with
     * no Activity. Must be called on the main thread (player access).
     */
    private fun saveLastPlayed(trigger: String, commit: Boolean = false) {
        if (applyingRestore) {
            Log.d(TAG, "[SAVE] trigger=$trigger skipped — applying restored snapshot")
            return
        }
        val item = exoPlayer.currentMediaItem ?: run {
            Log.d(TAG, "[SAVE] trigger=$trigger skipped — no current item")
            return
        }
        val index = exoPlayer.currentMediaItemIndex
        val position = exoPlayer.currentPosition
        val saved = lastPlayedStore.save(item, index, position, commit)
        if (saved == null) {
            Log.d(TAG, "[SAVE] trigger=$trigger skipped — item has no show/recording id=${item.mediaId}")
            return
        }

        // Throttle the log: periodic saves would otherwise log every 10s.
        val key = "${saved.showId}|${saved.recordingId}|${saved.trackIndex}"
        val now = System.currentTimeMillis()
        if (trigger != "periodic" || key != lastSaveLogKey || now - lastSaveLogAtMs >= SAVE_LOG_INTERVAL_MS) {
            lastSaveLogKey = key
            lastSaveLogAtMs = now
            Log.d(TAG, "[SAVE] trigger=$trigger show=${saved.showId} rec=${saved.recordingId} " +
                "idx=${saved.trackIndex} pos=${saved.positionMs}ms commit=$commit")
        }
    }

    private fun startPeriodicSave() {
        if (periodicSaveJob?.isActive == true) return
        periodicSaveJob = serviceScope.launch(Dispatchers.Main) {
            while (true) {
                delay(PERIODIC_SAVE_INTERVAL_MS)
                saveLastPlayed("periodic")
            }
        }
    }

    private fun stopPeriodicSave() {
        periodicSaveJob?.cancel()
        periodicSaveJob = null
    }

    // ── Audio focus management ────────────────────────────────────────────

    @Suppress("NewApi") // AudioFocusRequest usage is gated by Build.VERSION check
    private fun requestAudioFocus() {
        if (hasAudioFocus) return

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusChangeListener, mainHandler)
                .build()
            pendingFocusRequest = focusRequest
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        Log.d(TAG, "[AUDIO_FOCUS] Requested, granted=$hasAudioFocus")

        if (!hasAudioFocus) {
            exoPlayer.pause()
        }
    }

    @Suppress("NewApi")
    private fun abandonAudioFocus() {
        mainHandler.removeCallbacks(transientLossPauseRunnable)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pendingFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            pendingFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
        duckingForTransientLoss = false
        pausedByTransientLoss = false
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

    // ── Diagnostic log helpers ───────────────────────────────────────────

    private fun hexId(obj: Any): String = "@" + Integer.toHexString(System.identityHashCode(obj))

    private fun savedAgeSeconds(lastSaved: Long): Long =
        if (lastSaved <= 0L) -1L else (System.currentTimeMillis() - lastSaved) / 1000

    private fun describeController(c: MediaSession.ControllerInfo): String =
        "pkg=${c.packageName} uid=${c.uid} ver=${c.controllerVersion} trusted=${c.isTrusted}"

    /** First few media IDs only, so a 24-track queue stays one short line. */
    private fun summarizeIds(items: List<MediaItem>): String {
        val head = items.take(3).joinToString(",") { it.mediaId }
        return if (items.size > 3) "[$head,…+${items.size - 3}]" else "[$head]"
    }

    private fun playWhenReadyReasonName(reason: Int): String = when (reason) {
        Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "USER_REQUEST"
        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "AUDIO_FOCUS_LOSS"
        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "AUDIO_BECOMING_NOISY"
        Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "REMOTE"
        Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> "END_OF_MEDIA_ITEM"
        Player.PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG -> "SUPPRESSED_TOO_LONG"
        else -> "UNKNOWN($reason)"
    }

    private fun transitionReasonName(reason: Int): String = when (reason) {
        Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "REPEAT"
        Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "AUTO"
        Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "SEEK"
        Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "PLAYLIST_CHANGED"
        else -> "UNKNOWN($reason)"
    }

    private fun discontinuityReasonName(reason: Int): String = when (reason) {
        Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> "AUTO_TRANSITION"
        Player.DISCONTINUITY_REASON_SEEK -> "SEEK"
        Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT -> "SEEK_ADJUSTMENT"
        Player.DISCONTINUITY_REASON_SKIP -> "SKIP"
        Player.DISCONTINUITY_REASON_REMOVE -> "REMOVE"
        Player.DISCONTINUITY_REASON_INTERNAL -> "INTERNAL"
        Player.DISCONTINUITY_REASON_SILENCE_SKIP -> "SILENCE_SKIP"
        else -> "UNKNOWN($reason)"
    }

    private fun playerCommandName(command: Int): String = when (command) {
        Player.COMMAND_PLAY_PAUSE -> "PLAY_PAUSE"
        Player.COMMAND_PREPARE -> "PREPARE"
        Player.COMMAND_STOP -> "STOP"
        Player.COMMAND_SEEK_TO_DEFAULT_POSITION -> "SEEK_TO_DEFAULT_POSITION"
        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM -> "SEEK_IN_CURRENT_MEDIA_ITEM"
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> "SEEK_TO_PREVIOUS_MEDIA_ITEM"
        Player.COMMAND_SEEK_TO_PREVIOUS -> "SEEK_TO_PREVIOUS"
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> "SEEK_TO_NEXT_MEDIA_ITEM"
        Player.COMMAND_SEEK_TO_NEXT -> "SEEK_TO_NEXT"
        Player.COMMAND_SEEK_TO_MEDIA_ITEM -> "SEEK_TO_MEDIA_ITEM"
        Player.COMMAND_SEEK_BACK -> "SEEK_BACK"
        Player.COMMAND_SEEK_FORWARD -> "SEEK_FORWARD"
        Player.COMMAND_SET_SPEED_AND_PITCH -> "SET_SPEED_AND_PITCH"
        Player.COMMAND_SET_SHUFFLE_MODE -> "SET_SHUFFLE_MODE"
        Player.COMMAND_SET_REPEAT_MODE -> "SET_REPEAT_MODE"
        Player.COMMAND_SET_MEDIA_ITEM -> "SET_MEDIA_ITEM"
        Player.COMMAND_CHANGE_MEDIA_ITEMS -> "CHANGE_MEDIA_ITEMS"
        Player.COMMAND_SET_VOLUME -> "SET_VOLUME"
        Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS -> "SET_DEVICE_VOLUME"
        Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS -> "ADJUST_DEVICE_VOLUME"
        Player.COMMAND_RELEASE -> "RELEASE"
        else -> "CMD($command)"
    }

    // ── Media library session callback (browse tree + search) ─────────────────

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            // Expose our custom ±10s session commands to every controller so the
            // notification + AA can render them as buttons. Player commands stay
            // at their defaults.
            Log.d(TAG, "[SESSION] onConnect ${describeController(controller)}")
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(SessionCommand(CUSTOM_ACTION_REWIND, Bundle.EMPTY))
                .add(SessionCommand(CUSTOM_ACTION_FORWARD, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onDisconnected(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ) {
            Log.d(TAG, "[SESSION] onDisconnected ${describeController(controller)}")
            super.onDisconnected(session, controller)
        }

        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int
        ): Int {
            val result = super.onPlayerCommandRequest(session, controller, playerCommand)
            Log.d(TAG, "[SESSION] command=${playerCommandName(playerCommand)} from ${controller.packageName} " +
                "uid=${controller.uid} result=$result")
            return result
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            Log.d(TAG, "[SESSION] onAddMediaItems from ${controller.packageName} " +
                "count=${mediaItems.size} ids=${summarizeIds(mediaItems)}")
            return super.onAddMediaItems(mediaSession, controller, mediaItems)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CUSTOM_ACTION_REWIND -> exoPlayer.seekBack()
                CUSTOM_ACTION_FORWARD -> exoPlayer.seekForward()
                else -> return Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
                )
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val isRecent = params?.isRecent ?: false
            Log.d(TAG, "[BROWSE] onGetLibraryRoot from ${browser.packageName} uid=${browser.uid} " +
                "recent=$isRecent offline=${params?.isOffline} suggested=${params?.isSuggested}")
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
                    Log.d(TAG, "[BROWSE] onGetChildren parent=$parentId from ${browser.packageName} " +
                        "count=${children.size} ids=${summarizeIds(children)}")
                    return@future LibraryResult.ofItemList(children, params)
                }

                val children = browseTreeProvider.getChildren(parentId, page, pageSize)
                Log.d(TAG, "[BROWSE] onGetChildren parent=$parentId page=$page size=$pageSize " +
                    "from ${browser.packageName} count=${children.size}")
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
                Log.d(TAG, "[BROWSE] onGetItem id=$mediaId from ${browser.packageName} found=${item != null}")
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

        // Called by Android Auto, system media resume cards (Android 12+),
        // Wear OS, and Assistant when the user (or AA's auto-resume setting)
        // wants to resume playback after the app has been killed. Returning
        // the last queue + position lets these surfaces show a resumable
        // session; whether playback starts automatically is controlled by
        // the requesting surface (e.g. Android Auto's own auto-resume toggle),
        // matching Spotify/YT Music behaviour.
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = serviceScope.future {
            val saved = lastPlayedStore.load()
            Log.d(TAG, "[RESUME] onPlaybackResumption from ${describeController(controller)} " +
                "savedAgo=${savedAgeSeconds(saved?.lastSavedTime ?: 0L)}s " +
                "playerItems=${exoPlayer.mediaItemCount}")
            if (saved == null) {
                Log.d(TAG, "[RESUME] No last-played session, declining resume")
                throw UnsupportedOperationException("No resumable session")
            }
            val tracks = browseTreeProvider.resolveRecordingToPlayableTracks(
                saved.showId, saved.recordingId, saved.selectedFormat
            )
            if (tracks.isEmpty()) {
                Log.w(TAG, "[RESUME] No tracks resolved for ${saved.showId}/${saved.recordingId}")
                throw UnsupportedOperationException("No tracks resolved")
            }
            val safeIndex = saved.trackIndex.coerceIn(0, tracks.size - 1)
            Log.d(TAG, "[RESUME] trigger=onPlaybackResumption Returning show=${saved.showId} rec=${saved.recordingId} " +
                "${tracks.size} tracks, idx=$safeIndex, pos=${saved.positionMs}ms")
            MediaSession.MediaItemsWithStartPosition(tracks, safeIndex, saved.positionMs)
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = serviceScope.future {
            Log.d(TAG, "[SESSION] onSetMediaItems from ${controller.packageName} uid=${controller.uid} " +
                "count=${mediaItems.size} startIndex=$startIndex startPos=${startPositionMs}ms " +
                "ids=${summarizeIds(mediaItems)}")
            resolveSetMediaItems(mediaItems, startIndex, startPositionMs).also { r ->
                Log.d(TAG, "[SESSION] onSetMediaItems resolved count=${r.mediaItems.size} " +
                    "startIndex=${r.startIndex} startPos=${r.startPositionMs}ms " +
                    "startId=${r.mediaItems.getOrNull(r.startIndex)?.mediaId}")
            }
        }

        private suspend fun resolveSetMediaItems(
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): MediaSession.MediaItemsWithStartPosition {
            if (mediaItems.isEmpty()) {
                return MediaSession.MediaItemsWithStartPosition(
                    mediaItems, startIndex, startPositionMs
                )
            }

            val item = mediaItems[startIndex.coerceIn(0, mediaItems.size - 1)]
            val mediaId = item.mediaId

            return try {
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
                    BrowseMediaId.isFavoriteSong(mediaId) -> {
                        val favSong = BrowseMediaId.parseFavoriteSong(mediaId)
                        if (favSong != null) {
                            val tracks = if (favSong.recordingId.isNotBlank()) {
                                browseTreeProvider.resolveRecordingToPlayableTracks(
                                    favSong.showId, favSong.recordingId,
                                    item.mediaMetadata.extras?.getString("format") ?: "VBR MP3"
                                ).ifEmpty {
                                    browseTreeProvider.resolveShowToPlayableTracks(favSong.showId)
                                }
                            } else {
                                browseTreeProvider.resolveShowToPlayableTracks(favSong.showId)
                            }
                            if (tracks.isNotEmpty()) {
                                val matchIndex = tracks.indexOfFirst { t ->
                                    t.mediaMetadata.trackNumber == favSong.trackNumber
                                }.coerceAtLeast(0)
                                MediaSession.MediaItemsWithStartPosition(
                                    tracks, matchIndex, startPositionMs
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
