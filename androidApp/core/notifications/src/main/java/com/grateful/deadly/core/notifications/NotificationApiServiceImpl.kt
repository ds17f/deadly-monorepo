package com.grateful.deadly.core.notifications

import android.util.Log
import com.grateful.deadly.core.api.auth.AuthService
import com.grateful.deadly.core.database.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the public, cacheable notifications feed, and (when signed in) the
 * per-user read/dismiss overlay (ADR-0015). The feed fetch sends no auth header
 * (global content isn't user-specific); the overlay calls ride the authed
 * /api/user path with a Bearer token, mirroring UserSyncServiceImpl.
 */
@Singleton
class NotificationApiServiceImpl @Inject constructor(
    private val appPreferences: AppPreferences,
    private val authService: AuthService,
) : NotificationApiService {

    companion object {
        private const val TAG = "NotificationApi"
    }

    @Serializable
    private data class StateBody(val seenAt: Long?, val dismissedAt: Long?)

    @Serializable
    private data class BulkStateBody(val seenAt: Long?, val dismissedAt: Long?, val ids: List<Long>?)

    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json".toMediaType()

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

    override suspend fun pullState(): Result<List<NotificationStateRow>> {
        val token = authService.getAuthToken()
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val baseUrl = appPreferences.apiBaseUrl
        return try {
            val body = withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("$baseUrl/api/user/notifications/state")
                    .addHeader("Authorization", "Bearer $token")
                    .get().build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw RuntimeException("HTTP ${response.code}: ${response.body?.string().orEmpty()}")
                    }
                    response.body?.string() ?: throw RuntimeException("Empty response body")
                }
            }
            Result.success(json.decodeFromString(ListSerializer(NotificationStateRow.serializer()), body))
        } catch (e: Exception) {
            Log.w(TAG, "pullState failed", e)
            Result.failure(e)
        }
    }

    override suspend fun pushState(id: Long, seenAt: Long?, dismissedAt: Long?): Result<Unit> {
        val token = authService.getAuthToken()
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val baseUrl = appPreferences.apiBaseUrl
        val bodyJson = json.encodeToString(StateBody.serializer(), StateBody(seenAt, dismissedAt))
        return try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("$baseUrl/api/user/notifications/$id/state")
                    .addHeader("Authorization", "Bearer $token")
                    .post(bodyJson.toRequestBody(jsonMedia))
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw RuntimeException("HTTP ${response.code}: ${response.body?.string().orEmpty()}")
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "pushState failed for $id", e)
            Result.failure(e)
        }
    }

    override suspend fun pushStateBulk(seenAt: Long?, dismissedAt: Long?, ids: List<Long>?): Result<Unit> {
        val token = authService.getAuthToken()
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val baseUrl = appPreferences.apiBaseUrl
        val bodyJson = json.encodeToString(BulkStateBody.serializer(), BulkStateBody(seenAt, dismissedAt, ids))
        return try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("$baseUrl/api/user/notifications/state")
                    .addHeader("Authorization", "Bearer $token")
                    .post(bodyJson.toRequestBody(jsonMedia))
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw RuntimeException("HTTP ${response.code}: ${response.body?.string().orEmpty()}")
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "pushStateBulk failed", e)
            Result.failure(e)
        }
    }
}
