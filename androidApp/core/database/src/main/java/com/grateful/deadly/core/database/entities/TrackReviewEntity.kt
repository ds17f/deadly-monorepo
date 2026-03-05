package com.grateful.deadly.core.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "track_reviews",
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
data class TrackReviewEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val showId: String,
    val trackTitle: String,
    val trackNumber: Int? = null,
    val recordingId: String? = null,
    val thumbs: Int? = null,           // 1=up, -1=down, null=unrated
    val starRating: Int? = null,       // 1-5
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
