package com.deadly.v2.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.deadly.v2.core.database.entities.ShowEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShowDao {
    
    // Core operations for import
    @Insert
    suspend fun insert(show: ShowEntity)
    
    @Insert
    suspend fun insertAll(shows: List<ShowEntity>)
    
    // Basic queries for verification
    @Query("SELECT * FROM shows ORDER BY date DESC")
    suspend fun getAllShows(): List<ShowEntity>
    
    @Query("SELECT * FROM shows ORDER BY date DESC")
    fun getAllShowsFlow(): Flow<List<ShowEntity>>
    
    @Query("SELECT * FROM shows WHERE showId = :showId")
    suspend fun getShowById(showId: String): ShowEntity?
    
    @Query("SELECT * FROM shows WHERE showId IN (:showIds)")
    suspend fun getShowsByIds(showIds: List<String>): List<ShowEntity>
    
    @Query("SELECT COUNT(*) FROM shows")
    suspend fun getShowCount(): Int
    
    // Date-based queries
    @Query("SELECT * FROM shows WHERE year = :year ORDER BY date")
    suspend fun getShowsByYear(year: Int): List<ShowEntity>
    
    @Query("SELECT * FROM shows WHERE yearMonth = :yearMonth ORDER BY date")
    suspend fun getShowsByYearMonth(yearMonth: String): List<ShowEntity>
    
    @Query("SELECT * FROM shows WHERE date = :date ORDER BY showSequence")
    suspend fun getShowsByDate(date: String): List<ShowEntity>
    
    @Query("SELECT * FROM shows WHERE date >= :startDate AND date <= :endDate ORDER BY date")
    suspend fun getShowsInDateRange(startDate: String, endDate: String): List<ShowEntity>
    
    
    // Location queries
    @Query("SELECT * FROM shows WHERE venueName LIKE '%' || :venueName || '%' ORDER BY date")
    suspend fun getShowsByVenue(venueName: String): List<ShowEntity>
    
    @Query("SELECT * FROM shows WHERE city = :city ORDER BY date DESC")
    suspend fun getShowsByCity(city: String): List<ShowEntity>
    
    @Query("SELECT * FROM shows WHERE state = :state ORDER BY date DESC")
    suspend fun getShowsByState(state: String): List<ShowEntity>
    
    // Search queries
    @Query("""
        SELECT * FROM shows 
        WHERE songList LIKE '%' || :songName || '%' 
        ORDER BY date DESC
    """)
    suspend fun getShowsBySong(songName: String): List<ShowEntity>
    
    // Popular/featured queries
    @Query("SELECT * FROM shows WHERE averageRating IS NOT NULL ORDER BY averageRating DESC LIMIT :limit")
    suspend fun getTopRatedShows(limit: Int = 20): List<ShowEntity>
    
    @Query("SELECT * FROM shows ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentShows(limit: Int = 20): List<ShowEntity>
    
    @Query("SELECT * FROM shows WHERE month = :month AND SUBSTR(date, 9, 2) = PRINTF('%02d', :day) AND recordingCount > 0 ORDER BY year")
    suspend fun getShowsForDate(month: Int, day: Int): List<ShowEntity>
    
    // Navigation queries for efficient chronological traversal
    @Query("SELECT * FROM shows WHERE date > :currentDate ORDER BY date ASC LIMIT 1")
    suspend fun getNextShowByDate(currentDate: String): ShowEntity?
    
    @Query("SELECT * FROM shows WHERE date < :currentDate ORDER BY date DESC LIMIT 1") 
    suspend fun getPreviousShowByDate(currentDate: String): ShowEntity?
    
    // Specific famous shows for verification
    @Query("SELECT * FROM shows WHERE date = '1977-05-08'")
    suspend fun getCornell77(): List<ShowEntity>
    
    // Management operations
    @Query("DELETE FROM shows")
    suspend fun deleteAll()
}