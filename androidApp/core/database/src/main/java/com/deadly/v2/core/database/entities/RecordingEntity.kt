package com.deadly.v2.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recordings",
    foreignKeys = [
        ForeignKey(
            entity = ShowEntity::class,
            parentColumns = ["showId"],
            childColumns = ["show_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["show_id"]),
        Index(value = ["source_type"]),
        Index(value = ["rating"]),
        Index(value = ["show_id", "rating"]) // For quality-based filtering per show
    ]
)
data class RecordingEntity(
    @PrimaryKey
    val identifier: String, // Archive.org unique identifier (e.g., "gd1977-05-08.sbd.miller.97375.sbeok.flac16")
    
    @ColumnInfo(name = "show_id")
    val showId: String,
    
    @ColumnInfo(name = "source_type")
    val sourceType: String? = null, // "SBD", "AUD", "FM", "MATRIX", "REMASTER", "UNKNOWN"
    
    // Quality metrics from Archive.org reviews
    @ColumnInfo(name = "rating")
    val rating: Double = 0.0, // Weighted rating for internal ranking (0.0-5.0)
    
    @ColumnInfo(name = "raw_rating")
    val rawRating: Double = 0.0, // Simple average for display (0.0-5.0)
    
    @ColumnInfo(name = "review_count")
    val reviewCount: Int = 0, // Number of reviews
    
    @ColumnInfo(name = "confidence")
    val confidence: Double = 0.0, // Rating confidence (0.0-1.0)
    
    @ColumnInfo(name = "high_ratings")
    val highRatings: Int = 0, // Count of 4-5★ reviews
    
    @ColumnInfo(name = "low_ratings")
    val lowRatings: Int = 0, // Count of 1-2★ reviews
    
    // Recording detail fields for rich descriptions
    @ColumnInfo(name = "taper")
    val taper: String? = null, // Person who recorded the show
    
    @ColumnInfo(name = "source")
    val source: String? = null, // Equipment chain info
    
    @ColumnInfo(name = "lineage") 
    val lineage: String? = null, // Digital transfer chain
    
    @ColumnInfo(name = "source_type_string")
    val sourceTypeString: String? = null, // Raw source type string from data
    
    @ColumnInfo(name = "collection_timestamp")
    val collectionTimestamp: Long = System.currentTimeMillis()
)