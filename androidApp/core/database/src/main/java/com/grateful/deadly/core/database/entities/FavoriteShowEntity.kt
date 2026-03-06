package com.grateful.deadly.core.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Favorite Show Entity
 *
 * Represents a show that has been added to the user's favorites.
 * Uses foreign key relationship to ShowEntity for data integrity.
 * Includes favorites-specific metadata like pin status and timestamps.
 */
@Entity(
    tableName = "favorite_shows",
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
        Index(value = ["addedToFavoritesAt"]),
        Index(value = ["isPinned"])
    ]
)
data class FavoriteShowEntity(
    @PrimaryKey
    val showId: String,                    // References ShowEntity.showId

    // Favorites-specific metadata
    val addedToFavoritesAt: Long,          // Timestamp when added to favorites
    val isPinned: Boolean = false,         // Pin status for prioritized display
    val notes: String? = null,             // User notes for this favorite

    // Recording preference
    val preferredRecordingId: String? = null,    // User's preferred recording for this show

    // Download tracking
    val downloadedRecordingId: String? = null,  // Which recording was downloaded
    val downloadedFormat: String? = null,        // Audio format of the download

    // Review fields
    val recordingQuality: Int? = null,     // 1-5 rating of recording quality
    val playingQuality: Int? = null,       // 1-5 rating of playing/performance quality

    // Future expansion fields
    val customRating: Float? = null,       // User's personal rating override
    val lastAccessedAt: Long? = null,      // Track when user last viewed/played
    val tags: String? = null              // Comma-separated user tags
)
