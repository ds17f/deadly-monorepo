package com.deadly.v2.core.network.archive.service

import android.content.Context
import android.util.Log
import com.deadly.v2.core.model.Review
import com.deadly.v2.core.model.RecordingMetadata
import com.deadly.v2.core.model.Track
import com.deadly.v2.core.network.archive.api.ArchiveApiService
import com.deadly.v2.core.network.archive.mapper.ArchiveMapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * V2 Archive service implementation with filesystem caching
 *
 * Caches API responses as JSON files with 24-hour expiry.
 * Cache structure: /cache/archive/{recordingId}.{type}.json
 */
@Singleton
class ArchiveServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val archiveApiService: ArchiveApiService,
    private val archiveMapper: ArchiveMapper
) : ArchiveService {

    companion object {
        private const val TAG = "ArchiveServiceImpl"
        private const val CACHE_EXPIRY_HOURS = 24
        private const val CACHE_DIR_NAME = "archive"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val cacheDir: File
      get() = File(context.cacheDir, CACHE_DIR_NAME).apply {
        if (!exists()) mkdirs()
      }

    override suspend fun getRecordingMetadata(recordingId: String): Result<RecordingMetadata> {
        return try {
            Log.d(TAG, "getRecordingMetadata($recordingId)")

            // Check filesystem cache first
            val cacheFile = File(cacheDir, "$recordingId.metadata.json")
            if (cacheFile.exists() && !isCacheExpired(cacheFile.lastModified())) {
                Log.d(TAG, "Cache hit for metadata: $recordingId")
                val cached = json.decodeFromString<RecordingMetadata>(cacheFile.readText())
                return Result.success(cached)
            }

            // Cache miss - fetch from API
            Log.d(TAG, "Cache miss for metadata: $recordingId, fetching from API")
            val response = archiveApiService.getRecordingMetadata(recordingId)

            if (response.isSuccessful && response.body() != null) {
                val apiResponse = response.body()!!
                val metadata = archiveMapper.mapToRecordingMetadata(apiResponse)

                // Cache the result
                cacheFile.writeText(json.encodeToString(metadata))
                Log.d(TAG, "Cached metadata for: $recordingId")

                Result.success(metadata)
            } else {
                Log.w(TAG, "API error for metadata: $recordingId - ${response.code()}")
                Result.failure(Exception("API error: ${response.code()}"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error getting metadata for $recordingId", e)
            Result.failure(e)
        }
    }

    override suspend fun getRecordingTracks(recordingId: String): Result<List<Track>> {
        return try {
            Log.d(TAG, "getRecordingTracks($recordingId)")

            // Check filesystem cache first
            val cacheFile = File(cacheDir, "$recordingId.tracks.json")
            if (cacheFile.exists() && !isCacheExpired(cacheFile.lastModified())) {
                Log.d(TAG, "Cache hit for tracks: $recordingId")
                val cached = json.decodeFromString<List<Track>>(cacheFile.readText())
                return Result.success(cached)
            }

            // Cache miss - fetch from API
            Log.d(TAG, "Cache miss for tracks: $recordingId, fetching from API")
            val response = archiveApiService.getRecordingMetadata(recordingId)

            if (response.isSuccessful && response.body() != null) {
                val apiResponse = response.body()!!
                val tracks = archiveMapper.mapToTracks(apiResponse)

                // Cache the result
                cacheFile.writeText(json.encodeToString(tracks))
                Log.d(TAG, "Cached ${tracks.size} tracks for: $recordingId")

                Result.success(tracks)
            } else {
                Log.w(TAG, "API error for tracks: $recordingId - ${response.code()}")
                Result.failure(Exception("API error: ${response.code()}"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error getting tracks for $recordingId", e)
            Result.failure(e)
        }
    }

    override suspend fun getRecordingReviews(recordingId: String): Result<List<Review>> {
        return try {
            Log.d(TAG, "getRecordingReviews($recordingId)")

            // Check filesystem cache first
            val cacheFile = File(cacheDir, "$recordingId.reviews.json")
            if (cacheFile.exists() && !isCacheExpired(cacheFile.lastModified())) {
                Log.d(TAG, "Cache hit for reviews: $recordingId")
                val cached = json.decodeFromString<List<Review>>(cacheFile.readText())
                return Result.success(cached)
            }

            // Cache miss - fetch from API
            Log.d(TAG, "Cache miss for reviews: $recordingId, fetching from API")
            val response = archiveApiService.getRecordingMetadata(recordingId)

            if (response.isSuccessful && response.body() != null) {
                val apiResponse = response.body()!!
                val reviews = archiveMapper.mapToReviews(apiResponse)

                // Cache the result
                cacheFile.writeText(json.encodeToString(reviews))
                Log.d(TAG, "Cached ${reviews.size} reviews for: $recordingId")

                Result.success(reviews)
            } else {
                Log.w(TAG, "API error for reviews: $recordingId - ${response.code()}")
                Result.failure(Exception("API error: ${response.code()}"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error getting reviews for $recordingId", e)
            Result.failure(e)
        }
    }

    override suspend fun clearCache(recordingId: String): Result<Unit> {
        return try {
            Log.d(TAG, "clearCache($recordingId)")

            val metadataFile = File(cacheDir, "$recordingId.metadata.json")
            val tracksFile = File(cacheDir, "$recordingId.tracks.json")
            val reviewsFile = File(cacheDir, "$recordingId.reviews.json")

            metadataFile.delete()
            tracksFile.delete()
            reviewsFile.delete()

            Log.d(TAG, "Cleared cache for: $recordingId")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache for $recordingId", e)
            Result.failure(e)
        }
    }

    override suspend fun clearAllCache(): Result<Unit> {
        return try {
            Log.d(TAG, "clearAllCache()")

            val files = cacheDir.listFiles() ?: emptyArray()
            var deletedCount = 0

            files.forEach { file ->
                if (file.isFile && file.name.endsWith(".json")) {
                    file.delete()
                    deletedCount++
                }
            }

            Log.d(TAG, "Cleared all cache: deleted $deletedCount files")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all cache", e)
            Result.failure(e)
        }
    }

    /**
     * Check if cache file has expired (24 hours)
     */
    private fun isCacheExpired(timestamp: Long): Boolean {
        val expiryTime = timestamp + (CACHE_EXPIRY_HOURS * 60 * 60 * 1000L)
        return System.currentTimeMillis() > expiryTime
    }
}