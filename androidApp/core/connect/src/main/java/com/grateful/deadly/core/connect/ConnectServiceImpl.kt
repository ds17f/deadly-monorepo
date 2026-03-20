package com.grateful.deadly.core.connect

import android.content.Context
import android.os.Build
import android.util.Log
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

@Singleton
class ConnectServiceImpl @Inject constructor(
    private val context: Context,
    private val authService: AuthService,
    private val appPreferences: AppPreferences,
) : ConnectService {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val _connectionState = MutableStateFlow(ConnectConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectConnectionState> = _connectionState.asStateFlow()

    private val _devices = MutableStateFlow<List<ConnectDevice>>(emptyList())
    override val devices: StateFlow<List<ConnectDevice>> = _devices.asStateFlow()

    private val _userState = MutableStateFlow<UserPlaybackState?>(null)
    override val userState: StateFlow<UserPlaybackState?> = _userState.asStateFlow()

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var reconnectDelay = INITIAL_RECONNECT_DELAY
    private var intentionalClose = false

    override val deviceId: String by lazy {
        val prefs = context.getSharedPreferences("connect_prefs", Context.MODE_PRIVATE)
        prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("device_id", it).apply()
        }
    }

    private val deviceName: String
        get() = "${Build.MANUFACTURER} ${Build.MODEL}"

    init {
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
                    Log.w(TAG, "WebSocket failure", t)
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
    }

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
        Log.d(TAG, "Sent register: deviceId=$deviceId")
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
                Log.d(TAG, "Devices: ${list.map { "${it.name} (${it.type})" }}")
            }
            "user_state" -> {
                val state = try {
                    obj["state"]?.let { json.decodeFromJsonElement<UserPlaybackState>(it) }
                } catch (_: Exception) {
                    null
                }
                _userState.value = state
                Log.d(TAG, "[Connect] User state: playing=${state?.isPlaying}, activeDevice=${state?.activeDeviceName}")
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

    companion object {
        private const val TAG = "Connect"
        private const val INITIAL_RECONNECT_DELAY = 1_000L
        private const val MAX_RECONNECT_DELAY = 30_000L
    }
}
