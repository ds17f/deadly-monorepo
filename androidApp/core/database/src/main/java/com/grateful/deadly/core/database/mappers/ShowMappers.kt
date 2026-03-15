package com.grateful.deadly.core.database.mappers

import android.util.Log
import com.grateful.deadly.core.database.entities.ShowEntity
import com.grateful.deadly.core.database.entities.ShowSummary
import com.grateful.deadly.core.database.entities.RecordingEntity
import com.grateful.deadly.core.model.*
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ShowMappers - Centralized conversion between data and domain models
 * 
 * Handles safe JSON parsing with empty list fallbacks on errors.
 * All conversion logic is isolated here for testability and maintainability.
 */
@Singleton
class ShowMappers @Inject constructor(
    private val json: Json
) {
    
    companion object {
        private const val TAG = "ShowMappers"
    }
    
    /**
     * Convert ShowEntity to Show domain model
     */
    fun entityToDomain(entity: ShowEntity): Show {
        return Show(
            id = entity.showId,
            date = entity.date,
            year = entity.year,
            band = entity.band,
            venue = Venue(
                name = entity.venueName,
                city = entity.city,
                state = entity.state,
                country = entity.country
            ),
            location = Location.fromRaw(entity.locationRaw, entity.city, entity.state),
            setlist = parseSetlist(entity.setlistRaw, entity.setlistStatus),
            lineup = parseLineup(entity.lineupRaw, entity.lineupStatus),
            recordingIds = parseRecordingIds(entity.recordingsRaw),
            bestRecordingId = entity.bestRecordingId,
            bestSourceType = RecordingSourceType.fromString(entity.bestSourceType),
            coverImageUrl = entity.coverImageUrl,
            recordingCount = entity.recordingCount,
            averageRating = entity.averageRating,
            totalReviews = entity.totalReviews,
            isFavorite = entity.isFavorite,
            favoritedAt = entity.favoritedAt
        )
    }
    
    /**
     * Convert list of ShowEntity to list of Show domain models
     */
    fun entitiesToDomain(entities: List<ShowEntity>): List<Show> =
        entities.map { entityToDomain(it) }

    /**
     * Convert ShowSummary projection to Show domain model.
     * No JSON parsing — all fields map directly.
     */
    fun summaryToDomain(summary: ShowSummary): Show {
        return Show(
            id = summary.showId,
            date = summary.date,
            year = summary.year,
            band = summary.band,
            venue = Venue(
                name = summary.venueName,
                city = summary.city,
                state = summary.state,
                country = summary.country
            ),
            location = Location.fromRaw(summary.locationRaw, summary.city, summary.state),
            setlist = null,
            lineup = null,
            recordingIds = emptyList(),
            bestRecordingId = summary.bestRecordingId,
            bestSourceType = RecordingSourceType.fromString(summary.bestSourceType),
            coverImageUrl = summary.coverImageUrl,
            recordingCount = summary.recordingCount,
            averageRating = summary.averageRating,
            totalReviews = summary.totalReviews,
            isFavorite = summary.isFavorite,
            favoritedAt = summary.favoritedAt
        )
    }

    fun summariesToDomain(summaries: List<ShowSummary>): List<Show> =
        summaries.map { summaryToDomain(it) }

    /**
     * Convert RecordingEntity to Recording domain model
     */
    fun recordingEntityToDomain(entity: RecordingEntity): Recording {
        return Recording(
            identifier = entity.identifier,
            showId = entity.showId,
            sourceType = RecordingSourceType.fromString(entity.sourceType),
            rating = entity.rating,
            reviewCount = entity.reviewCount,
            taper = entity.taper,
            source = entity.source,
            lineage = entity.lineage,
            sourceTypeString = entity.sourceTypeString
        )
    }
    
    /**
     * Convert list of RecordingEntity to list of Recording domain models
     */
    fun recordingEntitiesToDomain(entities: List<RecordingEntity>): List<Recording> = 
        entities.map { recordingEntityToDomain(it) }
    
    /**
     * Parse recording IDs from JSON string with safe fallback
     */
    private fun parseRecordingIds(jsonString: String?): List<String> {
        if (jsonString.isNullOrBlank()) {
            return emptyList()
        }
        
        return try {
            json.decodeFromString<List<String>>(jsonString)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse recording IDs from JSON: $jsonString", e)
            emptyList() // Safe fallback - no crashes
        }
    }
    
    /**
     * Parse setlist from JSON with safe fallback
     */
    private fun parseSetlist(jsonString: String?, status: String?): Setlist? {
        return try {
            Setlist.parse(jsonString, status)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse setlist from JSON: $jsonString", e)
            null // Safe fallback
        }
    }
    
    /**
     * Parse lineup from JSON with safe fallback
     */
    private fun parseLineup(jsonString: String?, status: String?): Lineup? {
        return try {
            Lineup.parse(jsonString, status)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse lineup from JSON: $jsonString", e)
            null // Safe fallback
        }
    }
}