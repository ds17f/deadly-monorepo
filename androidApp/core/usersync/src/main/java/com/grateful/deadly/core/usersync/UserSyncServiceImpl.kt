package com.grateful.deadly.core.usersync

import android.util.Log
import com.grateful.deadly.core.api.auth.AuthService
import com.grateful.deadly.core.api.usersync.SyncBackupV3
import com.grateful.deadly.core.api.usersync.SyncFavoriteShowV3
import com.grateful.deadly.core.api.usersync.UserSyncService
import com.grateful.deadly.core.database.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserSyncServiceImpl @Inject constructor(
    private val authService: AuthService,
    private val appPreferences: AppPreferences,
) : UserSyncService {

    companion object {
        private const val TAG = "UserSyncService"
    }

    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun pullFullBackup(): Result<SyncBackupV3> {
        val token = authService.getAuthToken()
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val baseUrl = appPreferences.apiBaseUrl

        return try {
            val body = withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("$baseUrl/api/user/sync")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val bodyText = response.body?.string().orEmpty()
                        throw RuntimeException("HTTP ${response.code}: $bodyText")
                    }
                    response.body?.string()
                        ?: throw RuntimeException("Empty response body")
                }
            }
            Result.success(json.decodeFromString(SyncBackupV3.serializer(), body))
        } catch (e: Exception) {
            Log.w(TAG, "pullFullBackup failed", e)
            Result.failure(e)
        }
    }

    override suspend fun putFavoriteShow(show: SyncFavoriteShowV3): Result<Unit> {
        val token = authService.getAuthToken()
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val baseUrl = appPreferences.apiBaseUrl
        val bodyJson = json.encodeToString(SyncFavoriteShowV3.serializer(), show)

        return try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("$baseUrl/api/user/favorites/shows/${show.showId}")
                    .addHeader("Authorization", "Bearer $token")
                    .put(bodyJson.toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val bodyText = response.body?.string().orEmpty()
                        throw RuntimeException("HTTP ${response.code}: $bodyText")
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "putFavoriteShow failed for ${show.showId}", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteFavoriteShow(showId: String): Result<Unit> {
        val token = authService.getAuthToken()
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val baseUrl = appPreferences.apiBaseUrl

        return try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("$baseUrl/api/user/favorites/shows/$showId")
                    .addHeader("Authorization", "Bearer $token")
                    .delete()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    // 404 = server already lacks the row, treat as success.
                    if (!response.isSuccessful && response.code != 404) {
                        val bodyText = response.body?.string().orEmpty()
                        throw RuntimeException("HTTP ${response.code}: $bodyText")
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "deleteFavoriteShow failed for $showId", e)
            Result.failure(e)
        }
    }
}
