package com.grateful.deadly.core.notifications

import android.util.Log
import com.grateful.deadly.core.database.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the public, cacheable notifications feed. Mirrors UserSyncServiceImpl's
 * OkHttp + kotlinx.serialization shape, minus the Authorization header (global
 * content isn't user-specific, so the fetch fires regardless of sign-in state).
 */
@Singleton
class NotificationApiServiceImpl @Inject constructor(
    private val appPreferences: AppPreferences,
) : NotificationApiService {

    companion object {
        private const val TAG = "NotificationApi"
    }

    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetch(since: Long): Result<NotificationFetchResult> {
        val baseUrl = appPreferences.apiBaseUrl
        return try {
            val body = withContext(Dispatchers.IO) {
                val url = "$baseUrl/api/notifications".toHttpUrl().newBuilder().apply {
                    if (since > 0) addQueryParameter("since", since.toString())
                }.build()
                val request = Request.Builder().url(url).get().build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val bodyText = response.body?.string().orEmpty()
                        throw RuntimeException("HTTP ${response.code}: $bodyText")
                    }
                    response.body?.string() ?: throw RuntimeException("Empty response body")
                }
            }
            Result.success(json.decodeFromString(NotificationFetchResult.serializer(), body))
        } catch (e: Exception) {
            Log.w(TAG, "fetch(since=$since) failed", e)
            Result.failure(e)
        }
    }
}
