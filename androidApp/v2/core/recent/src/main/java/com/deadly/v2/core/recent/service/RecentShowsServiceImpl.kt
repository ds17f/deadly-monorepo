package com.deadly.v2.core.recent.service

import android.util.Log
import com.deadly.v2.core.api.recent.RecentShowsService
import com.deadly.v2.core.database.dao.RecentShowDao
import com.deadly.v2.core.database.entities.RecentShowEntity
import com.deadly.v2.core.domain.repository.ShowRepository
import com.deadly.v2.core.media.repository.MediaControllerRepository
import com.deadly.v2.core.model.PlaybackStatus
import com.deadly.v2.core.model.Show
import com.deadly.v2.core.model.V2Database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * V2 RecentShowsService implementation with real database persistence and MediaController observation.
 * 
 * Architecture:
 * - Observes MediaControllerRepository StateFlows for track changes
 * - Applies smart filtering: 10 seconds MAX (25% only for tracks <40 seconds)
 * - Uses UPSERT pattern with RecentShowDao for deduplication
 * - Provides reactive StateFlow by converting database entities to domain models
 * - Debounces rapid track changes to avoid noise from skipping
 * 
 * Key features:
 * - Smart play detection prevents rapid-skip spam
 * - Show-level tracking from track-level events
 * - Real-time UI updates via reactive StateFlow
 * - Persistent across app restarts
 * - Privacy controls (clear, remove specific shows)
 */
