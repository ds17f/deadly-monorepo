package com.grateful.deadly.core.connect

import android.os.Build
import android.util.Log
import com.grateful.deadly.core.api.auth.AuthService
import com.grateful.deadly.core.database.AppPreferences
import com.grateful.deadly.core.model.ConnectDevice
import com.grateful.deadly.core.model.ConnectSessionTrack
import com.grateful.deadly.core.model.ConnectState
import com.grateful.deadly.core.media.repository.MediaControllerRepository
import com.grateful.deadly.core.network.monitor.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.drop

@Singleton
class ConnectServiceImpl @Inject constructor(
    private val appPreferences: AppPreferences,
    private val authService: AuthService,
    private val mediaControllerRepository: MediaControllerRepository,
    private val networkMonitor: NetworkMonitor,
    private val showRepository: com.grateful.deadly.core.domain.repository.ShowRepository,
) : ConnectService {

    companion object {
        private const val TAG = "ConnectService"
        private val RECONNECT_DELAYS_S = listOf(1L, 2L, 4L, 8L, 30L)
        private const val HEARTBEAT_INTERVAL_MS = 15_000L
        private const val CLOSE_CODE_UNAUTHORIZED = 4003
        private const val DEFAULT_FORMAT = "VBR MP3"
        private const val HARDWARE_VOLUME_DEBOUNCE_MS = 300L
        private const val VOLUME_ECHO_SUPPRESS_MS = 1_500L
        private const val TIME_SYNC_REFRESH_MS = 5L * 60L * 1000L
        private const val TIME_SYNC_SAMPLES = 3
        private const val TIME_SYNC_SAMPLE_SPACING_MS = 200L

        // How often the active device reports its position to the server.
        private const val POSITION_REPORT_INTERVAL_MS = 5_000L
        // On becoming active, only re-seek if the local position differs from the
        // interpolated server position by more than this (avoids a pointless seek).
        private const val ACTIVATE_POSITION_SYNC_THRESHOLD_MS = 1_000L
        // While active, only honor a remote seek if it moves the position by more
        // than this (filters our own ~5s position reports echoing back).
        private const val REMOTE_SEEK_THRESHOLD_MS = 2_000L
        // A pending transport command auto-clears after this long if the server
        // never confirms it, so the UI spinner can never get permanently stuck.
        private const val PENDING_COMMAND_TIMEOUT_MS = 6_000L
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // required for WebSocket keep-alive
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _devices = MutableStateFlow<List<ConnectDevice>>(emptyList())
    override val devices: StateFlow<List<ConnectDevice>> = _devices.asStateFlow()

    private val _connectState = MutableStateFlow<ConnectState?>(null)
    override val connectState: StateFlow<ConnectState?> = _connectState.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _pendingCommand = MutableStateFlow<String?>(null)
    override val pendingCommand: StateFlow<String?> = _pendingCommand.asStateFlow()

    private val _isActiveDevice = MutableStateFlow(false)
    override val isActiveDevice: StateFlow<Boolean> = _isActiveDevice.asStateFlow()

    // Last non-empty tracklist we observed, re-asserted to the server if it
    // forgets it (e.g. a server restart rehydrates the session position-only).
    private var cachedTracks: List<ConnectSessionTrack> = emptyList()
    private var cachedTracksRecordingId: String? = null
    // Guards the one-shot re-assert so position broadcasts arriving before our
    // load echoes back don't make us re-send it repeatedly.
    private var reassertingTracks = false
    // Last server epoch seen. A change means the server restarted (and rehydrated
    // the session), which is the only situation a still-playing device reclaims.
    @Volatile private var lastEpoch: Long? = null

    private val _pendingTransfer = MutableStateFlow<String?>(null)
    override val pendingTransfer: StateFlow<String?> = _pendingTransfer.asStateFlow()

    private val _activeDeviceVolume = MutableStateFlow(100)
    override val activeDeviceVolume: StateFlow<Int> = _activeDeviceVolume.asStateFlow()

    private val _showVolumeUI = MutableStateFlow(false)
    override val showVolumeUI: StateFlow<Boolean> = _showVolumeUI.asStateFlow()

    private val _serverTimeOffsetMs = MutableStateFlow(0L)
    override val serverTimeOffsetMs: StateFlow<Long> = _serverTimeOffsetMs.asStateFlow()

    @Volatile private var currentVersion = 0L
    // Best (lowest) RTT seen in the current time_sync batch. Replies with
    // higher RTT are dropped; we only update the offset when a sample beats
    // every prior sample of this batch.
    @Volatile private var timeSyncBestRttMs = Long.MAX_VALUE
    private var timeSyncRefreshJob: Job? = null

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var shouldConnect = false
    @Volatile private var reconnectAttempt = 0
    private var heartbeatJob: Job? = null
    private var positionReportJob: Job? = null
    private var reconnectJob: Job? = null
    @Volatile private var pendingCommandTimeoutJob: Job? = null

    // Tracks last local sendVolume to suppress our own echoes from volume_report,
    // which race with rapid hardware-key presses and cause the slider to jump.
    @Volatile private var lastVolumeSentAtMs: Long = 0L

    // Debounces hardware-volume-key sends so rapid presses coalesce into one
    // outbound command (and one echo), instead of flooding the websocket.
    @Volatile private var pendingHardwareVolume: Int? = null
    private var hardwareVolumeSendJob: Job? = null

    // Strictly-ordered queue of state reactions. Each reaction runs to
    // completion (all player commands awaited) before the next is dequeued, so
    // commands issued across rapid-fire state snapshots can never reorder and a
    // reaction always observes the player state left by the previous one. This
    // is what a limitedParallelism(1) dispatcher cannot guarantee — coroutines
    // there interleave at suspension points; a single consumer does not.
    private val reactionQueue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (reaction in reactionQueue) {
                try {
                    reaction()
                } catch (e: Exception) {
                    Log.w(TAG, "reaction failed: ${e.message}")
                }
            }
        }

        // When the local player auto-advances to the next track, notify the Connect
        // server so all other devices stay in sync. Only fires when we are the active
        // device (seekToMediaItemIndex from reactToState uses REASON_SEEK, not REASON_AUTO).
        scope.launch {
            mediaControllerRepository.trackAutoAdvanced.collect {
                if (_isActiveDevice.value) {
                    Log.d(TAG, "trackAutoAdvanced: active device, sending next")
                    sendNext()
                }
            }
        }

        // Forward LOCAL transport changes that bypass the in-app buttons —
        // Bluetooth/headset media keys, the lock-screen/notification controls,
        // Android Auto, and audio-focus pauses all drive ExoPlayer directly
        // through the MediaSession and never call sendPause()/sendPlay(). Without
        // this, the server keeps thinking the active device is playing, its next
        // position broadcast arrives as playing=true, and reactToState resumes the
        // audio a few seconds after the user paused from their headphones.
        //
        // We watch playWhenReady (play INTENT), not isPlaying: isPlaying drops to
        // false on every buffering stall, which would otherwise fire a spurious
        // pause mid-playback (and reactToState would turn that into a real pause).
        // playWhenReady stays true through buffering and flips only on an actual
        // pause/resume — exactly the server's `playing` semantics. We push only a
        // DIVERGENCE from the server's view, so server-driven changes don't echo:
        // reactToState updates _connectState before it touches the player, so by the
        // time it flips playWhenReady the local value already equals
        // connectState.playing and nothing is sent. A duplicate send from the in-app
        // toggle is harmless — handlePlay/handlePause are no-ops when already in the
        // target state.
        scope.launch {
            mediaControllerRepository.playWhenReady.collect { localIntendsPlay ->
                if (!_isActiveDevice.value) return@collect
                val serverPlaying = _connectState.value?.playing ?: return@collect
                if (localIntendsPlay == serverPlaying) return@collect
                if (localIntendsPlay) {
                    Log.d(TAG, "playWhenReady reconcile: local play diverged from server (paused) — sendPlay")
                    sendPlay()
                } else {
                    Log.d(TAG, "playWhenReady reconcile: local pause diverged from server (playing) — sendPause")
                    sendPause()
                }
            }
        }

        // When network is restored while we have a pending reconnect, cancel the
        // backoff and reconnect immediately (mirrors iOS handleNetworkRestored).
        scope.launch {
            networkMonitor.isOnline
                .drop(1) // skip initial value
                .filter { it }
                .collect { handleNetworkRestored() }
        }
    }

    override fun startIfAuthenticated() {
        val token = authService.getAuthToken()
        Log.d(TAG, "startIfAuthenticated: token=${if (token != null) "present" else "null"}, shouldConnect=$shouldConnect")
        if (token == null) return
        if (shouldConnect) {
            // Already meant to be connected. Since we no longer stop() on
            // background, a socket that died while backgrounded (network drop)
            // leaves shouldConnect=true; reconnect now that we're foreground
            // instead of no-opping. handleNetworkRestored is a no-op if still
            // connected.
            if (!_isConnected.value) handleNetworkRestored()
            return
        }
        shouldConnect = true
        reconnectAttempt = 0
        connect()
    }

    override fun stop() {
        Log.d(TAG, "stop() called")
        shouldConnect = false
        reconnectJob?.cancel()
        reconnectJob = null
        stopHeartbeat()
        stopTimeSyncRefresh()
        val ws = webSocket
        webSocket = null
        ws?.close(1000, null)
        _isConnected.value = false
        _devices.value = emptyList()
        _connectState.value = null
        setPendingCommand(null)
        _isActiveDevice.value = false
        _pendingTransfer.value = null
        _activeDeviceVolume.value = 100
        _serverTimeOffsetMs.value = 0L
        timeSyncBestRttMs = Long.MAX_VALUE
        currentVersion = 0L
    }

    override fun handleNetworkRestored() {
        if (!shouldConnect || _isConnected.value) return
        Log.d(TAG, "handleNetworkRestored: cancelling backoff, reconnecting immediately")
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0
        connect()
    }

    private fun connect() {
        val token = authService.getAuthToken() ?: return
        if (!shouldConnect) return

        val baseUrl = appPreferences.apiBaseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
        val url = "$baseUrl/ws/connect"

        Log.d(TAG, "Connecting to $url")
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()
        webSocket = okHttpClient.newWebSocket(request, listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!shouldConnect) {
                webSocket.close(1000, null)
                return
            }
            Log.d(TAG, "Connected")
            _isConnected.value = true
            reconnectAttempt = 0
            // Reset the version watermark on every (re)connect. The monotonic
            // guard only dedupes within one connection; across a reconnect the
            // server may have restarted and reset its counter, so the first
            // snapshot after connecting is authoritative regardless of version.
            // (-1 so the server's lowest possible version, 0, is still accepted.)
            currentVersion = -1L
            sendRegister(webSocket)
            startHeartbeat(webSocket)
            startTimeSyncRefresh(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "onMessage: ${text.take(200)}")
            handleMessage(text)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Closed: code=$code reason=$reason")
            handleDisconnect(code)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "Failure: ${t.message}")
            handleDisconnect(null)
        }
    }

    private fun sendRegister(ws: WebSocket) {
        val msg = buildJsonObject {
            put("type", "register")
            put("deviceId", appPreferences.installId)
            put("deviceType", "android")
            put("deviceName", Build.MODEL)
        }
        ws.send(msg.toString())
    }

    private fun startHeartbeat(ws: WebSocket) {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (isActive) ws.send("""{"type":"heartbeat"}""")
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun startTimeSyncRefresh(ws: WebSocket) {
        stopTimeSyncRefresh()
        runTimeSync(ws)
        timeSyncRefreshJob = scope.launch {
            while (isActive) {
                delay(TIME_SYNC_REFRESH_MS)
                if (isActive) runTimeSync(ws)
            }
        }
    }

    private fun stopTimeSyncRefresh() {
        timeSyncRefreshJob?.cancel()
        timeSyncRefreshJob = null
    }

    private fun runTimeSync(ws: WebSocket) {
        // New batch: replies are scored against this batch's running best.
        timeSyncBestRttMs = Long.MAX_VALUE
        scope.launch {
            for (i in 0 until TIME_SYNC_SAMPLES) {
                if (!isActive) return@launch
                val sock = webSocket
                if (sock !== ws) return@launch  // socket replaced; bail
                sock.send(buildJsonObject {
                    put("type", "time_sync")
                    put("clientTs", System.currentTimeMillis())
                }.toString())
                if (i < TIME_SYNC_SAMPLES - 1) delay(TIME_SYNC_SAMPLE_SPACING_MS)
            }
        }
    }

    private fun startPositionReporting() {
        stopPositionReporting()
        positionReportJob = scope.launch {
            while (isActive) {
                delay(POSITION_REPORT_INTERVAL_MS)
                if (isActive) {
                    val positionMs = mediaControllerRepository.currentPosition.value.toInt()
                    val localIndex = mediaControllerRepository.currentTrackIndex.value
                    val serverIndex = _connectState.value?.trackIndex
                    if (serverIndex != null && localIndex != serverIndex) {
                        // The active device's track changed without going through
                        // Connect — a lock-screen / headset / Bluetooth skip drives
                        // ExoPlayer directly (REASON_SEEK), so no sendNext fires.
                        // Forward it as an absolute seek so the index resyncs; a bare
                        // position report would land the new position on the stale
                        // server index (track looks right elsewhere, progress bar wrong).
                        // Sent via sendCommand (not sendSeek) so this background sync
                        // doesn't arm the transport-command spinner.
                        val durationMs = mediaControllerRepository.duration.value.toInt()
                        Log.d(TAG, "positionReport: local track $localIndex != server $serverIndex — seek sync (pos=$positionMs)")
                        sendCommand("seek", mapOf(
                            "trackIndex" to localIndex,
                            "positionMs" to positionMs,
                            "durationMs" to durationMs,
                        ))
                    } else {
                        sendPosition(positionMs)
                    }
                }
            }
        }
    }

    private fun stopPositionReporting() {
        positionReportJob?.cancel()
        positionReportJob = null
    }

    private fun handleMessage(text: String) {
        try {
            val obj = json.parseToJsonElement(text).jsonObject
            when (obj["type"]?.jsonPrimitive?.content) {
                "state" -> {
                    val stateEl = obj["state"] ?: return
                    val newState = json.decodeFromJsonElement(ConnectState.serializer(), stateEl)

                    // Version check: ignore stale broadcasts
                    if (newState.version <= currentVersion) {
                        Log.d(TAG, "Ignoring stale state v${newState.version} (current=$currentVersion)")
                        return
                    }
                    currentVersion = newState.version
                    val myId = appPreferences.installId
                    val isActive = newState.activeDeviceId == myId
                    val localPlaying = mediaControllerRepository.isPlaying.value
                    val localRec = mediaControllerRepository.currentRecordingId.value
                    Log.d(TAG, "State v${newState.version}: show=${newState.showId} rec=${newState.recordingId} " +
                        "track=${newState.trackIndex} playing=${newState.playing} " +
                        "activeDevice=${newState.activeDeviceId} isMe=$isActive " +
                        "localPlaying=$localPlaying localRec=$localRec")

                    val old = _connectState.value
                    _connectState.value = newState
                    _isActiveDevice.value = isActive

                    // Enqueue the reaction so it runs serially behind any prior
                    // snapshot's reaction. UI state above is updated immediately;
                    // player commands are ordered by the single consumer.
                    reactionQueue.trySend { reactToState(old, newState) }
                }
                "devices" -> {
                    val devicesEl = obj["devices"]?.jsonArray ?: return
                    _devices.value = devicesEl.map {
                        json.decodeFromJsonElement(ConnectDevice.serializer(), it)
                    }
                    Log.d(TAG, "Devices (${_devices.value.size}): ${_devices.value.joinToString { "${it.deviceName}[${it.deviceType}]" }}")
                }
                "volume" -> {
                    val volume = obj["volume"]?.jsonPrimitive?.content?.toIntOrNull() ?: return
                    Log.d(TAG, "handleMessage: volume command $volume")
                    mediaControllerRepository.setVolume(volume)
                    _activeDeviceVolume.value = volume
                    sendVolumeReport(volume)
                }
                "volume_report" -> {
                    val volume = obj["volume"]?.jsonPrimitive?.content?.toIntOrNull() ?: return
                    val sinceLocalSend = System.currentTimeMillis() - lastVolumeSentAtMs
                    if (sinceLocalSend < VOLUME_ECHO_SUPPRESS_MS) {
                        // Our own send is still being echoed back. Trust the optimistic
                        // value on this device rather than letting a stale echo clobber
                        // a newer press.
                        Log.d(TAG, "handleMessage: suppressing volume_report $volume " +
                            "(local send ${sinceLocalSend}ms ago, keeping ${_activeDeviceVolume.value})")
                        return
                    }
                    Log.d(TAG, "handleMessage: volume_report $volume")
                    _activeDeviceVolume.value = volume
                }
                "time_sync" -> {
                    val clientTs = obj["clientTs"]?.jsonPrimitive?.content?.toLongOrNull() ?: return
                    val serverTs = obj["serverTs"]?.jsonPrimitive?.content?.toLongOrNull() ?: return
                    val now = System.currentTimeMillis()
                    val rtt = now - clientTs
                    // NTP-style: assume symmetric one-way delay. serverTs is stamped at
                    // server send time, so it ≈ server clock at (clientTs + rtt/2).
                    val offset = serverTs - (clientTs + rtt / 2)
                    if (rtt < timeSyncBestRttMs) {
                        timeSyncBestRttMs = rtt
                        _serverTimeOffsetMs.value = offset
                        Log.d(TAG, "time_sync: rtt=${rtt}ms offset=${offset}ms (kept)")
                    } else {
                        Log.d(TAG, "time_sync: rtt=${rtt}ms offset=${offset}ms (dropped, best=${timeSyncBestRttMs}ms)")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse message: ${e.message}")
        }
    }

    private suspend fun reactToState(old: ConnectState?, new: ConnectState) {
        // Remember the tracklist whenever the server has one, so we can re-assert
        // it if the server later forgets it (see the active-device block below).
        if (new.tracks.isNotEmpty()) {
            cachedTracks = new.tracks
            cachedTracksRecordingId = new.recordingId
            reassertingTracks = false
        }

        // Clear pending command if the server confirmed the expected transition
        val cmd = _pendingCommand.value
        if (cmd != null) {
            if (cmd == "play" && new.playing) {
                Log.d(TAG, "reactToState: pending 'play' confirmed, clearing")
                setPendingCommand(null)
            } else if (cmd == "pause" && !new.playing) {
                Log.d(TAG, "reactToState: pending 'pause' confirmed, clearing")
                setPendingCommand(null)
            } else if ((cmd == "next" || cmd == "prev") && new.trackIndex != (old?.trackIndex ?: -1)) {
                Log.d(TAG, "reactToState: pending '$cmd' confirmed (track ${old?.trackIndex ?: -1} -> ${new.trackIndex}), clearing")
                setPendingCommand(null)
            } else if (cmd == "seek" && new.positionMs != (old?.positionMs ?: -1)) {
                Log.d(TAG, "reactToState: pending 'seek' confirmed, clearing")
                setPendingCommand(null)
            }
        }

        // Clear pending transfer when a device becomes active (transfer resolved)
        if (_pendingTransfer.value != null && new.activeDeviceId != null) {
            Log.d(TAG, "reactToState: transfer resolved, clearing pendingTransfer")
            _pendingTransfer.value = null
        }

        val myId = appPreferences.installId
        val wasActive = old?.activeDeviceId == myId
        val nowActive = new.activeDeviceId == myId
        val isActive = nowActive
        val localRecordingId = mediaControllerRepository.currentRecordingId.value
        val locallyPlaying = mediaControllerRepository.isPlaying.value

        // Did the server restart? An epoch change is the explicit, authoritative
        // signal (vs. inferring it from null-active + empty-tracks coincidences).
        val serverRestarted = lastEpoch != null && new.epoch != lastEpoch
        lastEpoch = new.epoch

        // Reclaim only when the server restarted and rehydrated the session (so it
        // has no active device) while we're still playing this recording — take
        // ownership back without a gap. reassertingTracks keeps us in this mode
        // while our reclaim load is in flight (epoch is unchanged on later states).
        // A deliberate transition (transfer park, stop) keeps the same epoch, so
        // it is never mistaken for a restart.
        val reclaimAfterRestart = new.activeDeviceId == null &&
            locallyPlaying && localRecordingId == new.recordingId &&
            (serverRestarted || reassertingTracks)

        // If this device was active but is no longer — a different device took
        // over, or we were parked during a transfer — pause and report final
        // position. Skipped on a reclaim, where we keep playing and re-assert.
        if (wasActive && !nowActive && !reclaimAfterRestart) {
            val positionMs = mediaControllerRepository.currentPosition.value.toInt()
            Log.d(TAG, "reactToState: parked/transferred away — pausing and reporting position ${positionMs}ms")
            mediaControllerRepository.pause()
            sendPosition(positionMs)
        }

        // If the recording changed (or first load), load it locally.
        // Active device: autoPlay based on server state.
        // Non-active device: load paused so the player shows the shared show.
        if (new.recordingId != null && new.recordingId != localRecordingId) {
            val autoPlay = isActive && new.playing
            // ConnectState is transport-only (no ticket image). Resolve the show's
            // cover from the local catalog so a follower's now-playing/mini player
            // shows the ticket art instead of the Archive-thumbnail fallback.
            val coverImageUrl = new.showId
                ?.let { runCatching { showRepository.getShowById(it)?.coverImageUrl }.getOrNull() }
            Log.d(TAG, "reactToState: NEW RECORDING — server=${new.recordingId} local=$localRecordingId " +
                "isActive=$isActive autoPlay=$autoPlay trackIndex=${new.trackIndex} positionMs=${new.positionMs}")
            mediaControllerRepository.playTrack(
                trackIndex = new.trackIndex,
                recordingId = new.recordingId!!,
                format = DEFAULT_FORMAT,
                showId = new.showId ?: "",
                showDate = new.date ?: "",
                venue = new.venue,
                location = new.location,
                coverImageUrl = coverImageUrl,
                position = new.positionMs.toLong(),
                autoPlay = autoPlay,
            )
            stopPositionReporting()
            return
        }

        // Not active device — sync track index and stop local playback.
        // Compare against the LOCAL track index (not old server state) so the
        // very first snapshot (old == null) still aligns — e.g. when the app
        // just restored the same recording at a different track on startup.
        // Skipped while reclaiming: we keep playing and re-assert below instead.
        if (!isActive && !reclaimAfterRestart) {
            reassertingTracks = false
            val localTrackIndex = mediaControllerRepository.currentTrackIndex.value
            if (new.trackIndex != localTrackIndex) {
                Log.d(TAG, "reactToState: NOT ACTIVE — syncing track $localTrackIndex -> ${new.trackIndex}")
                mediaControllerRepository.seekToMediaItemIndex(new.trackIndex, new.positionMs.toLong())
                mediaControllerRepository.pause()
            } else if (locallyPlaying) {
                Log.d(TAG, "reactToState: NOT ACTIVE — pausing local playback")
                mediaControllerRepository.pause()
            } else {
                Log.d(TAG, "reactToState: NOT ACTIVE — no action needed")
            }
            stopPositionReporting()
            return
        }

        // Server forgot our tracklist (it restarted and rehydrated the session
        // from the saved position only). We still hold it — re-assert the load so
        // viewers' display and the server's next/prev get the tracks back.
        // handleLoad keeps us active and honors the index/position we pass.
        if (new.tracks.isEmpty() && cachedTracks.isNotEmpty() &&
            cachedTracksRecordingId == new.recordingId && !reassertingTracks) {
            reassertingTracks = true
            val idx = mediaControllerRepository.currentTrackIndex.value
            Log.d(TAG, "reactToState: server tracks empty — re-asserting load (${cachedTracks.size} tracks, idx=$idx)")
            sendLoad(
                showId = new.showId ?: mediaControllerRepository.currentShowId.value ?: "",
                recordingId = new.recordingId ?: cachedTracksRecordingId ?: "",
                tracks = cachedTracks,
                trackIndex = idx,
                positionMs = mediaControllerRepository.currentPosition.value.toInt(),
                durationMs = mediaControllerRepository.duration.value.toInt(),
                date = new.date,
                venue = new.venue,
                location = new.location,
                autoplay = mediaControllerRepository.isPlaying.value,
            )
        }

        // On a reclaim we are the source of truth: don't sync transport down to
        // the stale playing:false / saved position. The server echoes our
        // re-asserted load back on the next version (activeDeviceId == us), and
        // normal active-device sync resumes from there.
        if (reclaimAfterRestart) return

        // When this device just became active (e.g. transfer in), sync local player
        // to server state — the local player may be at a completely different track/position.
        //
        // BUT skip the transport sync when a pendingAdvance note is present: here we
        // "became active" only because we announced our OWN end-of-show (the server
        // claims the announcer active, ADR-0011). We are parked at the end of the
        // show waiting for the note-collector to advance — we know our position
        // better than the server, whose positionMs is the stale pre-end value. Without
        // this guard we seek back to that stale position and resume, replaying the
        // tail, which re-fires onShowCompleted and re-announces (resetting the
        // countdown). The note-collector owns the transition from here.
        val justBecameActive = !wasActive && nowActive
        if (justBecameActive && new.pendingAdvance == null) {
            val currentVolume = mediaControllerRepository.getVolume()
            _activeDeviceVolume.value = currentVolume
            sendVolumeReport(currentVolume)
            val targetPositionMs = interpolatedPositionMs(new)
            val localTrackIndex = mediaControllerRepository.currentTrackIndex.value
            if (localTrackIndex != new.trackIndex) {
                Log.d(TAG, "reactToState: became active, syncing track $localTrackIndex -> ${new.trackIndex} pos=${targetPositionMs}ms (interpolated from ${new.positionMs}ms)")
                mediaControllerRepository.seekToMediaItemIndex(new.trackIndex, targetPositionMs.toLong())
            } else {
                val localPositionMs = mediaControllerRepository.currentPosition.value
                if (abs(localPositionMs - targetPositionMs.toLong()) > ACTIVATE_POSITION_SYNC_THRESHOLD_MS) {
                    Log.d(TAG, "reactToState: became active, syncing position to ${targetPositionMs}ms (interpolated from ${new.positionMs}ms)")
                    mediaControllerRepository.seekToPosition(targetPositionMs.toLong())
                }
            }
        }

        // React to track changes from remote controllers (while already active)
        if (!justBecameActive && old != null && new.trackIndex != old.trackIndex) {
            Log.d(TAG, "reactToState: track changed ${old.trackIndex} -> ${new.trackIndex}, skipping to index")
            mediaControllerRepository.seekToMediaItemIndex(new.trackIndex, 0L)
        }

        // React to seek from remote controllers (while already active).
        // Compare against local position (not old server state) so our own position
        // reports echoing back don't cause unnecessary seeks.
        if (!justBecameActive && old != null && new.trackIndex == old.trackIndex && new.positionMs != old.positionMs) {
            val localPositionMs = mediaControllerRepository.currentPosition.value
            val delta = abs(new.positionMs.toLong() - localPositionMs)
            if (delta > REMOTE_SEEK_THRESHOLD_MS) {
                Log.d(TAG, "reactToState: seek from remote, jumping to ${new.positionMs}ms (delta=$delta)")
                mediaControllerRepository.seekToPosition(new.positionMs.toLong())
            }
        }

        // Active device, same recording — handle play/pause
        if (new.playing && !locallyPlaying) {
            Log.d(TAG, "reactToState: ACTIVE + SAME REC — server=play local=paused -> play()")
            mediaControllerRepository.play()
        } else if (!new.playing && locallyPlaying) {
            Log.d(TAG, "reactToState: ACTIVE + SAME REC — server=pause local=playing -> pause()")
            mediaControllerRepository.pause()
        } else {
            Log.d(TAG, "reactToState: ACTIVE + SAME REC — no change (server.playing=${new.playing} local=$locallyPlaying)")
        }

        // Manage periodic position reporting — run only when active + playing
        if (nowActive && new.playing) {
            if (positionReportJob == null) {
                startPositionReporting()
            }
        } else {
            stopPositionReporting()
        }
    }

    // -- Commands --

    /**
     * Set (or clear) the in-flight transport command. When set, arms a timeout
     * that clears it if the server never confirms the expected transition, so
     * the UI spinner can't get stuck on a command that produces no observable
     * state change (e.g. a seek to the current position) or a dropped socket.
     */
    private fun setPendingCommand(cmd: String?) {
        _pendingCommand.value = cmd
        pendingCommandTimeoutJob?.cancel()
        pendingCommandTimeoutJob = null
        if (cmd != null) {
            pendingCommandTimeoutJob = scope.launch {
                delay(PENDING_COMMAND_TIMEOUT_MS)
                if (_pendingCommand.value == cmd) {
                    Log.d(TAG, "pendingCommand '$cmd' not confirmed in ${PENDING_COMMAND_TIMEOUT_MS}ms, clearing")
                    _pendingCommand.value = null
                }
            }
        }
    }

    override fun sendStop() {
        Log.d(TAG, "sendStop")
        sendCommand("stop")
    }

    override fun sendAnnounceNext(showId: String, deadline: Double) {
        Log.d(TAG, "sendAnnounceNext: $showId @ $deadline")
        sendCommand("announce_next", mapOf("showId" to showId, "deadline" to deadline))
    }

    override fun sendCancelAdvance() {
        Log.d(TAG, "sendCancelAdvance")
        sendCommand("cancel_advance")
    }

    override fun sendAdvanceNow() {
        Log.d(TAG, "sendAdvanceNow")
        sendCommand("advance_now")
    }

    override fun sendLoad(
        showId: String,
        recordingId: String,
        tracks: List<ConnectSessionTrack>,
        trackIndex: Int,
        positionMs: Int,
        durationMs: Int,
        date: String?,
        venue: String?,
        location: String?,
        autoplay: Boolean,
    ) {
        Log.d(TAG, "sendLoad: show=$showId rec=$recordingId track=$trackIndex " +
            "pos=$positionMs dur=$durationMs autoplay=$autoplay tracks=${tracks.size}")
        val extra = mutableMapOf<String, Any>(
            "showId" to showId,
            "recordingId" to recordingId,
            "tracks" to tracks.map { mapOf("title" to it.title, "durationMs" to it.durationMs) },
            "trackIndex" to trackIndex,
            "positionMs" to positionMs,
            "durationMs" to durationMs,
            "autoplay" to autoplay,
        )
        date?.let { extra["date"] = it }
        venue?.let { extra["venue"] = it }
        location?.let { extra["location"] = it }
        sendCommand("load", extra)
    }

    override fun sendPlay() {
        Log.d(TAG, "sendPlay (pending=${_pendingCommand.value} -> play)")
        setPendingCommand("play")
        sendCommand("play")
    }

    override fun sendPause() {
        Log.d(TAG, "sendPause (pending=${_pendingCommand.value} -> pause)")
        setPendingCommand("pause")
        sendCommand("pause")
    }

    override fun sendTransfer(targetDeviceId: String) {
        if (_connectState.value?.showId == null) return
        Log.d(TAG, "sendTransfer: target=$targetDeviceId")
        _pendingTransfer.value = targetDeviceId
        sendCommand("transfer", mapOf("targetDeviceId" to targetDeviceId))
    }

    override fun sendPosition(positionMs: Int) {
        Log.d(TAG, "sendPosition: pos=$positionMs")
        sendCommand("position", mapOf("positionMs" to positionMs))
    }

    override fun sendSeek(trackIndex: Int, positionMs: Int, durationMs: Int) {
        Log.d(TAG, "sendSeek: track=$trackIndex pos=$positionMs dur=$durationMs (pending=${_pendingCommand.value} -> seek)")
        setPendingCommand("seek")
        sendCommand("seek", mapOf(
            "trackIndex" to trackIndex,
            "positionMs" to positionMs,
            "durationMs" to durationMs,
        ))
    }

    override fun sendNext() {
        Log.d(TAG, "sendNext (pending=${_pendingCommand.value} -> next)")
        setPendingCommand("next")
        sendCommand("next")
    }

    override fun sendPrev() {
        Log.d(TAG, "sendPrev (pending=${_pendingCommand.value} -> prev)")
        setPendingCommand("prev")
        sendCommand("prev")
    }

    override fun sendVolume(volume: Int) {
        Log.d(TAG, "sendVolume: $volume")
        lastVolumeSentAtMs = System.currentTimeMillis()
        sendCommand("volume", mapOf("volume" to volume))
    }

    override fun sendVolumeReport(volume: Int) {
        Log.d(TAG, "sendVolumeReport: $volume")
        sendCommand("volume_report", mapOf("volume" to volume))
    }

    override fun handleHardwareVolumeKey(delta: Int): Boolean {
        val activeId = _connectState.value?.activeDeviceId ?: return false
        val myId = appPreferences.installId
        // Only intercept when playback is on a *remote* device. If we're the
        // active device, hardware keys should control the phone's own output.
        if (activeId == myId) return false

        val current = _activeDeviceVolume.value
        val next = (current + delta).coerceIn(0, 100)
        Log.d(TAG, "handleHardwareVolumeKey: delta=$delta $current -> $next (remote=$activeId)")
        if (next != current) {
            // Update the UI immediately so the slider feels responsive, but
            // debounce the outbound command so a burst of key presses coalesces
            // into a single send (and a single echo). Without this the slider
            // visibly stutters as stale echoes of earlier sends land.
            _activeDeviceVolume.value = next
            pendingHardwareVolume = next
            hardwareVolumeSendJob?.cancel()
            hardwareVolumeSendJob = scope.launch {
                delay(HARDWARE_VOLUME_DEBOUNCE_MS)
                val v = pendingHardwareVolume ?: return@launch
                pendingHardwareVolume = null
                sendVolume(v)
            }
        }
        _showVolumeUI.value = true
        return true
    }

    override fun triggerShowVolumeUI() {
        if (_connectState.value?.activeDeviceId == null) return
        Log.d(TAG, "triggerShowVolumeUI")
        _showVolumeUI.value = true
    }

    override fun consumeShowVolumeUI() {
        _showVolumeUI.value = false
    }

    private fun sendCommand(action: String, extra: Map<String, Any> = emptyMap()) {
        val ws = webSocket
        if (ws == null) {
            Log.w(TAG, "sendCommand: $action DROPPED — webSocket is null")
            return
        }
        val obj = buildJsonObject {
            put("type", "command")
            put("action", action)
            for ((key, value) in extra) put(key, value.toJsonElement())
        }
        ws.send(obj.toString())
    }

    /** Convert the dynamically-typed command payload values into JSON. */
    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is JsonElement -> this
        is String -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Map<*, *> -> buildJsonObject {
            forEach { (k, v) -> put(k.toString(), v.toJsonElement()) }
        }
        is List<*> -> buildJsonArray {
            forEach { add(it.toJsonElement()) }
        }
        else -> JsonPrimitive(toString())
    }

    private fun interpolatedPositionMs(state: ConnectState): Int {
        if (!state.playing) return state.positionMs
        // positionTs is server wall-clock; translate our local clock into server
        // space via serverTimeOffsetMs before subtracting. Without this, devices
        // with clock skew mis-interpolate position and misseek on transfer.
        val serverNow = System.currentTimeMillis() + _serverTimeOffsetMs.value
        val elapsedMs = serverNow - state.positionTs.toLong()
        return (state.positionMs + elapsedMs).toInt().coerceIn(0, state.durationMs)
    }

    private fun handleDisconnect(closeCode: Int?) {
        stopHeartbeat()
        stopTimeSyncRefresh()
        stopPositionReporting()
        _isConnected.value = false
        _serverTimeOffsetMs.value = 0L
        timeSyncBestRttMs = Long.MAX_VALUE
        webSocket = null
        // Don't strand a spinner while the socket is down — any in-flight
        // command/transfer can't be confirmed now and will be re-derived from
        // the fresh state snapshot after reconnect.
        setPendingCommand(null)
        _pendingTransfer.value = null

        if (!shouldConnect || closeCode == CLOSE_CODE_UNAUTHORIZED) return

        val delaySecs = RECONNECT_DELAYS_S[minOf(reconnectAttempt, RECONNECT_DELAYS_S.size - 1)]
        reconnectAttempt++
        Log.d(TAG, "Reconnecting in ${delaySecs}s (attempt $reconnectAttempt)")
        reconnectJob = scope.launch {
            delay(delaySecs * 1000L)
            if (shouldConnect) connect()
        }
    }
}
