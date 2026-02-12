package com.deadly.v2.core.database.service

import android.util.Log
import com.deadly.v2.core.model.V2Database
import com.deadly.v2.core.database.dao.CollectionsDao
import com.deadly.v2.core.database.dao.ShowDao
import com.deadly.v2.core.database.entities.DeadCollectionEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for importing collections from collections.json into database
 * 
 * Converts JSON collections with show_selector patterns into DeadCollectionEntity
 * records with resolved show IDs for database storage.
 */
@Singleton
class CollectionsImportService @Inject constructor(
    @V2Database private val collectionsDao: CollectionsDao,
    @V2Database private val showDao: ShowDao
) {
    
    companion object {
        private const val TAG = "CollectionsImportService"
        private const val COLLECTIONS_FILE = "collections.json"
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }
    
    @Serializable
    data class CollectionsWrapper(
        val collections: List<CollectionImportData>
    )
    
    @Serializable
    data class CollectionImportData(
        val id: String,
        val name: String,
        val description: String,
        val tags: List<String> = emptyList(),
        @SerialName("show_selector")
        val showSelector: ShowSelectorData? = null
    )
    
    @Serializable
    data class ShowSelectorData(
        val dates: List<String> = emptyList(),
        val ranges: List<DateRangeData> = emptyList(),
        val range: DateRangeData? = null, // External schema: singular range object
        @SerialName("exclusion_ranges") 
        val exclusionRanges: List<ExternalDateRangeData> = emptyList(), // External schema: exclusion ranges
        @SerialName("exclusion_dates") 
        val exclusionDates: List<String> = emptyList(), // External schema: exclusion dates
        @SerialName("show_ids") 
        val showIds: List<String> = emptyList(),
        val venues: List<String> = emptyList(),
        val years: List<Int> = emptyList()
    )
    
    @Serializable 
    data class DateRangeData(
        val start: String,
        val end: String
    )
    
    @Serializable 
    data class ExternalDateRangeData(
        val from: String,
        val to: String
    )
    
    /**
     * Import collections from JSON file in extracted data directory
     */
    suspend fun importCollectionsFromFile(extractedDataDirectory: File): CollectionsImportResult {
        return try {
            // Try multiple possible locations for collections.json
            val possiblePaths = listOf(
                File(extractedDataDirectory, COLLECTIONS_FILE),           // Same level as data directory
                File(extractedDataDirectory.parentFile, COLLECTIONS_FILE), // Parent of data directory
                File(extractedDataDirectory, "data/$COLLECTIONS_FILE")     // Inside data subdirectory
            )
            
            val collectionsFile = possiblePaths.firstOrNull { it.exists() }
            
            if (collectionsFile == null) {
                val searchedPaths = possiblePaths.map { it.absolutePath }
                Log.w(TAG, "Collections file not found in any of: ${searchedPaths.joinToString(", ")}")
                return CollectionsImportResult.Success(0, "No collections.json found")
            }
            
            Log.d(TAG, "Found collections.json at: ${collectionsFile.absolutePath}")
            
            Log.i(TAG, "Reading collections from: ${collectionsFile.absolutePath}")
            val jsonContent = collectionsFile.readText()
            
            val wrapper = json.decodeFromString<CollectionsWrapper>(jsonContent)
            val collections = wrapper.collections
            Log.i(TAG, "Parsed ${collections.size} collections from JSON")
            
            // Convert to entities with resolved show IDs
            val entities = collections.mapIndexed { index, collection ->
                Log.d(TAG, "Processing collection ${index + 1}/${collections.size}: '${collection.name}' (${collection.id})")
                val entity = convertToEntity(collection)
                Log.d(TAG, "Collection '${collection.name}' resolved to ${entity.totalShows} shows")
                entity
            }
            
            // Clear existing collections and insert new ones
            collectionsDao.deleteAllCollections()
            collectionsDao.insertCollections(entities)
            
            Log.i(TAG, "Successfully imported ${entities.size} collections")
            CollectionsImportResult.Success(entities.size, "Collections imported successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import collections", e)
            CollectionsImportResult.Error("Failed to import collections: ${e.message}")
        }
    }
    
    /**
     * Convert JSON import data to database entity with resolved show IDs
     */
    private suspend fun convertToEntity(collection: CollectionImportData): DeadCollectionEntity {
        val currentTime = System.currentTimeMillis()
        
        // Resolve show IDs from show_selector patterns
        val resolvedShowIds = resolveShowIds(collection.showSelector)
        
        // Convert to JSON for storage
        val tagsJson = json.encodeToString(ListSerializer(String.serializer()), collection.tags)
        val showIdsJson = json.encodeToString(ListSerializer(String.serializer()), resolvedShowIds)
        
        return DeadCollectionEntity(
            id = collection.id,
            name = collection.name,
            description = collection.description,
            tagsJson = tagsJson,
            showIdsJson = showIdsJson,
            totalShows = resolvedShowIds.size,
            primaryTag = collection.tags.firstOrNull(),
            createdAt = currentTime,
            updatedAt = currentTime
        )
    }
    
    /**
     * Resolve show_selector patterns into actual show IDs from database
     */
    private suspend fun resolveShowIds(showSelector: ShowSelectorData?): List<String> {
        if (showSelector == null) {
            Log.d(TAG, "No show_selector provided, returning empty list")
            return emptyList()
        }
        
        val resolvedIds = mutableSetOf<String>()
        var totalPatternsProcessed = 0
        var patternsWithResults = 0
        
        try {
            // Add explicit show IDs
            if (showSelector.showIds.isNotEmpty()) {
                resolvedIds.addAll(showSelector.showIds)
                totalPatternsProcessed++
                patternsWithResults++
                Log.d(TAG, "Added ${showSelector.showIds.size} explicit show IDs")
            }
            
            // Add shows by specific dates
            showSelector.dates.forEach { date ->
                val shows = showDao.getShowsByDate(date)
                if (shows.isNotEmpty()) {
                    resolvedIds.addAll(shows.map { it.showId })
                    patternsWithResults++
                }
                totalPatternsProcessed++
                Log.d(TAG, "Date '$date' resolved to ${shows.size} shows")
            }
            
            // Add shows by date ranges (plural format)
            showSelector.ranges.forEach { range ->
                val shows = showDao.getShowsInDateRange(range.start, range.end)
                if (shows.isNotEmpty()) {
                    resolvedIds.addAll(shows.map { it.showId })
                    patternsWithResults++
                }
                totalPatternsProcessed++
                Log.d(TAG, "Date range '${range.start}' to '${range.end}' resolved to ${shows.size} shows")
            }
            
            // Add shows by single date range (external schema format)
            showSelector.range?.let { range ->
                val shows = showDao.getShowsInDateRange(range.start, range.end)
                val showIds = shows.map { it.showId }.toMutableSet()
                
                // Apply exclusion ranges
                showSelector.exclusionRanges.forEach { exclusionRange ->
                    val exclusionShows = showDao.getShowsInDateRange(exclusionRange.from, exclusionRange.to)
                    val exclusionIds = exclusionShows.map { it.showId }.toSet()
                    showIds.removeAll(exclusionIds)
                    Log.d(TAG, "Exclusion range '${exclusionRange.from}' to '${exclusionRange.to}' removed ${exclusionIds.size} shows")
                }
                
                // Apply exclusion dates
                showSelector.exclusionDates.forEach { exclusionDate ->
                    val exclusionShows = showDao.getShowsByDate(exclusionDate)
                    val exclusionIds = exclusionShows.map { it.showId }.toSet()
                    showIds.removeAll(exclusionIds)
                    Log.d(TAG, "Exclusion date '$exclusionDate' removed ${exclusionIds.size} shows")
                }
                
                if (showIds.isNotEmpty()) {
                    resolvedIds.addAll(showIds)
                    patternsWithResults++
                }
                totalPatternsProcessed++
                Log.d(TAG, "Single range '${range.start}' to '${range.end}' (after exclusions) resolved to ${showIds.size} shows")
            }
            
            // Add shows by venue names
            showSelector.venues.forEach { venue ->
                val shows = showDao.getShowsByVenue(venue)
                if (shows.isNotEmpty()) {
                    resolvedIds.addAll(shows.map { it.showId })
                    patternsWithResults++
                }
                totalPatternsProcessed++
                Log.d(TAG, "Venue '$venue' resolved to ${shows.size} shows")
            }
            
            // Add shows by years
            showSelector.years.forEach { year ->
                val shows = showDao.getShowsByYear(year)
                if (shows.isNotEmpty()) {
                    resolvedIds.addAll(shows.map { it.showId })
                    patternsWithResults++
                }
                totalPatternsProcessed++
                Log.d(TAG, "Year $year resolved to ${shows.size} shows")
            }
            
            Log.i(TAG, "Show resolution summary: ${resolvedIds.size} unique shows from $patternsWithResults/$totalPatternsProcessed patterns")
            
            if (resolvedIds.isEmpty() && totalPatternsProcessed > 0) {
                Log.w(TAG, "⚠️ All show_selector patterns resolved to 0 shows - database may be empty or patterns don't match existing data")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception during show resolution", e)
        }
        
        return resolvedIds.toList().sorted()
    }
}

/**
 * Result of collections import operation
 */
sealed class CollectionsImportResult {
    data class Success(val importedCount: Int, val message: String) : CollectionsImportResult()
    data class Error(val error: String) : CollectionsImportResult()
}