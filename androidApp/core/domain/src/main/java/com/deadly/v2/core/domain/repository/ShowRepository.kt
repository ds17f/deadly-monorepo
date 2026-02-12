package com.deadly.v2.core.domain.repository

import com.deadly.v2.core.model.Show
import com.deadly.v2.core.model.Recording
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository interface for show operations.
 * 
 * Returns pure domain models (Show, Recording) and has no dependencies
 * on Android or data layer implementation details. This interface
 * represents the contract that data layer implementations must fulfill.
 */
interface ShowRepository {
    
    // Show queries
    suspend fun getShowById(showId: String): Show?
    suspend fun getShowsByIds(showIds: List<String>): List<Show>
    suspend fun getAllShows(): List<Show>
    fun getAllShowsFlow(): Flow<List<Show>>
    suspend fun getShowsByYear(year: Int): List<Show>
    suspend fun getShowsByYearMonth(yearMonth: String): List<Show>
    suspend fun getShowsByVenue(venueName: String): List<Show>
    suspend fun getShowsByCity(city: String): List<Show>
    suspend fun getShowsByState(state: String): List<Show>
    suspend fun getShowsBySong(songName: String): List<Show>
    suspend fun getTopRatedShows(limit: Int = 20): List<Show>
    suspend fun getRecentShows(limit: Int = 20): List<Show>
    suspend fun getShowsForDate(month: Int, day: Int): List<Show>
    suspend fun getShowCount(): Int
    
    // Navigation queries
    suspend fun getNextShowByDate(currentDate: String): Show?
    suspend fun getPreviousShowByDate(currentDate: String): Show?
    
    // Recording queries
    suspend fun getRecordingsForShow(showId: String): List<Recording>
    suspend fun getBestRecordingForShow(showId: String): Recording?
    suspend fun getRecordingById(identifier: String): Recording?
    suspend fun getTopRatedRecordings(minRating: Double = 2.0, minReviews: Int = 5, limit: Int = 50): List<Recording>
}