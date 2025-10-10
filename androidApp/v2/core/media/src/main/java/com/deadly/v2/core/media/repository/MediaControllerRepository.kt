package com.deadly.v2.core.media.repository

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.deadly.v2.core.media.service.DeadlyMediaSessionService
import com.deadly.v2.core.media.exception.FormatNotAvailableException
import com.deadly.v2.core.model.PlaybackStatus
import com.deadly.v2.core.model.PlaybackState
import com.deadly.v2.core.model.Track as V2Track
import com.deadly.v2.core.network.archive.service.ArchiveService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * V2 MediaController repository for playback state management
 * 
 * Loads tracks directly from ArchiveService and maintains centralized playback state.
 * All V2 screens observe this repository for consistent playback information.
 */
@Singleton
class MediaControllerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val archiveService: ArchiveService
) {
    companion object {
        private const val TAG = "MediaControllerRepository"
    }
    
    // Connection state
    enum class ConnectionState {
        Disconnected, Connecting, Connected, Failed
    }
    
    private var mediaController: MediaController? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    
    // Coroutine scope for async operations
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    // Connection state management
    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    // Command queue for operations while connecting
    private val pendingCommands = mutableListOf<suspend () -> Unit>()
    
    // Playback state flows
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _playbackState = MutableStateFlow(PlaybackState.DEFAULT)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    
    // Raw ExoPlayer state tracking for combined PlaybackState calculation
    private var currentExoPlayerState: Int = Player.STATE_IDLE
    private var currentIsPlaying: Boolean = false
    
    private val _currentTrack = MutableStateFlow<MediaMetadata?>(null)
    val currentTrack: StateFlow<MediaMetadata?> = _currentTrack.asStateFlow()
    
    private val _currentMediaItem = MutableStateFlow<androidx.media3.common.MediaItem?>(null)
    val currentMediaItem: StateFlow<androidx.media3.common.MediaItem?> = _currentMediaItem.asStateFlow()
    
    // MiniPlayer state flows
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()
    
    private val _currentShowId = MutableStateFlow<String?>(null)
    val currentShowId: StateFlow<String?> = _currentShowId.asStateFlow()
    
    private val _currentRecordingId = MutableStateFlow<String?>(null)
    val currentRecordingId: StateFlow<String?> = _currentRecordingId.asStateFlow()
    
    private val _currentTrackIndex = MutableStateFlow(0)
    val currentTrackIndex: StateFlow<Int> = _currentTrackIndex.asStateFlow()
    
    private val _mediaItemCount = MutableStateFlow(0)
    val mediaItemCount: StateFlow<Int> = _mediaItemCount.asStateFlow()
    
    // Unified playback status with computed progress
    val playbackStatus: StateFlow<PlaybackStatus> = combine(
        _currentPosition, _duration
    ) { pos, dur -> 
        PlaybackStatus(
            currentPosition = pos,
            duration = dur
        )
    }.stateIn(
        scope = repositoryScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = PlaybackStatus.EMPTY
    )
    
    init {
        // Start async connection immediately
        connectToService()
    }
    
    /**
     * Play all tracks for a recording
     * Loads tracks from ArchiveService and starts playback
     */
    suspend fun playAll(
        recordingId: String, 
        format: String, 
        showId: String,
        showDate: String,
        venue: String?,
        location: String?,
        startPosition: Long = 0L,
        autoPlay: Boolean = true
    ) {
        Log.d(TAG, "playAll: $recordingId ($format) at position $startPosition")
        
        executeWhenConnected {
            val controller = mediaController
            
            if (controller != null) {
                try {
                    Log.d(TAG, "Loading tracks from ArchiveService for recording: $recordingId")
                    
                    // Get raw V2 Track models from ArchiveService
                    val result = archiveService.getRecordingTracks(recordingId)
                    if (result.isSuccess) {
                        val rawTracks = result.getOrNull() ?: emptyList()
                        Log.d(TAG, "Loaded ${rawTracks.size} raw tracks from ArchiveService")
                        
                        // Filter by format - explicit failure if not found
                        val filteredTracks = rawTracks.filter { track ->
                            track.format.equals(format, ignoreCase = true)
                        }
                        
                        if (filteredTracks.isEmpty()) {
                            // Explicit failure - throw exception with debugging info
                            val availableFormats = rawTracks.map { it.format }.distinct()
                            throw FormatNotAvailableException(
                                recordingId = recordingId,
                                requestedFormat = format,
                                availableFormats = availableFormats
                            )
                        }
                        
                        Log.d(TAG, "Found ${filteredTracks.size} tracks for format: $format")
                        
                        // Set position immediately from known value (before MediaController is ready)
                        _currentPosition.value = startPosition
                        // Set duration from first track (playAll starts at track 0)
                        if (filteredTracks.isNotEmpty()) {
                            _duration.value = parseDurationToMs(filteredTracks[0].duration)
                        }
                        
                        // Convert to MediaItems
                        val mediaItems = convertToMediaItems(recordingId, filteredTracks, showId, showDate, venue, location, format)
                        
                        // Set media items and optionally start playing at position
                        Log.d(TAG, "🕒🎵 [V2-URL] Setting ${mediaItems.size} media items to MediaController at ${System.currentTimeMillis()}")
                        mediaItems.forEach { item ->
                            Log.d(TAG, "🕒🎵 [V2-URL] Loading URL: ${item.localConfiguration?.uri} at ${System.currentTimeMillis()}")
                        }
                        
                        // Set LOADING state before ExoPlayer operations
                        _playbackState.value = PlaybackState.LOADING
                        Log.d(TAG, "🕒🎵 [V2-STATE] Manual LOADING state set before setMediaItems")
                        
                        controller.setMediaItems(mediaItems, 0, startPosition)
                        Log.d(TAG, "🕒🎵 [V2-URL] MediaController.setMediaItems() completed, calling prepare() at ${System.currentTimeMillis()}")
                        controller.prepare()
                        Log.d(TAG, "🕒🎵 [V2-URL] MediaController.prepare() completed at ${System.currentTimeMillis()}")
                        
                        // MediaController will provide accurate updates once ready via callbacks
                        
                        if (autoPlay) {
                            Log.d(TAG, "🕒🎵 [V2-URL] Calling controller.play() to start playback at ${System.currentTimeMillis()}")
                            controller.play()
                            Log.d(TAG, "🕒🎵 [V2-URL] controller.play() call completed at ${System.currentTimeMillis()}")
                        } else {
                            Log.d(TAG, "🕒🎵 [V2-URL] Recording loaded at position $startPosition (paused) at ${System.currentTimeMillis()}")
                        }
                        
                    } else {
                        Log.e(TAG, "Failed to load tracks: ${result.exceptionOrNull()}")
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in playAll", e)
                }
            } else {
                Log.w(TAG, "MediaController is null, cannot play")
            }
        }
    }
    
    /**
     * Play specific track within recording
     * Loads full recording queue, then plays specified track at specified position
     */
    suspend fun playTrack(
        trackIndex: Int, 
        recordingId: String, 
        format: String, 
        showId: String,
        showDate: String,
        venue: String?,
        location: String?,
        position: Long = 0L,
        autoPlay: Boolean = true
    ) {
        Log.d(TAG, "playTrack: index=$trackIndex, recording=$recordingId ($format) at position $position")
        
        executeWhenConnected {
            val controller = mediaController
            
            if (controller != null) {
                try {
                    Log.d(TAG, "Loading tracks from ArchiveService for playTrack")
                    
                    // Get raw V2 Track models from ArchiveService
                    val result = archiveService.getRecordingTracks(recordingId)
                    if (result.isSuccess) {
                        val rawTracks = result.getOrNull() ?: emptyList()
                        Log.d(TAG, "Loaded ${rawTracks.size} raw tracks from ArchiveService")
                        
                        // Filter by format - explicit failure if not found
                        val filteredTracks = rawTracks.filter { track ->
                            track.format.equals(format, ignoreCase = true)
                        }
                        
                        if (filteredTracks.isEmpty()) {
                            // Explicit failure - throw exception with debugging info
                            val availableFormats = rawTracks.map { it.format }.distinct()
                            throw FormatNotAvailableException(
                                recordingId = recordingId,
                                requestedFormat = format,
                                availableFormats = availableFormats
                            )
                        }
                        
                        Log.d(TAG, "Found ${filteredTracks.size} tracks for format: $format")
                        
                        // Validate track index
                        if (trackIndex >= 0 && trackIndex < filteredTracks.size) {
                            // Get selected track for immediate duration calculation
                            val selectedTrack = filteredTracks[trackIndex]
                            
                            // Set position and duration immediately from known values (before MediaController is ready)
                            _currentPosition.value = position
                            _duration.value = parseDurationToMs(selectedTrack.duration)
                            
                            // Convert to MediaItems
                            val mediaItems = convertToMediaItems(recordingId, filteredTracks, showId, showDate, venue, location, format)
                            
                            // Set media items and seek to specific track at position
                            Log.d(TAG, "🕒🎵 [V2-URL] Setting ${mediaItems.size} media items for track $trackIndex at ${System.currentTimeMillis()}")
                            mediaItems.forEachIndexed { index, item ->
                                if (index == trackIndex) {
                                    Log.d(TAG, "🕒🎵 [V2-URL] Loading target track URL: ${item.localConfiguration?.uri} at ${System.currentTimeMillis()}")
                                }
                            }
                            
                            // Set LOADING state before ExoPlayer operations
                            _playbackState.value = PlaybackState.LOADING
                            Log.d(TAG, "🕒🎵 [V2-STATE] Manual LOADING state set before setMediaItems for track $trackIndex")
                            
                            controller.setMediaItems(mediaItems, trackIndex, position)
                            Log.d(TAG, "🕒🎵 [V2-URL] MediaController.setMediaItems(track=$trackIndex, pos=$position) completed at ${System.currentTimeMillis()}")
                            controller.prepare()
                            Log.d(TAG, "🕒🎵 [V2-URL] MediaController.prepare() for track $trackIndex completed at ${System.currentTimeMillis()}")
                            
                            // MediaController will provide accurate updates once ready via callbacks
                            
                            if (autoPlay) {
                                Log.d(TAG, "🕒🎵 [V2-URL] Calling controller.play() for track $trackIndex at ${System.currentTimeMillis()}")
                                controller.play()
                                Log.d(TAG, "🕒🎵 [V2-URL] controller.play() for track $trackIndex completed at ${System.currentTimeMillis()}")
                            } else {
                                Log.d(TAG, "🕒🎵 [V2-URL] Track $trackIndex loaded at position $position (paused) at ${System.currentTimeMillis()}")
                            }
                        } else {
                            Log.e(TAG, "Invalid track index: $trackIndex (available: 0-${filteredTracks.size - 1})")
                        }
                        
                    } else {
                        Log.e(TAG, "Failed to load tracks: ${result.exceptionOrNull()}")
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in playTrack", e)
                }
            }
        }
    }
    
    /**
     * Simple play/pause toggle
     */
    suspend fun togglePlayPause() {
        Log.d(TAG, "🕒🎵 [V2-MEDIA] MediaControllerRepository togglePlayPause called at ${System.currentTimeMillis()}")
        
        executeWhenConnected {
            val controller = mediaController
            if (controller != null) {
                val wasPlaying = controller.isPlaying
                Log.d(TAG, "🕒🎵 [V2-MEDIA] Current state: playing=$wasPlaying")
                
                if (wasPlaying) {
                    Log.d(TAG, "🕒🎵 [V2-MEDIA] Calling controller.pause() at ${System.currentTimeMillis()}")
                    controller.pause()
                } else {
                    Log.d(TAG, "🕒🎵 [V2-MEDIA] Calling controller.play() at ${System.currentTimeMillis()}")
                    controller.play()
                }
                
                Log.d(TAG, "togglePlayPause: MediaController command sent successfully")
            } else {
                Log.w(TAG, "togglePlayPause: MediaController is null!")
            }
        }
    }
    
    /**
     * Start playback (for auto-play when navigating tracks)
     */
    suspend fun play() {
        Log.d(TAG, "play() - ENTRY - ConnectionState: ${_connectionState.value}")
        
        executeWhenConnected {
            val controller = mediaController
            if (controller != null) {
                val wasPlaying = controller.isPlaying
                Log.d(TAG, "play: Current state: playing=$wasPlaying")
                Log.d(TAG, "play: Calling controller.play()")
                controller.play()
                Log.d(TAG, "play: MediaController.play() command sent successfully")
            } else {
                Log.w(TAG, "play: MediaController is null!")
            }
        }
    }
    
    /**
     * Pause playback (for explicit pause commands)
     */
    suspend fun pause() {
        Log.d(TAG, "pause() - ENTRY - ConnectionState: ${_connectionState.value}")
        
        executeWhenConnected {
            val controller = mediaController
            if (controller != null) {
                val wasPlaying = controller.isPlaying
                Log.d(TAG, "pause: Current state: playing=$wasPlaying")
                Log.d(TAG, "pause: Calling controller.pause()")
                controller.pause()
                Log.d(TAG, "pause: MediaController.pause() command sent successfully")
            } else {
                Log.w(TAG, "pause: MediaController is null!")
            }
        }
    }
    
    /**
     * Update combined PlaybackState based on ExoPlayer state and isPlaying
     * Called whenever either currentExoPlayerState or currentIsPlaying changes
     */
    private fun updatePlaybackState() {
        val newState = when (currentExoPlayerState) {
            Player.STATE_IDLE -> PlaybackState.IDLE
            Player.STATE_BUFFERING -> PlaybackState.BUFFERING
            Player.STATE_READY -> {
                if (currentIsPlaying) PlaybackState.PLAYING else PlaybackState.READY
            }
            Player.STATE_ENDED -> PlaybackState.ENDED
            else -> PlaybackState.IDLE
        }
        
        Log.d(TAG, "🕒🎵 [V2-STATE] PlaybackState updated: ExoPlayer=${getExoPlayerStateString(currentExoPlayerState)}, isPlaying=$currentIsPlaying → $newState")
        _playbackState.value = newState
    }
    
    /**
     * Helper to convert ExoPlayer state to readable string for logging
     */
    private fun getExoPlayerStateString(state: Int): String = when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "UNKNOWN($state)"
    }
    
    /**
     * Async connection to MediaSessionService
     */
    private fun connectToService() {
        if (_connectionState.value == ConnectionState.Connecting) {
            Log.d(TAG, "🕒🎵 [V2-MEDIA] Connection already in progress")
            return
        }
        
        _connectionState.value = ConnectionState.Connecting
        Log.d(TAG, "🕒🎵 [V2-MEDIA] Connecting to MediaSessionService at ${System.currentTimeMillis()}")
        
        try {
            val sessionToken = SessionToken(
                context,
                ComponentName(context, DeadlyMediaSessionService::class.java)
            )
            
            val future = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture = future
            
            // Add async listener - runs on main executor
            future.addListener({
                try {
                    val controller = future.get()
                    mediaController = controller
                    _connectionState.value = ConnectionState.Connected
                    Log.d(TAG, "🕒🎵 [V2-MEDIA] Connected to MediaSessionService successfully at ${System.currentTimeMillis()}")
                    
                    // Set up player state listeners
                    controller.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            if (isPlaying) {
                                Log.d(TAG, "🕒🎵 [V2-AUDIO] MediaController detected AUDIO STARTED at ${System.currentTimeMillis()}")
                            } else {
                                Log.d(TAG, "🕒🎵 [V2-AUDIO] MediaController detected audio stopped at ${System.currentTimeMillis()}")
                            }
                            _isPlaying.value = isPlaying
                            currentIsPlaying = isPlaying
                            updatePlaybackState()
                        }
                        
                        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                            _currentTrack.value = mediaMetadata
                        }
                        
                        override fun onPositionDiscontinuity(reason: Int) {
                            // Update position when there's a discontinuity (seeking, etc.)
                            _currentPosition.value = controller.currentPosition
                        }
                        
                        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                            _currentMediaItem.value = mediaItem
                            _currentShowId.value = extractShowIdFromMediaItem(mediaItem)
                            _currentRecordingId.value = extractRecordingIdFromMediaItem(mediaItem)
                            _currentTrackIndex.value = controller.currentMediaItemIndex
                            _mediaItemCount.value = controller.mediaItemCount
                            
                            // Update both position and duration immediately to prevent visual hiccup
                            if (mediaItem != null) {
                                _currentPosition.value = 0L  // New tracks always start at beginning
                                _duration.value = controller.duration.coerceAtLeast(0L)
                            }
                        }
                        
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            Log.d(TAG, "🕒🎵 [V2-EXOPLAYER] ExoPlayer state changed: ${getExoPlayerStateString(currentExoPlayerState)} → ${getExoPlayerStateString(playbackState)}")
                            
                            currentExoPlayerState = playbackState
                            updatePlaybackState()
                            
                            if (playbackState == Player.STATE_READY) {
                                _duration.value = controller.duration.coerceAtLeast(0L)
                                _currentPosition.value = controller.currentPosition
                            }
                        }
                    })
                    
                    Log.d(TAG, "MediaController connected successfully")
                    
                    // Start position updater for MiniPlayer
                    startPositionUpdater()
                    
                    // Execute any pending commands
                    repositoryScope.launch {
                        executePendingCommands()
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "🕒🎵 [V2-ERROR] Failed to connect MediaController at ${System.currentTimeMillis()}", e)
                    _connectionState.value = ConnectionState.Failed
                    mediaController = null
                }
            }, ContextCompat.getMainExecutor(context))
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up MediaController connection", e)
            _connectionState.value = ConnectionState.Failed
        }
    }
    
    /**
     * Execute command when connected, or queue if still connecting
     * CRITICAL: All MediaController operations MUST run on Main thread
     */
    private suspend fun executeWhenConnected(command: suspend () -> Unit) {
        when (_connectionState.value) {
            ConnectionState.Connected -> {
                // CRITICAL FIX: MediaController operations must run on Main thread
                withContext(Dispatchers.Main) {
                    try {
                        Log.d(TAG, "Executing MediaController command on Main thread")
                        command()
                        Log.d(TAG, "MediaController command completed successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "MediaController command failed", e)
                        throw e
                    }
                }
            }
            ConnectionState.Connecting -> {
                // Queue command for later execution
                synchronized(pendingCommands) {
                    pendingCommands.add(command)
                }
                Log.d(TAG, "Queued command - waiting for connection")
            }
            ConnectionState.Disconnected, ConnectionState.Failed -> {
                Log.d(TAG, "Attempting to reconnect...")
                connectToService()
                // Queue command for execution after connection
                synchronized(pendingCommands) {
                    pendingCommands.add(command)
                }
            }
        }
    }
    
    /**
     * Execute all pending commands after connection is established
     * CRITICAL: All MediaController operations MUST run on Main thread
     */
    private suspend fun executePendingCommands() {
        val commandsToExecute = synchronized(pendingCommands) {
            val commands = pendingCommands.toList()
            pendingCommands.clear()
            commands
        }
        
        Log.d(TAG, "Executing ${commandsToExecute.size} pending commands on Main thread")
        
        // CRITICAL FIX: Execute pending commands on Main thread
        withContext(Dispatchers.Main) {
            commandsToExecute.forEach { command ->
                try {
                    Log.d(TAG, "Executing pending MediaController command on Main thread")
                    command()
                    Log.d(TAG, "Pending MediaController command completed successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing pending MediaController command", e)
                }
            }
        }
    }
    
    /**
     * Convert V2 Track models to MediaItems for ExoPlayer
     */
    private fun convertToMediaItems(
        recordingId: String, 
        tracks: List<V2Track>,
        showId: String,
        showDate: String,
        venue: String?,
        location: String?,
        format: String
    ): List<androidx.media3.common.MediaItem> {
        return tracks.mapIndexed { index, track ->
            // Use track name/filename for URL construction if no direct URL available
            val uri = generateArchiveUrl(recordingId, track)
            Log.d(TAG, "Converting track ${index + 1}: ${track.title ?: track.name} -> $uri")
            
            androidx.media3.common.MediaItem.Builder()
                .setUri(uri)
                .setMediaId("${showId}|${recordingId}|${index}")
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(track.title ?: track.name)
                        .setArtist("Grateful Dead")
                        .setAlbumTitle(
                            // Format: "Apr 3, 1990 - The Omni" or just show date if no venue
                            if (!venue.isNullOrBlank()) {
                                "${formatShowDate(showDate)} - $venue"
                            } else {
                                formatShowDate(showDate)
                            }
                        )
                        .setTrackNumber(track.trackNumber)
                        .setExtras(Bundle().apply {
                            putString("showId", showId)
                            putString("recordingId", recordingId)
                            putString("venue", venue)
                            putString("showDate", showDate)
                            putString("location", location)
                            putString("filename", track.name)
                            putString("format", format)
                            putString("trackUrl", uri.toString())
                        })
                        .build()
                )
                .build()
        }
    }
    
    /**
     * Generate Archive.org URL from V2 Track model
     */
    private fun generateArchiveUrl(recordingId: String, track: V2Track): String {
        // V2 Track should have the filename/name that corresponds to actual Archive.org files
        return "https://archive.org/download/${recordingId}/${track.name}"
    }
    
    // /**
    //  * Extract recording identifier from track name pattern
    //  */
    // private fun extractRecordingIdFromTrack(track: V2Track): String {
    //     // Extract recording ID from track name pattern like "gd77-05-08d1t01.mp3"
    //     val name = track.name
    //     return when {
    //         name.contains("gd") && name.contains("d") -> {
    //             // Pattern: gd77-05-08d1t01.mp3 -> need the full recording identifier
    //             // This is a simplified approach - real implementation may need more logic
    //             "gd77-05-08.sbd.hicks.4982.sbeok.shnf" // Fallback for now
    //         }
    //         else -> "unknown-recording"
    //     }
    // }
    
    /**
     * Parse duration string to milliseconds
     */
    private fun parseDuration(duration: String?): Long? {
        return duration?.let { durationStr ->
            try {
                // Handle MM:SS format
                if (durationStr.contains(":")) {
                    val parts = durationStr.split(":")
                    if (parts.size == 2) {
                        val minutes = parts[0].toIntOrNull() ?: 0
                        val seconds = parts[1].toIntOrNull() ?: 0
                        return (minutes * 60 + seconds) * 1000L
                    }
                }
                // Handle seconds as string
                durationStr.toDoubleOrNull()?.let { (it * 1000).toLong() }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    private fun extractShowIdFromMediaItem(mediaItem: androidx.media3.common.MediaItem?): String? {
        return mediaItem?.mediaMetadata?.extras?.getString("showId")
    }
    
    private fun extractRecordingIdFromMediaItem(mediaItem: androidx.media3.common.MediaItem?): String? {
        return mediaItem?.mediaMetadata?.extras?.getString("recordingId")
    }
    
    /**
     * Start periodic position updates for MiniPlayer progress
     */
    /**
     * Format show date from YYYY-MM-DD to readable format
     * Copied from V1 CurrentTrackInfo pattern
     */
    private fun formatShowDate(dateString: String): String {
        return try {
            // Convert from YYYY-MM-DD to more readable format
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
     * Parse duration string (e.g., "4:32" or "1:23:45") to milliseconds
     */
    private fun parseDurationToMs(durationString: String?): Long {
        if (durationString.isNullOrBlank()) return 0L
        return try {
            val parts = durationString.split(":")
            when (parts.size) {
                2 -> { // mm:ss format
                    val minutes = parts[0].toLong()
                    val seconds = parts[1].toLong()
                    (minutes * 60 + seconds) * 1000
                }
                3 -> { // hh:mm:ss format  
                    val hours = parts[0].toLong()
                    val minutes = parts[1].toLong()
                    val seconds = parts[2].toLong()
                    (hours * 3600 + minutes * 60 + seconds) * 1000
                }
                else -> 0L
            }
        } catch (e: Exception) { 
            Log.w(TAG, "Failed to parse duration: $durationString", e)
            0L 
        }
    }
    
    private fun startPositionUpdater() {
        repositoryScope.launch {
            while (true) {
                val controller = mediaController
                if (controller != null) {
                    _currentPosition.value = controller.currentPosition
                }
                kotlinx.coroutines.delay(1000) // Update every second
            }
        }
    }
    
    suspend fun seekToNext() {
        Log.d(TAG, "seekToNext - calling seekToNextMediaItem")
        executeWhenConnected {
            mediaController?.seekToNextMediaItem()
        }
    }
    
    suspend fun seekToPrevious() {
        Log.d(TAG, "seekToPrevious - calling seekToPreviousMediaItem")
        executeWhenConnected {
            mediaController?.seekToPreviousMediaItem()
        }
    }
    
    suspend fun seekToPosition(positionMs: Long) {
        Log.d(TAG, "seekToPosition: ${positionMs}ms")
        executeWhenConnected {
            mediaController?.seekTo(positionMs)
        }
    }
    
    /**
     * Get current MediaItems from the queue for hydration
     * Must be called from the main thread (MediaController requirement)
     */
    suspend fun getCurrentMediaItems(): List<androidx.media3.common.MediaItem> {
        return withContext(Dispatchers.Main) {
            try {
                val controller = mediaController
                if (controller == null) {
                    Log.w(TAG, "MediaController not available for queue access")
                    return@withContext emptyList()
                }
                
                val timeline = controller.currentTimeline
                val mediaItems = mutableListOf<androidx.media3.common.MediaItem>()
                
                for (i in 0 until timeline.windowCount) {
                    val window = androidx.media3.common.Timeline.Window()
                    timeline.getWindow(i, window)
                    mediaItems.add(window.mediaItem)
                }
                
                Log.d(TAG, "Retrieved ${mediaItems.size} MediaItems from queue")
                mediaItems
                
            } catch (e: Exception) {
                Log.e(TAG, "Error getting current MediaItems", e)
                emptyList()
            }
        }
    }
    
    /**
     * Update the queue with hydrated MediaItems
     * Preserves current position and playback state
     * Must be called from the main thread (MediaController requirement)
     */
    suspend fun updateQueueWithHydratedItems(hydratedItems: List<androidx.media3.common.MediaItem>) {
        Log.d(TAG, "Updating queue with ${hydratedItems.size} hydrated MediaItems")
        
        withContext(Dispatchers.Main) {
            executeWhenConnected {
                val controller = mediaController
                if (controller != null && hydratedItems.isNotEmpty()) {
                    try {
                        val currentIndex = controller.currentMediaItemIndex
                        val currentPosition = controller.currentPosition
                        val wasPlaying = controller.isPlaying
                        
                        // Update the queue with hydrated items
                        controller.setMediaItems(hydratedItems, currentIndex, currentPosition)
                        controller.prepare()
                        
                        // Restore playback state
                        if (wasPlaying) {
                            controller.play()
                        }
                        
                        Log.d(TAG, "Successfully updated queue with hydrated metadata")
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating queue with hydrated items", e)
                    }
                } else {
                    Log.w(TAG, "Cannot update queue: controller=${controller != null}, items=${hydratedItems.size}")
                }
            }
        }
    }
    
    /**
     * Get debug information for troubleshooting
     */
    fun getDebugInfo(): String {
        val controller = mediaController
        return buildString {
            appendLine("=== MediaControllerRepository Debug Info ===")
            appendLine("ConnectionState: ${_connectionState.value}")
            appendLine("MediaController: ${if (controller != null) "Connected" else "Null"}")
            appendLine("PendingCommands: ${pendingCommands.size}")
            
            if (controller != null) {
                try {
                    appendLine("IsPlaying: ${controller.isPlaying}")
                    appendLine("PlaybackState: ${controller.playbackState}")
                    appendLine("CurrentPosition: ${controller.currentPosition}ms")
                    appendLine("Duration: ${controller.duration}ms")
                    appendLine("CurrentMediaItemIndex: ${controller.currentMediaItemIndex}")
                    appendLine("MediaItemCount: ${controller.mediaItemCount}")
                } catch (e: Exception) {
                    appendLine("Error accessing controller state: ${e.message}")
                }
            }
            
            appendLine("StateFlow Values:")
            appendLine("  isPlaying: ${_isPlaying.value}")
            appendLine("  currentPosition: ${_currentPosition.value}ms")
            appendLine("  duration: ${_duration.value}ms")
            appendLine("  currentShowId: ${_currentShowId.value}")
            appendLine("  currentRecordingId: ${_currentRecordingId.value}")
            appendLine("  currentTrackIndex: ${_currentTrackIndex.value}")
            appendLine("=== End Debug Info ===")
        }
    }
    
    /**
     * Release resources
     */
    fun release() {
        Log.d(TAG, "release() - Releasing MediaController resources")
        mediaController?.release()
        mediaController = null
        controllerFuture?.cancel(true)
        repositoryScope.launch { /* scope will be cancelled by job */ }
        _connectionState.value = ConnectionState.Disconnected
        Log.d(TAG, "release() - Resources released successfully")
    }
}