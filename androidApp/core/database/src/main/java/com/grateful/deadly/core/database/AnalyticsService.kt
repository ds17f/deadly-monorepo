package com.grateful.deadly.core.database

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.*

/**
 * Fire-and-forget anonymous analytics client.
 * Buffers events in memory and flushes to the server periodically.
 */
@Singleton
class AnalyticsService @Inject constructor(
    private val appPreferences: AppPreferences,
    @Named("analyticsApiKey") private val apiKey: String,
    @Named("appVersionName") private val appVersion: String
) {
    companion object {
        private const val TAG = "AnalyticsService"
        private const val FLUSH_INTERVAL_MS = 30_000L
        private const val MAX_BUFFER_SIZE = 50
    }

    private val sessionId = UUID.randomUUID().toString()
    private val buffer = CopyOnWriteArrayList<AnalyticsEvent>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        startFlushTimer()
    }

    fun track(event: String, props: Map<String, Any> = emptyMap()) {
        if (!appPreferences.analyticsEnabled.value) return

        buffer.add(
            AnalyticsEvent(
                event = event,
                ts = System.currentTimeMillis(),
                iid = appPreferences.installId,
                sid = sessionId,
                platform = "android",
                appVersion = appVersion,
                props = props.ifEmpty { null }
            )
        )

        if (buffer.size >= MAX_BUFFER_SIZE) {
            flush()
        }
    }

    fun flush() {
        val events = mutableListOf<AnalyticsEvent>()
        // Drain buffer atomically
        while (buffer.isNotEmpty()) {
            val event = buffer.removeFirstOrNull() ?: break
            events.add(event)
        }
        if (events.isEmpty()) return

        scope.launch {
            try {
                postEvents(events)
            } catch (e: Exception) {
                Log.d(TAG, "Analytics flush failed (discarding): ${e.message}")
            }
        }
    }

    private fun postEvents(events: List<AnalyticsEvent>) {
        val baseUrl = appPreferences.apiBaseUrl
        val url = URL("$baseUrl/api/analytics")
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-Analytics-Key", apiKey)
            connection.doOutput = true
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            val payload = JSONObject().apply {
                put("events", JSONArray().apply {
                    for (event in events) {
                        put(JSONObject().apply {
                            put("event", event.event)
                            put("ts", event.ts)
                            put("iid", event.iid)
                            put("sid", event.sid)
                            put("platform", event.platform)
                            put("app_version", event.appVersion)
                            if (event.props != null) {
                                put("props", JSONObject(event.props))
                            }
                        })
                    }
                })
            }

            connection.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            connection.responseCode // trigger the request
        } finally {
            connection.disconnect()
        }
    }

    private fun startFlushTimer() {
        scope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flush()
            }
        }
    }

    private data class AnalyticsEvent(
        val event: String,
        val ts: Long,
        val iid: String,
        val sid: String,
        val platform: String,
        val appVersion: String,
        val props: Map<String, Any>?
    )
}
