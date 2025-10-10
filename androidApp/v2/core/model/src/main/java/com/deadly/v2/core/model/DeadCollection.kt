package com.deadly.v2.core.model

import kotlinx.serialization.Serializable

/**
 * DeadCollection domain model
 * 
 * Represents a curated collection of Grateful Dead shows with metadata.
 * Collections are curated groupings like "Dick's Picks", "Europe '72", 
 * "The Acid Tests", etc. Each collection contains actual Show domain objects
 * rather than just references.
 * 
 * The show selection logic (date ranges, exclusions, etc.) is handled
 * during import/entity conversion, not in the domain model.
 */
@Serializable
data class DeadCollection(
    val id: String,                    // "acid-tests", "dicks-picks"
    val name: String,                  // "The Acid Tests", "Dick's Picks"  
    val description: String,           // "The early days of the Grateful Dead..."
    val tags: List<String>,            // ["era", "early-dead", "psychedelic"]
    val shows: List<Show>              // Actual Show domain objects
) {
    /**
     * Total number of shows in this collection
     */
    val totalShows: Int
        get() = shows.size
    
    /**
     * Whether this collection has shows
     */
    val hasShows: Boolean
        get() = shows.isNotEmpty()
    
    /**
     * Display text showing show count
     */
    val showCountText: String
        get() = when (totalShows) {
            0 -> "No shows"
            1 -> "1 show"
            else -> "$totalShows shows"
        }
    
    /**
     * Primary tag for categorization (first tag)
     */
    val primaryTag: String?
        get() = tags.firstOrNull()
    
    /**
     * Whether this is an era-based collection
     */
    val isEraCollection: Boolean
        get() = tags.contains("era")
    
    /**
     * Whether this is an official release collection
     */
    val isOfficialRelease: Boolean
        get() = tags.contains("official") || tags.contains("dicks-picks")
}