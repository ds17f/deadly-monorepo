package com.deadly.v2.core.database.repository

import com.deadly.v2.core.database.dao.ShowDao
import com.deadly.v2.core.database.dao.RecordingDao
import com.deadly.v2.core.database.dao.DataVersionDao
import com.deadly.v2.core.database.mappers.ShowMappers
import com.deadly.v2.core.domain.repository.ShowRepository
import com.deadly.v2.core.model.Show
import com.deadly.v2.core.model.Recording
import com.deadly.v2.core.model.V2Database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ShowRepositoryImpl - Clean architecture implementation
 * 
 * Implements the domain ShowRepository interface and converts
 * data models (ShowEntity, RecordingEntity) to domain models
 * (Show, Recording) at the repository boundary using ShowMappers.
 */
@Singleton
class ShowRepositoryImpl @Inject constructor(
    @V2Database private val showDao: ShowDao,
    @V2Database private val recordingDao: RecordingDao,
    @V2Database private val dataVersionDao: DataVersionDao,
    private val showMappers: ShowMappers
) : ShowRepository {
    
    // Show queries - all return domain models
    override suspend fun getShowById(showId: String): Show? {
        return showDao.getShowById(showId)?.let { 
            showMappers.entityToDomain(it) 
        }
    }
    
    override suspend fun getShowsByIds(showIds: List<String>): List<Show> {
        if (showIds.isEmpty()) return emptyList()
        return showMappers.entitiesToDomain(showDao.getShowsByIds(showIds))
    }
    
    override suspend fun getAllShows(): List<Show> {
        return showMappers.entitiesToDomain(showDao.getAllShows())
    }
    
    override fun getAllShowsFlow(): Flow<List<Show>> {
        return showDao.getAllShowsFlow().map { entities ->
            showMappers.entitiesToDomain(entities)
        }
    }
    
    override suspend fun getShowsByYear(year: Int): List<Show> {
        return showMappers.entitiesToDomain(showDao.getShowsByYear(year))
    }
    
    override suspend fun getShowsByYearMonth(yearMonth: String): List<Show> {
        return showMappers.entitiesToDomain(showDao.getShowsByYearMonth(yearMonth))
    }
    
    override suspend fun getShowsByVenue(venueName: String): List<Show> {
        return showMappers.entitiesToDomain(showDao.getShowsByVenue(venueName))
    }
    
    override suspend fun getShowsByCity(city: String): List<Show> {
        return showMappers.entitiesToDomain(showDao.getShowsByCity(city))
    }
    
    override suspend fun getShowsByState(state: String): List<Show> {
        return showMappers.entitiesToDomain(showDao.getShowsByState(state))
    }
    
    override suspend fun getShowsBySong(songName: String): List<Show> {
        return showMappers.entitiesToDomain(showDao.getShowsBySong(songName))
    }
    
    override suspend fun getTopRatedShows(limit: Int): List<Show> {
        return showMappers.entitiesToDomain(showDao.getTopRatedShows(limit))
    }
    
    override suspend fun getRecentShows(limit: Int): List<Show> {
        return showMappers.entitiesToDomain(showDao.getRecentShows(limit))
    }
    
    override suspend fun getShowsForDate(month: Int, day: Int): List<Show> {
        return showMappers.entitiesToDomain(showDao.getShowsForDate(month, day))
    }
    
    override suspend fun getShowCount(): Int = showDao.getShowCount()
    
    // Navigation queries - efficient chronological traversal
    override suspend fun getNextShowByDate(currentDate: String): Show? {
        return showDao.getNextShowByDate(currentDate)?.let { 
            showMappers.entityToDomain(it) 
        }
    }
    
    override suspend fun getPreviousShowByDate(currentDate: String): Show? {
        return showDao.getPreviousShowByDate(currentDate)?.let { 
            showMappers.entityToDomain(it) 
        }
    }
    
    // Recording queries - all return domain models
    override suspend fun getRecordingsForShow(showId: String): List<Recording> {
        return showMappers.recordingEntitiesToDomain(recordingDao.getRecordingsForShow(showId))
    }
    
    override suspend fun getBestRecordingForShow(showId: String): Recording? {
        return recordingDao.getBestRecordingForShow(showId)?.let { 
            showMappers.recordingEntityToDomain(it) 
        }
    }
    
    override suspend fun getRecordingById(identifier: String): Recording? {
        return recordingDao.getRecordingById(identifier)?.let { 
            showMappers.recordingEntityToDomain(it) 
        }
    }
    
    override suspend fun getTopRatedRecordings(minRating: Double, minReviews: Int, limit: Int): List<Recording> {
        return showMappers.recordingEntitiesToDomain(
            recordingDao.getTopRatedRecordings(minRating, minReviews, limit)
        )
    }
}

/**
 * Legacy data access methods for backward compatibility during transition
 * 
 * TODO: Remove once all consumers are updated to use domain models
 */
@Singleton
class LegacyShowRepository @Inject constructor(
    private val showDao: ShowDao,
    private val recordingDao: RecordingDao,
    private val dataVersionDao: DataVersionDao
) {
    // Data version queries (no domain equivalent yet)
    suspend fun getDataVersion() = dataVersionDao.getDataVersion()
}