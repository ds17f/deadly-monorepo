package com.deadly.v2.core.network.archive.api

import com.deadly.v2.core.network.archive.model.ArchiveMetadataResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Archive.org API service interface for accessing Grateful Dead concert recordings
 * 
 * Base URL: https://archive.org/
 * API Documentation: https://archive.org/help/aboutapi.php
 */
interface ArchiveApiService {
    
    /**
     * Get detailed metadata for a specific concert recording
     * 
     * @param identifier Archive.org item identifier
     * @return Metadata response with files, full metadata, and reviews
     */
    @GET("metadata/{identifier}")
    suspend fun getRecordingMetadata(
        @Path("identifier") identifier: String
    ): Response<ArchiveMetadataResponse>
}