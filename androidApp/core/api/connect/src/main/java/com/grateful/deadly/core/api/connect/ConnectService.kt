package com.grateful.deadly.core.api.connect

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface ConnectService {
    val deviceId: String
    val connectionState: StateFlow<ConnectConnectionState>
    val devices: StateFlow<List<ConnectDevice>>
    val userState: StateFlow<UserPlaybackState?>
    val config: StateFlow<ConnectConfig>
    val playbackEvents: SharedFlow<ConnectPlaybackEvent>
    /** True after the first user_state has been received since connecting. */
    val receivedInitialState: StateFlow<Boolean>
    fun connect()
    fun disconnect()
    fun sendSessionUpdate(state: OutgoingPlaybackState)
    fun sendSessionPlayOn(targetDeviceId: String, state: OutgoingPlaybackState)
}
