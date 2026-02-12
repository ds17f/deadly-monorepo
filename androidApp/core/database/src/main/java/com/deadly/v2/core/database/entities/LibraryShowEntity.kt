package com.deadly.v2.core.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * V2 Library Show Entity - Pure V2 database implementation
 * 
 * Represents a show that has been added to the user's library.
 * Uses foreign key relationship to ShowEntity for data integrity.
 * Includes library-specific metadata like pin status and timestamps.
 */
@Entity(
    tableName = "library_shows",
    foreignKeys = [
        ForeignKey(
            entity = ShowEntity::class,
            parentColumns = ["showId"],
            childColumns = ["showId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["showId"], unique = true),
        Index(value = ["addedToLibraryAt"]),
        Index(value = ["isPinned"])
    ]
)
data class LibraryShowEntity(
    @PrimaryKey
    val showId: String,                    // References ShowEntity.showId
    
    // Library-specific metadata
    val addedToLibraryAt: Long,            // Timestamp when added to library
    val isPinned: Boolean = false,         // Pin status for prioritized display
    val libraryNotes: String? = null,      // User notes for this library item
    
    // Future expansion fields
    val customRating: Float? = null,       // User's personal rating override
    val lastAccessedAt: Long? = null,      // Track when user last viewed/played
    val tags: String? = null              // Comma-separated user tags
)