package com.grateful.deadly.core.network.wikipedia.api

import com.grateful.deadly.core.network.wikipedia.model.WikipediaSummaryResponse
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface WikipediaApi {

    @GET("api/rest_v1/page/summary/{title}")
    suspend fun getPageSummary(
        @Path("title", encoded = true) title: String
    ): Response<WikipediaSummaryResponse>

    @GET("w/api.php")
    suspend fun search(
        @Query("action") action: String = "opensearch",
        @Query("search") query: String,
        @Query("limit") limit: Int = 1,
        @Query("format") format: String = "json"
    ): Response<JsonElement>
}
