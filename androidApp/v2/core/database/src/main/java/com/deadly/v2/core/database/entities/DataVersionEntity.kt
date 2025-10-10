package com.deadly.v2.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "data_version_v2")
data class DataVersionEntity(
    @PrimaryKey
    val id: Int = 1,              // Only one row ever
    
    // Version info
    val dataVersion: String,      // "2.0.0" from manifest.json
    val packageName: String,      // "Deadly Metadata"
    val versionType: String,      // "release"
    val description: String?,     // Package description
    
    // Import info
    val importedAt: Long,         // When imported
    val gitCommit: String?,       // Git commit from manifest
    val gitTag: String?,          // Git tag from manifest
    val buildTimestamp: String?,  // Build timestamp from manifest
    
    // Statistics
    val totalShows: Int = 0,
    val totalVenues: Int = 0,
    val totalFiles: Int = 0,
    val totalSizeBytes: Long = 0
)