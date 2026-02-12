package com.deadly.v2.core.database.mappers

import android.util.Log
import com.deadly.v2.core.database.entities.ShowEntity
import com.deadly.v2.core.database.entities.RecordingEntity
import com.deadly.v2.core.model.*
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
            recordingCount = entity.recordingCount,
            averageRating = entity.averageRating,
            totalReviews = entity.totalReviews,
            isInLibrary = entity.isInLibrary,
            libraryAddedAt = entity.libraryAddedAt
        )
    }
    
    /**
     * Convert list of ShowEntity to list of Show domain models
     */
    fun entitiesToDomain(entities: List<ShowEntity>): List<Show> = 
        entities.map { entityToDomain(it) }
    
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