package com.grateful.deadly.core.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "recording_preferences",
    foreignKeys = [
        ForeignKey(
            entity = ShowEntity::class,
            parentColumns = ["showId"],
            childColumns = ["showId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RecordingPreferenceEntity(
    @PrimaryKey
    val showId: String,
    val recordingId: String,
    val updatedAt: Long
)
