package com.deadly.v2.core.network.github.service

import android.content.Context
import android.util.Log
import com.deadly.v2.core.network.github.api.GitHubReleasesApi
import com.deadly.v2.core.network.github.model.GitHubRelease
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.ResponseBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubDataService @Inject constructor(
    private val gitHubApi: GitHubReleasesApi,
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "GitHubDataService"
    }
    
    suspend fun getLatestRelease(): GitHubRelease? {
        return try {
            Log.d(TAG, "Fetching latest release from GitHub")
            val release = gitHubApi.getLatestRelease()
            Log.d(TAG, "Got release: ${release.tagName} with ${release.assets.size} assets")
            release
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch latest release", e)
            null
        }
    }
    
    suspend fun downloadFile(url: String): ResponseBody {
        Log.d(TAG, "Downloading file from: $url")
        return gitHubApi.downloadFile(url)
    }
}