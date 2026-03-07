package com.grateful.deadly.core.database.entities

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
    indices = [
        Index(value = ["showId"]),
        Index(value = ["showId", "trackTitle", "recordingId"], unique = true)
    ]
)
data class FavoriteSongEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val showId: String,
    val trackTitle: String,
    val trackNumber: Int? = null,
    val recordingId: String? = null,
    val createdAt: Long
)
