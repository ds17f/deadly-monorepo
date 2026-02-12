package com.deadly.v2.core.network.archive.service

import com.deadly.v2.core.model.Review
import com.deadly.v2.core.model.RecordingMetadata
import com.deadly.v2.core.model.Track

/**
 * V2 Archive service interface for accessing Archive.org data
 * 
 * Provides clean domain interface for Archive.org API operations with
 * integrated filesystem caching. All methods return V2 domain models.
 */
interface ArchiveService {
    
    /**
     * Get recording metadata from Archive.org
     * Uses filesystem cache with 24-hour expiry
     * 
     * @param recordingId Archive.org identifier (e.g., "gd1977-05-08.sbd.miller.97065")
     * @return Result with RecordingMetadata domain model or error
     */
    suspend fun getRecordingMetadata(recordingId: String): Result<RecordingMetadata>
    
    /**
     * Get track list for a recording from Archive.org
     * Uses filesystem cache with 24-hour expiry
     * 
     * @param recordingId Archive.org identifier
     * @return Result with list of Track domain models or error
     */
    suspend fun getRecordingTracks(recordingId: String): Result<List<Track>>
    
    /**
     * Get reviews for a recording from Archive.org
     * Uses filesystem cache with 24-hour expiry
     * 
     * @param recordingId Archive.org identifier
     * @return Result with list of Review domain models or error
     */
    suspend fun getRecordingReviews(recordingId: String): Result<List<Review>>
    
    /**
     * Clear cached data for a specific recording
     * 
     * @param recordingId Archive.org identifier
     * @return Result indicating success or failure
     */
    suspend fun clearCache(recordingId: String): Result<Unit>
    
    /**
     * Clear all cached data
     * 
     * @return Result indicating success or failure
     */
    suspend fun clearAllCache(): Result<Unit>
}