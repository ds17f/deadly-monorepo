package com.grateful.deadly.core.network.github.service

import android.content.Context
import android.util.Log
import com.grateful.deadly.core.network.github.api.GitHubReleasesApi
import com.grateful.deadly.core.network.github.model.GitHubRelease
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
        /** Data version pinned at build time from data/version. Update when bumping data release. */
        const val REQUIRED_DATA_VERSION = "2.3.0"
    }
    
    suspend fun getLatestRelease(): GitHubRelease? {
        return getRelease("data-v$REQUIRED_DATA_VERSION")
    }

    suspend fun getRelease(tag: String): GitHubRelease? {
        return try {
            Log.d(TAG, "Fetching release $tag from GitHub")
            val release = gitHubApi.getReleaseByTag(tag)
            Log.d(TAG, "Got release: ${release.tagName} with ${release.assets.size} assets")
            release
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch release $tag", e)
            null
        }
    }
    
    suspend fun downloadFile(url: String): ResponseBody {
        Log.d(TAG, "Downloading file from: $url")
        return gitHubApi.downloadFile(url)
    }
}