package com.deadly.v2.core.database.entities

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * FTS4 entity for full-text search on show data.
 * 
 * Architecture: Separate FTS table that references ShowEntity.id
 * - rowid: Primary key for FTS table (auto-generated)
 * - showId: Foreign key to ShowEntity
 * - searchText: Rich searchable content (venue, location, date, year)
 * 
 * Example searchText: "1977-05-08 5-8-77 5/8/77 Barton Hall Ithaca, NY"
 * 
 * FTS4 with unicode61 tokenizer:
 * - Handles dates with dashes (5-8-77) as single tokens
 * - Handles dates with dashes (5.8.77) as single tokens
 * - BM26 ranking algorithm for relevance scoring
 * - tokenchars=- preserves dashes in date tokens
 */
@Entity(tableName = "show_search")
@Fts4(
    tokenizer = "unicode61",
    tokenizerArgs = ["tokenchars=-."]
)
data class ShowSearchEntity(
    @PrimaryKey(autoGenerate = true) val rowid: Int = 0,
    val showId: String,        // References ShowEntity.id
    val searchText: String     // Rich searchable content
)