package com.grateful.deadly.core.usersync

import android.util.Log
import com.grateful.deadly.core.api.auth.AuthService
import com.grateful.deadly.core.api.usersync.SyncBackupV3
import com.grateful.deadly.core.api.usersync.UserSyncService
import com.grateful.deadly.core.database.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
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
}
