package com.grateful.deadly.core.connect

import com.grateful.deadly.core.model.ConnectDevice
import com.grateful.deadly.core.model.ConnectSessionTrack
import com.grateful.deadly.core.model.ConnectState
import kotlinx.coroutines.flow.StateFlow

interface ConnectService {
    val devices: StateFlow<List<ConnectDevice>>
    val connectState: StateFlow<ConnectState?>
    val isConnected: StateFlow<Boolean>
    val pendingCommand: StateFlow<String?>
    val isActiveDevice: StateFlow<Boolean>

    fun startIfAuthenticated()
    fun stop()

    fun sendLoad(
        showId: String,
        recordingId: String,
        tracks: List<ConnectSessionTrack>,
        trackIndex: Int,
        positionMs: Int,
        durationMs: Int,
        date: String? = null,
        venue: String? = null,
        location: String? = null,
        autoplay: Boolean = true,
    )

    fun sendPlay()
    fun sendPause()
}
