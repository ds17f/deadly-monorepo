package com.grateful.deadly.core.network.genius.api

import com.grateful.deadly.core.network.genius.model.GeniusSearchResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface GeniusApi {

    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Header("Authorization") auth: String
    ): Response<GeniusSearchResponse>
}
