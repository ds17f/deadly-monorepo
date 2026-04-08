package com.grateful.deadly.core.connect

import android.os.Build
import android.util.Log
import com.grateful.deadly.core.api.auth.AuthService
import com.grateful.deadly.core.database.AppPreferences
import com.grateful.deadly.core.model.ConnectDevice
import com.grateful.deadly.core.model.ConnectSessionTrack
import com.grateful.deadly.core.model.ConnectState
import com.grateful.deadly.core.media.repository.MediaControllerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ConnectServiceImpl @Inject constructor(
    private val appPreferences: AppPreferences,
    private val authService: AuthService,
    private val mediaControllerRepository: MediaControllerRepository,
) : ConnectService {

    companion object {
        private const val TAG = "ConnectService"
        private val RECONNECT_DELAYS_S = listOf(1L, 2L, 4L, 8L, 30L)
        private const val HEARTBEAT_INTERVAL_MS = 15_000L
        private const val CLOSE_CODE_UNAUTHORIZED = 4003
        private const val DEFAULT_FORMAT = "VBR MP3"
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

    @Volatile private var currentVersion = 0

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var shouldConnect = false
    @Volatile private var reconnectAttempt = 0
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    override fun startIfAuthenticated() {
        val token = authService.getAuthToken()
        Log.d(TAG, "startIfAuthenticated: token=${if (token != null) "present" else "null"}, shouldConnect=$shouldConnect")
        if (token == null) return
        if (shouldConnect) return
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
        val ws = webSocket
        webSocket = null
        ws?.close(1000, null)
        _isConnected.value = false
        _devices.value = emptyList()
        _connectState.value = null
        _pendingCommand.value = null
        _isActiveDevice.value = false
        currentVersion = 0
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
            sendRegister(webSocket)
            startHeartbeat(webSocket)
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
        val deviceId = appPreferences.installId
        val deviceName = Build.MODEL
        val msg = """{"type":"register","deviceId":"$deviceId","deviceType":"android","deviceName":"${deviceName.replace("\"", "\\\"")}"}"""
        ws.send(msg)
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

                    reactToState(old, newState)
                }
                "devices" -> {
                    val devicesEl = obj["devices"]?.jsonArray ?: return
                    _devices.value = devicesEl.map {
                        json.decodeFromJsonElement(ConnectDevice.serializer(), it)
                    }
                    Log.d(TAG, "Devices (${_devices.value.size}): ${_devices.value.joinToString { "${it.deviceName}[${it.deviceType}]" }}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse message: ${e.message}")
        }
    }

    private fun reactToState(old: ConnectState?, new: ConnectState) {
        // Clear pending command if the server confirmed the expected transition
        val cmd = _pendingCommand.value
        if (cmd != null) {
            if (cmd == "play" && new.playing) {
                Log.d(TAG, "reactToState: pending 'play' confirmed, clearing")
                _pendingCommand.value = null
            } else if (cmd == "pause" && !new.playing) {
                Log.d(TAG, "reactToState: pending 'pause' confirmed, clearing")
                _pendingCommand.value = null
            }
        }

        val isActive = new.activeDeviceId == appPreferences.installId
        val localRecordingId = mediaControllerRepository.currentRecordingId.value
        val locallyPlaying = mediaControllerRepository.isPlaying.value

        // If the recording changed (or first load), load it locally.
        // Active device: autoPlay based on server state.
        // Non-active device: load paused so the player shows the shared show.
        if (new.recordingId != null && new.recordingId != localRecordingId) {
            val autoPlay = isActive && new.playing
            Log.d(TAG, "reactToState: NEW RECORDING — server=${new.recordingId} local=$localRecordingId " +
                "isActive=$isActive autoPlay=$autoPlay trackIndex=${new.trackIndex} positionMs=${new.positionMs}")
            scope.launch {
                mediaControllerRepository.playTrack(
                    trackIndex = new.trackIndex,
                    recordingId = new.recordingId!!,
                    format = DEFAULT_FORMAT,
                    showId = new.showId ?: "",
                    showDate = new.date ?: "",
                    venue = new.venue,
                    location = new.location,
                    position = new.positionMs.toLong(),
                    autoPlay = autoPlay,
                )
            }
            return
        }

        // Not active device — stop local playback if another device took over
        if (!isActive) {
            if (locallyPlaying) {
                Log.d(TAG, "reactToState: NOT ACTIVE — pausing local playback")
                scope.launch { mediaControllerRepository.pause() }
            } else {
                Log.d(TAG, "reactToState: NOT ACTIVE — no action needed (local not playing)")
            }
            return
        }

        // Active device, same recording — just handle play/pause
        if (new.playing && !locallyPlaying) {
            Log.d(TAG, "reactToState: ACTIVE + SAME REC — server=play local=paused -> play()")
            scope.launch { mediaControllerRepository.play() }
        } else if (!new.playing && locallyPlaying) {
            Log.d(TAG, "reactToState: ACTIVE + SAME REC — server=pause local=playing -> pause()")
            scope.launch { mediaControllerRepository.pause() }
        } else {
            Log.d(TAG, "reactToState: ACTIVE + SAME REC — no change (server.playing=${new.playing} local=$locallyPlaying)")
        }
    }

    // -- Commands --

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
        _pendingCommand.value = "play"
        sendCommand("play")
    }

    override fun sendPause() {
        Log.d(TAG, "sendPause (pending=${_pendingCommand.value} -> pause)")
        _pendingCommand.value = "pause"
        sendCommand("pause")
    }

    override fun sendSeek(trackIndex: Int, positionMs: Int, durationMs: Int) {
        Log.d(TAG, "sendSeek: track=$trackIndex pos=$positionMs dur=$durationMs")
        sendCommand("seek", mapOf(
            "trackIndex" to trackIndex,
            "positionMs" to positionMs,
            "durationMs" to durationMs,
        ))
    }

    private fun sendCommand(action: String, extra: Map<String, Any> = emptyMap()) {
        val ws = webSocket
        if (ws == null) {
            Log.w(TAG, "sendCommand: $action DROPPED — webSocket is null")
            return
        }
        val obj = JSONObject()
        obj.put("type", "command")
        obj.put("action", action)
        for ((key, value) in extra) {
            when (value) {
                is List<*> -> obj.put(key, JSONArray().apply {
                    (value as? List<Map<*, *>>)?.forEach { map ->
                        put(JSONObject(map as Map<*, *>))
                    }
                })
                else -> obj.put(key, value)
            }
        }
        ws.send(obj.toString())
    }

    private fun handleDisconnect(closeCode: Int?) {
        stopHeartbeat()
        _isConnected.value = false
        webSocket = null

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
