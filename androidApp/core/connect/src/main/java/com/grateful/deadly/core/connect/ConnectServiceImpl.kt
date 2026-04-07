package com.grateful.deadly.core.connect

import android.os.Build
import android.util.Log
import com.grateful.deadly.core.api.auth.AuthService
import com.grateful.deadly.core.database.AppPreferences
import com.grateful.deadly.core.model.ConnectDevice
import com.grateful.deadly.core.model.ConnectState
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
import java.net.URLEncoder
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
) : ConnectService {

    companion object {
        private const val TAG = "ConnectService"
        private val RECONNECT_DELAYS_S = listOf(1L, 2L, 4L, 8L, 30L)
        private const val HEARTBEAT_INTERVAL_MS = 15_000L
        private const val CLOSE_CODE_UNAUTHORIZED = 4003
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

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var shouldConnect = false
    @Volatile private var reconnectAttempt = 0
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    override fun startIfAuthenticated() {
        authService.getAuthToken() ?: return
        if (shouldConnect) return
        shouldConnect = true
        reconnectAttempt = 0
        connect()
    }

    override fun stop() {
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
    }

    private fun connect() {
        val token = authService.getAuthToken() ?: return
        if (!shouldConnect) return

        val baseUrl = appPreferences.apiBaseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
        val encodedToken = URLEncoder.encode(token, "UTF-8")
        val url = "$baseUrl/ws/connect?token=$encodedToken"

        Log.d(TAG, "Connecting to $baseUrl/ws/connect")
        val request = Request.Builder().url(url).build()
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
                    _connectState.value = json.decodeFromJsonElement(ConnectState.serializer(), stateEl)
                }
                "devices" -> {
                    val devicesEl = obj["devices"]?.jsonArray ?: return
                    _devices.value = devicesEl.map {
                        json.decodeFromJsonElement(ConnectDevice.serializer(), it)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse message: ${e.message}")
        }
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
