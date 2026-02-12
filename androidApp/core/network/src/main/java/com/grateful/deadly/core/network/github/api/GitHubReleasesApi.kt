package com.grateful.deadly.core.network.github.api

import com.grateful.deadly.core.network.github.model.GitHubRelease
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

interface GitHubReleasesApi {
    
    @GET("repos/ds17f/dead-metadata/releases/latest")
    suspend fun getLatestRelease(): GitHubRelease
    
    @Streaming
    @GET
    suspend fun downloadFile(@Url url: String): ResponseBody
}