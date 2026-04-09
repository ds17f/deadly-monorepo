package com.grateful.deadly.core.miniplayer.service

import android.util.Log
import com.grateful.deadly.core.api.miniplayer.MiniPlayerService
import com.grateful.deadly.core.connect.ConnectService
import com.grateful.deadly.core.media.repository.MediaControllerRepository
import com.grateful.deadly.core.media.state.MediaControllerStateUtil
import com.grateful.deadly.core.model.CurrentTrackInfo
import com.grateful.deadly.core.model.PlaybackStatus
import com.grateful.deadly.core.model.QueueInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    private val mediaControllerStateUtil: MediaControllerStateUtil,
    private val connectService: ConnectService,
) : MiniPlayerService {
    
    companion object {
        private const val TAG = "MiniPlayerServiceImpl"
    }
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    // When remote controlling (another device is active), reflect server playing state.
    // Otherwise use local MediaController state.
    override val isPlaying: StateFlow<Boolean> = combine(
        mediaControllerRepository.isPlaying,
        connectService.connectState,
        connectService.isActiveDevice,
    ) { localPlaying, state, isActive ->
        if (state != null && state.showId != null && !isActive && state.activeDeviceId != null) {
            state.playing
        } else {
            localPlaying
        }
    }.stateIn(serviceScope, SharingStarted.Eagerly, false)
    override val playbackStatus: StateFlow<PlaybackStatus> = mediaControllerRepository.playbackStatus
    override val currentTrackInfo: StateFlow<CurrentTrackInfo?> = 
        mediaControllerStateUtil.createCurrentTrackInfoStateFlow(serviceScope)
    override val queueInfo: StateFlow<QueueInfo> = 
        mediaControllerStateUtil.createQueueInfoStateFlow(serviceScope)
    
    /**
     * Direct command delegation to MediaControllerRepository
     */
    override suspend fun togglePlayPause() {
        val state = connectService.connectState.value
        val isActive = connectService.isActiveDevice.value
        val serverPlaying = state?.playing ?: false
        val localPlaying = mediaControllerRepository.isPlaying.value
        val activeDeviceId = state?.activeDeviceId

        val isRemoteControlling = state?.let {
            it.activeDeviceId != null && !isActive
        } ?: false

        Log.d(TAG, "togglePlayPause: isRemote=$isRemoteControlling isActive=$isActive " +
            "serverPlaying=$serverPlaying localPlaying=$localPlaying " +
            "activeDevice=$activeDeviceId connected=${connectService.isConnected.value}")

        if (isRemoteControlling) {
            // Remote control: send command only, wait for server to confirm
            if (serverPlaying) {
                Log.d(TAG, "togglePlayPause: remote -> sendPause")
                connectService.sendPause()
            } else {
                Log.d(TAG, "togglePlayPause: remote -> sendPlay")
                connectService.sendPlay()
            }
        } else {
            // Active device or no active device: drive local audio optimistically + send command
            val wasPlaying = mediaControllerRepository.isPlaying.value
            Log.d(TAG, "togglePlayPause: local toggle (wasPlaying=$wasPlaying)")
            mediaControllerRepository.togglePlayPause()
            if (wasPlaying) {
                Log.d(TAG, "togglePlayPause: optimistic -> sendPause")
                connectService.sendPause()
            } else {
                Log.d(TAG, "togglePlayPause: optimistic -> sendPlay")
                connectService.sendPlay()
            }
        }
    }
}