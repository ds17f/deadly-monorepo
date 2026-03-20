package com.grateful.deadly.core.connect

import android.content.Context
import android.os.Build
import com.grateful.deadly.core.api.auth.AuthService
import com.grateful.deadly.core.api.auth.AuthState
import com.grateful.deadly.core.api.connect.*
import com.grateful.deadly.core.database.AppPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebSocket-backed Connect service.
 *
 * Connects when signed in, disconnects on sign-out, and handles
 * exponential-backoff reconnection (1 s → 30 s, matching the web client).
 */
@Singleton
class ConnectServiceImpl @Inject constructor(
    private val context: Context,
    private val authService: AuthService,
    private val appPreferences: AppPreferences,
) : ConnectService {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // no read timeout for WS
        .build()

    // ── Public state ────────────────────────────────────────────────────

    private val _connectionState = MutableStateFlow(ConnectConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectConnectionState> = _connectionState.asStateFlow()

    private val _devices = MutableStateFlow<List<ConnectDevice>>(emptyList())
    override val devices: StateFlow<List<ConnectDevice>> = _devices.asStateFlow()

    private val _userState = MutableStateFlow<UserPlaybackState?>(null)
    override val userState: StateFlow<UserPlaybackState?> = _userState.asStateFlow()

    // ── Internal ────────────────────────────────────────────────────────

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var reconnectDelay = INITIAL_RECONNECT_DELAY
    private var intentionalClose = false
    private var lastSyncedShowId: String? = null
    private var lastSyncedTrackIndex: Int? = null

    override val deviceId: String by lazy {
        val prefs = context.getSharedPreferences("connect_prefs", Context.MODE_PRIVATE)
        prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("device_id", it).apply()
        }
    }

    private val deviceName: String
        get() = "${Build.MANUFACTURER} ${Build.MODEL}"

    // ── Lifecycle ───────────────────────────────────────────────────────

    init {
        // Observe auth state changes → connect / disconnect
        scope.launch {
            authService.authState.collect { state ->
                when (state) {
                    is AuthState.SignedIn -> connect()
                    is AuthState.SignedOut -> disconnect()
                }
            }
        }
    }

    override fun connect() {
        val token = authService.getAuthToken() ?: return
        if (_connectionState.value == ConnectConnectionState.CONNECTED ||
            _connectionState.value == ConnectConnectionState.CONNECTING
        ) return

        intentionalClose = false
        reconnectJob?.cancel()

        val wsUrl = appPreferences.apiBaseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://") + "/ws/connect"

        val request = Request.Builder()
            .url(wsUrl)
            .header("Authorization", "Bearer $token")
            .build()

        _connectionState.value = ConnectConnectionState.CONNECTING

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                scope.launch {
                    _connectionState.value = ConnectConnectionState.CONNECTED
                    reconnectDelay = INITIAL_RECONNECT_DELAY
                    sendRegister(webSocket)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch { handleMessage(text) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                scope.launch {
                    _connectionState.value = ConnectConnectionState.DISCONNECTED
                    if (!intentionalClose) scheduleReconnect()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                scope.launch {
                    _connectionState.value = ConnectConnectionState.DISCONNECTED
                    if (!intentionalClose) scheduleReconnect()
                }
            }
        })
    }

    override fun disconnect() {
        intentionalClose = true
        reconnectJob?.cancel()
        webSocket?.close(1000, "sign-out")
        webSocket = null
        _connectionState.value = ConnectConnectionState.DISCONNECTED
        _devices.value = emptyList()
        _userState.value = null
        lastSyncedShowId = null
        lastSyncedTrackIndex = null
    }

    // ── Outgoing messages ───────────────────────────────────────────────

    override fun announcePlayback(state: ConnectPlaybackState) {
        lastSyncedShowId = state.showId
        lastSyncedTrackIndex = state.trackIndex
        send(buildJsonObject {
            put("type", "session_update")
            put("state", json.encodeToJsonElement(state))
        })
    }

    override fun sendPositionUpdate(state: ConnectPlaybackState) {
        send(buildJsonObject {
            put("type", "position_update")
            put("state", json.encodeToJsonElement(state))
        })
    }

    override fun claimSession() {
        send(buildJsonObject { put("type", "session_claim") })
    }

    override fun playOnDevice(targetDeviceId: String, state: ConnectPlaybackState) {
        send(buildJsonObject {
            put("type", "session_play_on")
            put("targetDeviceId", targetDeviceId)
            put("state", json.encodeToJsonElement(state))
        })
    }

    override fun sendCommand(targetDeviceId: String, command: PlaybackCommand) {
        send(buildJsonObject {
            put("type", "command")
            put("targetDeviceId", targetDeviceId)
            put("command", json.encodeToJsonElement(command))
        })
    }

    override fun clearState() {
        send(buildJsonObject { put("type", "state_clear") })
    }

    // ── Internal helpers ────────────────────────────────────────────────

    private fun sendRegister(ws: WebSocket) {
        val msg = buildJsonObject {
            put("type", "register")
            putJsonObject("device") {
                put("deviceId", deviceId)
                put("type", "android")
                put("name", deviceName)
                putJsonArray("capabilities") {
                    add("playback")
                    add("control")
                }
            }
        }
        ws.send(msg.toString())
    }

    private fun send(obj: JsonObject) {
        webSocket?.send(obj.toString())
    }

    private fun handleMessage(text: String) {
        val obj = try {
            json.parseToJsonElement(text).jsonObject
        } catch (_: Exception) {
            return
        }

        when (obj["type"]?.jsonPrimitive?.contentOrNull) {
            "devices" -> {
                val list = obj["devices"]?.let {
                    json.decodeFromJsonElement<List<ConnectDevice>>(it)
                } ?: emptyList()
                _devices.value = list
            }

            "user_state" -> {
                val state = obj["state"]?.let {
                    if (it is JsonNull) null
                    else json.decodeFromJsonElement<UserPlaybackState>(it)
                }
                _userState.value = state
                // Fire remote takeover if another device became active
                if (state?.activeDeviceId != null && state.activeDeviceId != deviceId) {
                    remoteTakeoverListeners.forEach { it() }
                }
                // Sync local player to match server state
                if (state != null) {
                    val showChanged = state.showId != lastSyncedShowId
                    val trackChanged = state.trackIndex != lastSyncedTrackIndex
                    if (showChanged || trackChanged) {
                        lastSyncedShowId = state.showId
                        lastSyncedTrackIndex = state.trackIndex
                        syncListeners.forEach { it(state) }
                    }
                }
            }

            "command_received" -> {
                val command = obj["command"]?.let {
                    json.decodeFromJsonElement<PlaybackCommand>(it)
                } ?: return
                val fromDeviceId = obj["fromDeviceId"]?.jsonPrimitive?.contentOrNull ?: ""
                commandListeners.forEach { it(fromDeviceId, command) }
            }

            "session_play_on" -> {
                val state = obj["state"]?.let {
                    json.decodeFromJsonElement<ConnectPlaybackState>(it)
                } ?: return
                playOnListeners.forEach { it(state) }
            }

            "active_session" -> {
                // Legacy — ignored in favor of user_state
            }

            "error" -> {
                // Could log: obj["message"]?.jsonPrimitive?.contentOrNull
            }
        }
    }

    private fun scheduleReconnect() {
        if (authService.getAuthToken() == null) return
        _connectionState.value = ConnectConnectionState.RECONNECTING
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(reconnectDelay)
            reconnectDelay = (reconnectDelay * 2).coerceAtMost(MAX_RECONNECT_DELAY)
            connect()
        }
    }

    // ── Callbacks for bridge ────────────────────────────────────────────

    private val commandListeners = mutableListOf<(String, PlaybackCommand) -> Unit>()
    private val playOnListeners = mutableListOf<(ConnectPlaybackState) -> Unit>()
    private val remoteTakeoverListeners = mutableListOf<() -> Unit>()
    private val syncListeners = mutableListOf<(UserPlaybackState) -> Unit>()

    fun onCommandReceived(listener: (fromDeviceId: String, command: PlaybackCommand) -> Unit) {
        commandListeners.add(listener)
    }

    fun onPlayOnReceived(listener: (state: ConnectPlaybackState) -> Unit) {
        playOnListeners.add(listener)
    }

    fun onRemoteTakeover(listener: () -> Unit) {
        remoteTakeoverListeners.add(listener)
    }

    fun onUserStateSync(listener: (UserPlaybackState) -> Unit) {
        syncListeners.add(listener)
    }

    companion object {
        private const val INITIAL_RECONNECT_DELAY = 1_000L
        private const val MAX_RECONNECT_DELAY = 30_000L
    }
}
