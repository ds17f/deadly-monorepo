package com.grateful.deadly.core.api.connect

import kotlinx.coroutines.flow.StateFlow

/**
 * Connect service for multi-device playback coordination.
 *
 * Manages a WebSocket connection to the server's `/ws/connect` endpoint,
 * registers this device, and relays playback state between devices.
 */
interface ConnectService {

    /** This device's unique identifier. */
    val deviceId: String

    /** Current WebSocket connection state. */
    val connectionState: StateFlow<ConnectConnectionState>

    /** All devices registered for the current user. */
    val devices: StateFlow<List<ConnectDevice>>

    /** The user's playback state (may be parked with no active device). */
    val userState: StateFlow<UserPlaybackState?>

    /** Connect the WebSocket (called when signed in). */
    fun connect()

    /** Disconnect the WebSocket (called on sign out). */
    fun disconnect()

    /** Send a session_update with our current playback state. */
    fun announcePlayback(state: ConnectPlaybackState)

    /** Send a position_update. */
    fun sendPositionUpdate(state: ConnectPlaybackState)

    /** Claim the active session for this device. */
    fun claimSession()

    /** Tell another device to start playing the given state. */
    fun playOnDevice(targetDeviceId: String, state: ConnectPlaybackState)

    /** Send a remote command to another device. */
    fun sendCommand(targetDeviceId: String, command: PlaybackCommand)

    /** Clear all playback state. */
    fun clearState()
}
