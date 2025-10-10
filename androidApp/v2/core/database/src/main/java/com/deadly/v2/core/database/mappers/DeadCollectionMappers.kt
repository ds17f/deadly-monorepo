package com.deadly.v2.core.database.mappers

import android.util.Log
import com.deadly.v2.core.database.entities.DeadCollectionEntity
import com.deadly.v2.core.domain.repository.ShowRepository
import com.deadly.v2.core.model.DeadCollection
import com.deadly.v2.core.model.Show
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DeadCollectionMappers - Convert between DeadCollectionEntity and DeadCollection domain models
 * 
 * Handles JSON parsing of tags and show IDs, then resolves show IDs to actual 
 * Show domain objects via ShowRepository.
 */
@Singleton
class DeadCollectionMappers @Inject constructor(
    private val json: Json,
    private val showRepository: ShowRepository
) {
    
    companion object {
        private const val TAG = "CollectionMappers"
    }
    
    /**
     * Convert CollectionEntity to Collection domain model
     * 
     * Resolves show IDs to actual Show objects via ShowRepository
     */
    suspend fun entityToDomain(entity: DeadCollectionEntity): DeadCollection {
        val tags = parseTagsJson(entity.tagsJson)
        val showIds = parseShowIdsJson(entity.showIdsJson)
        val shows = resolveShowIds(showIds)
        
        return DeadCollection(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            tags = tags,
            shows = shows
        )
    }
    
    /**
     * Convert list of CollectionEntity to list of Collection domain models
     */
    suspend fun entitiesToDomain(entities: List<DeadCollectionEntity>): List<DeadCollection> {
        return entities.map { entityToDomain(it) }
    }
    
    /**
     * Convert Collection domain model to CollectionEntity
     * 
     * Used for creating/updating collections in database
     */
    fun domainToEntity(collection: DeadCollection, createdAt: Long = System.currentTimeMillis()): DeadCollectionEntity {
        val tagsJson = try {
            json.encodeToString(kotlinx.serialization.serializer(), collection.tags)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to encode tags to JSON: ${collection.tags}", e)
            "[]"
        }
        
        val showIdsJson = try {
            val showIds = collection.shows.map { it.id }
            json.encodeToString(kotlinx.serialization.serializer(), showIds)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to encode show IDs to JSON", e)
            "[]"
        }
        
        return DeadCollectionEntity(
            id = collection.id,
            name = collection.name,
            description = collection.description,
            tagsJson = tagsJson,
            showIdsJson = showIdsJson,
            totalShows = collection.totalShows,
            primaryTag = collection.primaryTag,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Parse tags JSON with safe fallback
     */
    private fun parseTagsJson(tagsJson: String): List<String> {
        if (tagsJson.isBlank()) return emptyList()
        
        return try {
            json.decodeFromString<List<String>>(tagsJson)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse tags JSON: $tagsJson", e)
            emptyList()
        }
    }
    
    /**
     * Parse show IDs JSON with safe fallback
     */
    private fun parseShowIdsJson(showIdsJson: String): List<String> {
        if (showIdsJson.isBlank()) return emptyList()
        
        return try {
            json.decodeFromString<List<String>>(showIdsJson)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse show IDs JSON: $showIdsJson", e)
            emptyList()
        }
    }
    
    /**
     * Resolve show IDs to Show domain objects via ShowRepository
     */
    private suspend fun resolveShowIds(showIds: List<String>): List<Show> {
        if (showIds.isEmpty()) return emptyList()
        
        return try {
            showRepository.getShowsByIds(showIds)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve show IDs: $showIds", e)
            emptyList()
        }
    }
}