package com.grateful.deadly.core.home

import android.util.Log
import com.grateful.deadly.core.api.home.PopularContent
import com.grateful.deadly.core.api.home.PopularService
import com.grateful.deadly.core.database.AppPreferences
import com.grateful.deadly.core.domain.repository.ShowRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches "Fan Favorites" shows from /api/popular and resolves the
 * returned IDs into Show domain models via the local catalog. Single
 * list (no windows). Refreshes on init and periodically.
 */
@Singleton
class PopularServiceImpl @Inject constructor(
    private val appPreferences: AppPreferences,
    private val showRepository: ShowRepository,
) : PopularService {

    companion object {
        private const val TAG = "PopularServiceImpl"
        private const val REFRESH_INTERVAL_MS = 10 * 60 * 1000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _popular = MutableStateFlow(PopularContent.initial())
    override val popular: StateFlow<PopularContent> = _popular.asStateFlow()

    init {
        scope.launch {
            while (isActive) {
                refresh()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    override suspend fun refresh(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = fetchJson()
            val arr = raw.optJSONArray("shows") ?: return@runCatching
            val ids = List(arr.length()) { i -> arr.getJSONObject(i).getString("show_id") }
            val byId = showRepository.getShowsByIds(ids).associateBy { it.id }
            _popular.value = PopularContent(
                shows = ids.mapNotNull { byId[it] },
                lastRefresh = System.currentTimeMillis(),
            )
        }.onFailure {
            Log.w(TAG, "Popular refresh failed: ${it.message}")
        }
    }

    private fun fetchJson(): JSONObject {
        val url = URL("${appPreferences.apiBaseUrl}/api/popular")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }
}
