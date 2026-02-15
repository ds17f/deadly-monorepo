package com.grateful.deadly.core.collections

import android.util.Log
import com.grateful.deadly.core.api.collections.DeadCollectionDetails
import com.grateful.deadly.core.api.collections.DeadCollectionsService
import com.grateful.deadly.core.domain.repository.ShowRepository
import com.grateful.deadly.core.model.DeadCollection
import com.grateful.deadly.core.model.Show
import com.grateful.deadly.core.model.AppDatabase
import com.grateful.deadly.core.database.dao.CollectionsDao
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DeadCollectionsServiceImpl - Production implementation of DeadCollectionsService
 * 
 * Provides curated Grateful Dead show collections with real data integration.
 * Collections are defined statically but can reference real shows from the database.
 */
@Singleton
class DeadCollectionsServiceImpl @Inject constructor(
    private val showRepository: ShowRepository,
    @AppDatabase private val collectionsDao: CollectionsDao
) : DeadCollectionsService {
    
    companion object {
        private const val TAG = "DeadCollectionsServiceImpl"
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    
    override val featuredCollections: StateFlow<List<DeadCollection>> =
        collectionsDao.getFeaturedCollectionsFlow()
            .map { entities ->
                try {
                    val collections = entities.map { convertToModel(it) }
                    Log.d(TAG, "Loaded ${collections.size} featured collections from database")
                    collections
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to convert featured collections", e)
                    createFallbackCollections()
                }
            }
            .stateIn(
                scope = serviceScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    init {
        Log.d(TAG, "DeadCollectionsServiceImpl initialized")
    }
    
    override suspend fun getAllCollections(): Result<List<DeadCollection>> {
        return try {
            val entities = collectionsDao.getAllCollections()
            val collections = entities.map { convertToModel(it) }
            Result.success(collections)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all collections", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getCollectionShows(collectionId: String): Result<List<Show>> {
        return try {
            val entity = collectionsDao.getCollectionById(collectionId)
                ?: return Result.failure(IllegalArgumentException("Collection not found: $collectionId"))
            
            // Parse show IDs from JSON and get shows
            val showIds = json.decodeFromString<List<String>>(entity.showIdsJson)
            val shows = showRepository.getShowsByIds(showIds)
            
            Result.success(shows)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get shows for collection $collectionId", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getCollectionDetails(collectionId: String): Result<DeadCollectionDetails> {
        return try {
            val entity = collectionsDao.getCollectionById(collectionId)
                ?: return Result.failure(IllegalArgumentException("Collection not found: $collectionId"))
            
            val collection = convertToModel(entity)
            val shows = getCollectionShows(collectionId).getOrElse { emptyList() }
            
            val details = when (collectionId) {
                "dicks-picks" -> DeadCollectionDetails(
                    collection = collection,
                    curator = "Dick Latvala",
                    releaseInfo = "Released 1993-2005, 36 volumes",
                    description = "Dick Latvala's archival series featuring the best soundboard recordings from the Grateful Dead vault. Each volume represents Dick's personal selection of outstanding performances.",
                    tags = listOf("soundboard", "official", "archival", "dick-latvala")
                )
                "europe-72" -> DeadCollectionDetails(
                    collection = collection,
                    curator = "Grateful Dead",
                    releaseInfo = "Tour: April-May 1972",
                    description = "The legendary European tour that revitalized the band and produced countless classics. Features the final performances with Pigpen.",
                    tags = listOf("tour", "1972", "europe", "pigpen", "classic")
                )
                else -> DeadCollectionDetails(
                    collection = collection
                )
            }
            
            Result.success(details)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get collection details for $collectionId", e)
            Result.failure(e)
        }
    }
    
    override suspend fun searchCollections(query: String): Result<List<DeadCollection>> {
        return try {
            val entities = collectionsDao.searchCollections(query)
            val collections = entities.map { convertToModel(it) }
            Result.success(collections)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search collections for '$query'", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getCollectionsContainingShow(showId: String): Result<List<DeadCollection>> {
        return try {
            val entities = collectionsDao.getCollectionsContainingShow(showId)
            val collections = entities.map { convertToModel(it) }
            Log.d(TAG, "Found ${collections.size} collections containing show $showId")
            Result.success(collections)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get collections containing show $showId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Convert database entity to domain model
     */
    private suspend fun convertToModel(entity: com.grateful.deadly.core.database.entities.DeadCollectionEntity): DeadCollection {
        val tags = json.decodeFromString<List<String>>(entity.tagsJson)
        val showIds = json.decodeFromString<List<String>>(entity.showIdsJson)
        
        // Get actual shows for this collection
        val shows = try {
            showRepository.getShowsByIds(showIds)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load shows for collection ${entity.id}", e)
            emptyList()
        }
        
        return DeadCollection(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            tags = tags,
            shows = shows
        )
    }
    
    /**
     * Create fallback collections when database is empty
     */
    private fun createFallbackCollections(): List<DeadCollection> {
        return listOf(
            DeadCollection(
                id = "dicks-picks",
                name = "Dick's Picks",
                description = "Dick Latvala's archival series featuring the best soundboard recordings",
                tags = listOf("official", "soundboard", "archival", "dick-latvala"),
                shows = emptyList() // Fallback metadata only; shows load via database in normal path
            ),
            DeadCollection(
                id = "europe-72",
                name = "Europe '72",
                description = "The legendary European tour that produced countless classics",
                tags = listOf("tour", "1972", "europe", "pigpen", "classic"),
                shows = emptyList() // Fallback metadata only; shows load via database in normal path
            ),
            DeadCollection(
                id = "greatest-shows",
                name = "Greatest Shows",
                description = "The most celebrated concerts in Grateful Dead history",
                tags = listOf("quality", "greatest", "top-rated"),
                shows = emptyList() // Fallback metadata only; shows load via database in normal path
            ),
            DeadCollection(
                id = "wall-of-sound",
                name = "Wall of Sound",
                description = "Shows featuring the massive Wall of Sound PA system (1974)",
                tags = listOf("era", "1974", "wall-of-sound", "technology"),
                shows = emptyList() // Fallback metadata only; shows load via database in normal path
            ),
            DeadCollection(
                id = "rare-recordings",
                name = "Rare Recordings",
                description = "Hard-to-find and limited circulation recordings",
                tags = listOf("rarity", "limited", "rare", "circulation"),
                shows = emptyList() // Fallback metadata only; shows load via database in normal path
            ),
            DeadCollection(
                id = "acoustic-sets",
                name = "Acoustic Sets", 
                description = "Intimate acoustic performances and rare unplugged moments",
                tags = listOf("theme", "acoustic", "intimate", "unplugged"),
                shows = emptyList() // Fallback metadata only; shows load via database in normal path
            ),
            DeadCollection(
                id = "fillmore-west",
                name = "Fillmore West",
                description = "Classic performances at Bill Graham's legendary venue",
                tags = listOf("venue", "fillmore", "bill-graham", "legendary"),
                shows = emptyList() // Fallback metadata only; shows load via database in normal path
            ),
            DeadCollection(
                id = "egypt-78",
                name = "Egypt '78",
                description = "The mystical concerts at the Great Pyramid of Giza",
                tags = listOf("tour", "1978", "egypt", "pyramid", "mystical"),
                shows = emptyList() // Fallback metadata only; shows load via database in normal path
            ),
            DeadCollection(
                id = "long-jams",
                name = "Epic Jams",
                description = "Extended improvisational journeys and marathon songs",
                tags = listOf("theme", "jams", "improvisation", "extended"),
                shows = emptyList() // Fallback metadata only; shows load via database in normal path
            ),
            DeadCollection(
                id = "new-years",
                name = "New Year's Shows",
                description = "Celebration concerts welcoming each new year",
                tags = listOf("theme", "new-year", "celebration", "annual"),
                shows = emptyList() // Fallback metadata only; shows load via database in normal path
            )
        )
    }
}