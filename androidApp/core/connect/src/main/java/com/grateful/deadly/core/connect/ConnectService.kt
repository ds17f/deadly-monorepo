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

    /**
     * Server-clock minus local-clock, in milliseconds. Add to
     * [System.currentTimeMillis] to approximate the server's wall-clock when
     * comparing against [ConnectState.positionTs]. Defaults to 0 until the
     * first time_sync round-trip completes. See
     * docs/connect-v2-architecture.md "Clock Sync".
     */
    val serverTimeOffsetMs: StateFlow<Long>

    /**
     * Whether the server's global Connect kill switch is ON (ADR-0018). Drives
     * UI discovery: when false the Connect icon renders greyed/"unavailable" and
     * no session can form. Seeded from the last cached value (default false on a
     * fresh install — fail safe to "not offered"), refreshed by
     * [refreshServerConnectEnabled], and forced false when a live session is
     * closed with code 4005.
     */
    val serverConnectEnabled: StateFlow<Boolean>

    fun startIfAuthenticated()

    /**
     * Fetch the global Connect flag (`GET /api/connect/enabled`) and update
     * [serverConnectEnabled]. Short-timeout, best-effort: on failure the last
     * cached value is kept. Call on startup and on foreground (ADR-0018).
     */
    fun refreshServerConnectEnabled()

    fun stop()

    /**
     * Toggle the per-device Connect kill switch (Settings). Persists the choice
     * and either tears down or (re)starts the session. When turning off while
     * this device is the active player, sends an explicit `stop` first so
     * followers pause cleanly before the socket closes.
     */
    fun setEnabled(enabled: Boolean)
    fun handleNetworkRestored()

    fun sendStop()

    // ADR-0010 §7: cross-device end-of-show countdown.
    fun sendAnnounceNext(showId: String, deadline: Double)
    fun sendCancelAdvance()
    fun sendAdvanceNow()

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