@Singleton
class RecentShowsServiceImpl @Inject constructor(
    @V2Database private val recentShowDao: RecentShowDao,
    private val showRepository: ShowRepository,
    private val mediaControllerRepository: MediaControllerRepository,
    @Named("RecentShowsApplicationScope") private val applicationScope: CoroutineScope
) : RecentShowsService {
    
    companion object {
        private const val TAG = "RecentShowsServiceImpl"
        private const val DEFAULT_RECENT_LIMIT = 8
        private const val MEANINGFUL_PLAY_DURATION_MS = 10_000L // 10 seconds
        private const val MEANINGFUL_PLAY_PERCENTAGE = 0.25f // 25% of track
    }
    
    /**
     * Data class to combine playback information from multiple StateFlows
     */
    private data class PlaybackInfo(
        val showId: String?,
        val recordingId: String?,
        val playbackStatus: PlaybackStatus,
        val isPlaying: Boolean
    )
    
    // Internal state for debouncing and tracking
    private var currentTrackShowId: String? = null
    private var currentTrackStartTime: Long = 0
    private var hasRecordedCurrentTrack = false
    
    // Cached recent shows StateFlow
    private val _recentShows = MutableStateFlow<List<Show>>(emptyList())
    override val recentShows: StateFlow<List<Show>> = _recentShows.asStateFlow()
    
    init {
        Log.d(TAG, "Initializing RecentShowsService with MediaController observation")
        startObservingPlayback()
        startObservingRecentShows()
    }
    
    /**
     * Observe MediaController StateFlows to detect meaningful plays
     */
    private fun startObservingPlayback() {
        applicationScope.launch {
            combine(
                mediaControllerRepository.currentShowId,
                mediaControllerRepository.currentRecordingId, 
                mediaControllerRepository.playbackStatus,
                mediaControllerRepository.isPlaying
            ) { showId, recordingId, playbackStatus, isPlaying ->
                PlaybackInfo(showId, recordingId, playbackStatus, isPlaying)
            }
            .distinctUntilChanged()
            .collect { playbackInfo ->
                handlePlaybackStateChange(playbackInfo)
            }
        }
    }
    
    /**
     * Observe database changes and update StateFlow
     */
    private fun startObservingRecentShows() {
        applicationScope.launch {
            recentShowDao.getRecentShowsFlow(DEFAULT_RECENT_LIMIT)
                .map { entities -> convertEntitiesToShows(entities) }
                .flowOn(Dispatchers.IO)
                .collect { shows ->
                    _recentShows.value = shows
                    Log.d(TAG, "Recent shows updated: ${shows.size} shows")
                }
        }
    }
    
    /**
     * Handle playback state changes and apply hybrid filtering
     */
    private fun handlePlaybackStateChange(playbackInfo: PlaybackInfo) {
        val showId = playbackInfo.showId
        
        if (showId.isNullOrBlank()) {
            resetTrackingState()
            return
        }
        
        // Track changes
        if (showId != currentTrackShowId) {
            Log.d(TAG, "Track/show changed: $currentTrackShowId -> $showId")
            resetTrackingState()
            currentTrackShowId = showId
            currentTrackStartTime = System.currentTimeMillis()
            hasRecordedCurrentTrack = false
        }
        
        // Check if we should record this play
        if (playbackInfo.isPlaying && !hasRecordedCurrentTrack && shouldRecordPlay(playbackInfo.playbackStatus)) {
            Log.d(TAG, "Recording meaningful play for show: $showId")
            applicationScope.launch {
                recordShowPlay(showId)
            }
            hasRecordedCurrentTrack = true
        }
    }
    
    /**
     * Smart filtering: 10 seconds MAX, or 25% for very short tracks (<40 seconds)
     */
    private fun shouldRecordPlay(playbackStatus: PlaybackStatus): Boolean {
        val position = playbackStatus.currentPosition
        val duration = playbackStatus.duration
        
        // For tracks longer than 40 seconds: simple 10 second rule
        if (duration > 40_000L) {
            val qualifies = position >= MEANINGFUL_PLAY_DURATION_MS
            if (qualifies) {
                Log.d(TAG, "Long track qualifies by 10s rule: ${position}ms >= ${MEANINGFUL_PLAY_DURATION_MS}ms")
            }
            return qualifies
        }
        
        // For very short tracks (<=40 seconds): use 25% rule with 10s maximum
        if (duration > 0) {
            val percentageThreshold = (duration * MEANINGFUL_PLAY_PERCENTAGE).toLong()
            val actualThreshold = minOf(percentageThreshold, MEANINGFUL_PLAY_DURATION_MS)
            
            val qualifies = position >= actualThreshold
            if (qualifies) {
                Log.d(TAG, "Short track qualifies: ${position}ms >= ${actualThreshold}ms (25% of ${duration}ms, capped at 10s)")
            }
            return qualifies
        }
        
        return false
    }
    
    
    /**
     * Reset tracking state when track/show changes
     */
    private fun resetTrackingState() {
        currentTrackShowId = null
        currentTrackStartTime = 0
        hasRecordedCurrentTrack = false
    }
    
    /**
     * Convert database entities to domain models
     */
    private suspend fun convertEntitiesToShows(entities: List<RecentShowEntity>): List<Show> {
        return entities.mapNotNull { entity ->
            try {
                showRepository.getShowById(entity.showId)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get show ${entity.showId} from repository", e)
                null
            }
        }
    }
    
    // === RecentShowsService Interface Implementation ===
    
    override suspend fun recordShowPlay(showId: String, playTimestamp: Long) {
        try {
            Log.d(TAG, "Recording show play: $showId at $playTimestamp")
            
            val existingShow = recentShowDao.getShowById(showId)
            if (existingShow != null) {
                // Update existing show
                recentShowDao.updateShow(
                    showId = showId,
                    timestamp = playTimestamp,
                    playCount = existingShow.totalPlayCount + 1
                )
                Log.d(TAG, "Updated existing show $showId (playCount: ${existingShow.totalPlayCount + 1})")
            } else {
                // Insert new show
                val newEntity = RecentShowEntity.createNew(showId, playTimestamp)
                recentShowDao.insert(newEntity)
                Log.d(TAG, "Inserted new show $showId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record show play for $showId", e)
        }
    }
    
    override suspend fun getRecentShows(limit: Int): List<Show> {
        return try {
            val entities = recentShowDao.getRecentShows(limit)
            convertEntitiesToShows(entities)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get recent shows", e)
            emptyList()
        }
    }
    
    override suspend fun isShowInRecent(showId: String): Boolean {
        return try {
            recentShowDao.getShowById(showId) != null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check if show $showId is in recent", e)
            false
        }
    }
    
    override suspend fun removeShow(showId: String) {
        try {
            recentShowDao.removeShow(showId)
            Log.d(TAG, "Removed show $showId from recent shows")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove show $showId from recent", e)
        }
    }
    
    override suspend fun clearRecentShows() {
        try {
            recentShowDao.clearAll()
            Log.d(TAG, "Cleared all recent shows")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear recent shows", e)
        }
    }
    
    override suspend fun getRecentShowsStats(): Map<String, Any> {
        return try {
            val totalCount = recentShowDao.getRecentShowCount()
            val recentEntities = recentShowDao.getRecentShows(100) // Get more for stats
            
            val mostPlayedShow = recentEntities.maxByOrNull { it.totalPlayCount }
            val oldestShow = recentEntities.minByOrNull { it.firstPlayedTimestamp }
            val newestShow = recentEntities.maxByOrNull { it.lastPlayedTimestamp }
            
            mapOf(
                "totalRecentShows" to totalCount,
                "currentCacheSize" to _recentShows.value.size,
                "mostPlayedShowId" to (mostPlayedShow?.showId ?: "none"),
                "mostPlayedShowCount" to (mostPlayedShow?.totalPlayCount ?: 0),
                "oldestShowTimestamp" to (oldestShow?.firstPlayedTimestamp ?: 0L),
                "newestShowTimestamp" to (newestShow?.lastPlayedTimestamp ?: 0L),
                "meaningfulPlayThresholdMs" to MEANINGFUL_PLAY_DURATION_MS,
                "meaningfulPlayPercentage" to MEANINGFUL_PLAY_PERCENTAGE,
                "currentTrackingShowId" to (currentTrackShowId ?: "none"),
                "hasRecordedCurrentTrack" to hasRecordedCurrentTrack
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get recent shows stats", e)
            mapOf("error" to (e.message ?: "Unknown error"))
        }
    }
}