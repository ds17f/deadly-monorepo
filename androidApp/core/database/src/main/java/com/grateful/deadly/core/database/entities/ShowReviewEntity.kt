package com.grateful.deadly.core.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "show_reviews",
    foreignKeys = [
        ForeignKey(
            entity = ShowEntity::class,
            parentColumns = ["showId"],
            childColumns = ["showId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ShowReviewEntity(
    @PrimaryKey
    val showId: String,
    val notes: String? = null,
    val customRating: Float? = null,
    val recordingQuality: Int? = null,
    val playingQuality: Int? = null,
    val reviewedRecordingId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
