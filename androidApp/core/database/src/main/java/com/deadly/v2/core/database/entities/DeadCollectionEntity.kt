package com.deadly.v2.core.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * DeadCollection entity for Room database storage
 * 
 * Stores curated collections with resolved show IDs as JSON strings.
 * The show selector logic from import JSON is resolved during import
 * and stored as a simple list of show IDs.
 */
@Entity(
    tableName = "dead_collections",
    indices = [
        Index(value = ["primaryTag"]),
        Index(value = ["totalShows"])
    ]
)
data class DeadCollectionEntity(
    @PrimaryKey
    val id: String,                    // "acid-tests", "dicks-picks"
    
    val name: String,                  // "The Acid Tests", "Dick's Picks"
    val description: String,           // "The early days of the Grateful Dead..."
    
    // JSON serialized data
    val tagsJson: String,              // JSON array: ["era", "early-dead", "psychedelic"]
    val showIdsJson: String,           // JSON array: ["1965-12-04-big-nigs-house-...", ...]
    
    // Precomputed for performance
    val totalShows: Int,               // Count of shows
    val primaryTag: String?,           // First tag for indexing/filtering
    
    // Timestamps
    val createdAt: Long,
    val updatedAt: Long
)