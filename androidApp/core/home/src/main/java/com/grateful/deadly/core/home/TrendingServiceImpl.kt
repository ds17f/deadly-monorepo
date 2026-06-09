package com.grateful.deadly.core.home

import android.util.Log
import com.grateful.deadly.core.api.home.TrendingContent
import com.grateful.deadly.core.api.home.TrendingService
import com.grateful.deadly.core.database.AppPreferences
import com.grateful.deadly.core.database.service.DatabaseManager
import com.grateful.deadly.core.domain.repository.ShowRepository
import com.grateful.deadly.core.model.Show
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches trending shows from /api/trending and resolves the returned IDs
 * into Show domain models via the local catalog. Refreshes on init and
 * periodically; failures keep the previous content rather than blanking.
 */
@Singleton
class TrendingServiceImpl @Inject constructor(
    private val appPreferences: AppPreferences,
    private val showRepository: ShowRepository,
    private val databaseManager: DatabaseManager,
) : TrendingService {

    companion object {
        private const val TAG = "TrendingServiceImpl"
        private const val REFRESH_INTERVAL_MS = 10 * 60 * 1000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _trending = MutableStateFlow(TrendingContent.initial())
    override val trending: StateFlow<TrendingContent> = _trending.asStateFlow()

    init {
        scope.launch {
            while (isActive) {
                refresh()
                delay(REFRESH_INTERVAL_MS)
            }
        }
        // Refetch immediately when the user flips the anniversaries filter
        // — the param changes the response, so the cached content is stale.
        scope.launch {
            appPreferences.homeTrendingIncludeAnniversaries
                .drop(1)
                .collect { refresh() }
        }
        // Re-fetch when the first-launch catalog import completes. On a fresh
        // install the init refresh above races the import: the API call
        // succeeds but the returned show IDs resolve against an empty catalog,
        // so without this the rail stays blank until the next 10-min tick or a
        // cold restart. Observing the import-complete signal repaints it as
        // soon as the catalog is queryable.
        scope.launch {
            databaseManager.progress
                .filter { it.phase == "COMPLETED" }
                .collect {
                    Log.d(TAG, "Catalog import completed → refresh trending")
                    refresh()
                }
        }
    }

    override suspend fun refresh(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = fetchTrendingJson()
            val windows = raw.getJSONObject("windows")
            val nowIds = parseIds(windows, "now")
            val weekIds = parseIds(windows, "week")
            val monthIds = parseIds(windows, "month")
            val allIds = parseIds(windows, "all")

            // Single batch lookup, then partition. Preserves the server's
            // ranking order — getShowsByIds() does not guarantee order, so
            // we reorder each window list ourselves.
            val unique = (nowIds + weekIds + monthIds + allIds).toSet().toList()
            val shows = showRepository.getShowsByIds(unique).associateBy { it.id }

            // Guard the first-launch race: if the API returned IDs but none
            // resolve, the catalog isn't populated yet. Keep the previous
            // content instead of caching an empty rail until the next tick.
            if (unique.isNotEmpty() && shows.isEmpty()) {
                Log.d(TAG, "Trending IDs unresolved (catalog not ready) — keeping previous content")
                return@runCatching
            }

            _trending.value = TrendingContent(
                now = nowIds.mapNotNull { shows[it] },
                week = weekIds.mapNotNull { shows[it] },
                month = monthIds.mapNotNull { shows[it] },
                all = allIds.mapNotNull { shows[it] },
                lastRefresh = System.currentTimeMillis(),
            )
        }.onFailure {
            Log.w(TAG, "Trending refresh failed: ${it.message}")
        }
    }

    private fun fetchTrendingJson(): JSONObject {
        val includeAnniv = appPreferences.homeTrendingIncludeAnniversaries.value
        val query = if (includeAnniv) "?include_anniversaries=true" else ""
        val url = URL("${appPreferences.apiBaseUrl}/api/trending$query")
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

    private fun parseIds(windows: JSONObject, key: String): List<String> {
        val arr = windows.optJSONArray(key) ?: return emptyList()
        return List(arr.length()) { i -> arr.getJSONObject(i).getString("show_id") }
    }
}
