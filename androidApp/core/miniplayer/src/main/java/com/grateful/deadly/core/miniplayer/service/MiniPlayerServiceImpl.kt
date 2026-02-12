package com.grateful.deadly.core.miniplayer.service

import android.util.Log
import com.grateful.deadly.core.api.miniplayer.MiniPlayerService
import com.grateful.deadly.core.media.repository.MediaControllerRepository
import com.grateful.deadly.core.media.state.MediaControllerStateUtil
import com.grateful.deadly.core.model.CurrentTrackInfo
import com.grateful.deadly.core.model.PlaybackStatus
import com.grateful.deadly.core.model.QueueInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MiniPlayer Service Implementation
 * 
 * Direct delegation to MediaControllerRepository for core playback state and commands.
 * Uses MediaControllerStateUtil for rich CurrentTrackInfo StateFlow.
 * 
 * ARCHITECTURE: Simple, direct dependencies without unnecessary abstraction layers.
 */
@Singleton
class MiniPlayerServiceImpl @Inject constructor(
    private val mediaControllerRepository: MediaControllerRepository,
    private val mediaControllerStateUtil: MediaControllerStateUtil
) : MiniPlayerService {
    
    companion object {
        private const val TAG = "MiniPlayerServiceImpl"
    }
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    // Direct delegation to MediaControllerRepository
    override val isPlaying: StateFlow<Boolean> = mediaControllerRepository.isPlaying
    override val playbackStatus: StateFlow<PlaybackStatus> = mediaControllerRepository.playbackStatus
    override val currentTrackInfo: StateFlow<CurrentTrackInfo?> = 
        mediaControllerStateUtil.createCurrentTrackInfoStateFlow(serviceScope)
    override val queueInfo: StateFlow<QueueInfo> = 
        mediaControllerStateUtil.createQueueInfoStateFlow(serviceScope)
    
    /**
     * Direct command delegation to MediaControllerRepository
     */
    override suspend fun togglePlayPause() {
        Log.d(TAG, "🕒🎵 [MINI] MiniPlayer togglePlayPause requested at ${System.currentTimeMillis()}")
        mediaControllerRepository.togglePlayPause()
    }
}