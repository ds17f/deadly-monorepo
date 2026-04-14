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
    val pendingTransfer: StateFlow<String?>
    val activeDeviceVolume: StateFlow<Int>
    val showVolumeUI: StateFlow<Boolean>

    fun startIfAuthenticated()
    fun stop()
    fun handleNetworkRestored()

    fun sendStop()
    fun sendTransfer(targetDeviceId: String)
    fun sendPosition(positionMs: Int)

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
    fun sendSeek(trackIndex: Int, positionMs: Int, durationMs: Int)
    fun sendNext()
    fun sendPrev()

    fun sendVolume(volume: Int)
    fun sendVolumeReport(volume: Int)

    /**
     * Hardware volume key handler. If playback is on a remote device, steps the
     * remote volume by [delta] (clamped 0..100), shows the ConnectSheet, and
     * returns true so the caller can suppress the local system volume change.
     * Returns false when no remote session is active — caller should let the
     * OS handle the key normally.
     */
    fun handleHardwareVolumeKey(delta: Int): Boolean

    fun triggerShowVolumeUI()
    fun consumeShowVolumeUI()
}
