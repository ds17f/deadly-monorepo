package com.grateful.deadly.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorite_songs",
    foreignKeys = [
        ForeignKey(
            entity = ShowEntity::class,
            parentColumns = ["showId"],
            childColumns = ["showId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    // Identity is (showId, trackTitle) — matches the server. recordingId is
    // a property the row carries (used to navigate from favorites to "the
    // recording the user favorited it from") but does NOT participate in
    // uniqueness. See PLANS/mobile-server-sync.md.
    indices = [
        Index(value = ["showId"]),
        Index(value = ["showId", "trackTitle"], unique = true)
    ]
)
data class FavoriteSongEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val showId: String,
    val trackTitle: String,
    val trackNumber: Int? = null,
    val recordingId: String? = null,
    val createdAt: Long,
    // Sync support (see PLANS/mobile-server-sync.md)
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null
)
