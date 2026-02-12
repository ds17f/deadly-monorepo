package com.deadly.v2.core.api.collections

import com.deadly.v2.core.model.Show
import com.deadly.v2.core.model.DeadCollection
import kotlinx.coroutines.flow.StateFlow

/**
 * DeadCollectionsService - Service interface for curated show collections
 * 
 * Manages curated collections of Grateful Dead shows including:
 * - Dick's Picks series (36 official releases)
 * - Europe '72 tour shows  
 * - Greatest Shows compilations
 * - Wall of Sound era (1974)
 * - Rare and limited circulation recordings
 * - Acoustic sets and unplugged performances
 * 
 * Collections provide organized discovery paths for users beyond
 * simple date/venue searches.
 */
interface DeadCollectionsService {
    
    /**
     * Reactive featured collections for home screen
     * Provides curated subset of most popular/interesting collections
     */
    val featuredCollections: StateFlow<List<DeadCollection>>
    
    /**
     * Get all available collections
     * @return Result with complete list of collections or error
     */
    suspend fun getAllCollections(): Result<List<DeadCollection>>
    
    /**
     * Get shows for a specific collection
     * @param collectionId Unique identifier for the collection
     * @return Result with shows in the collection or error
     */
    suspend fun getCollectionShows(collectionId: String): Result<List<Show>>
    
    /**
     * Get detailed information about a collection
     * @param collectionId Unique identifier for the collection  
     * @return Result with collection details or error
     */
    suspend fun getCollectionDetails(collectionId: String): Result<DeadCollectionDetails>
    
    /**
     * Search collections by name or description
     * @param query Search term
     * @return Result with matching collections or error
     */
    suspend fun searchCollections(query: String): Result<List<DeadCollection>>
    
    /**
     * Get collections containing a specific show
     * @param showId The show ID to search for
     * @return Result with collections containing this show or error
     */
    suspend fun getCollectionsContainingShow(showId: String): Result<List<DeadCollection>>
}

/**
 * Detailed collection information with metadata
 */
data class DeadCollectionDetails(
    val collection: DeadCollection,
    val curator: String? = null,       // "Dick Latvala"
    val releaseInfo: String? = null,   // "Released 1993-2005"
    val description: String? = null,   // Extended description
    val tags: List<String> = emptyList() // ["soundboard", "official", "archival"]
)

