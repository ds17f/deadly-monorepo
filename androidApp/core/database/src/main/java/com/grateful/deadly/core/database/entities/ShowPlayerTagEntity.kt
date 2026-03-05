package com.grateful.deadly.core.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "show_player_tags",
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
        Index(value = ["playerName"]),
        Index(value = ["showId", "playerName"], unique = true)
    ]
)
data class ShowPlayerTagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val showId: String,
    val playerName: String,
    val instruments: String? = null,
    val isStandout: Boolean = true,
    val notes: String? = null,
    val createdAt: Long
)
