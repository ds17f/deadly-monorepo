package com.grateful.deadly.core.api.connect

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface ConnectService {
    val deviceId: String
    val connectionState: StateFlow<ConnectConnectionState>
    val devices: StateFlow<List<ConnectDevice>>
    val userState: StateFlow<UserPlaybackState?>
    val playbackEvents: SharedFlow<ConnectPlaybackEvent>
    fun connect()
    fun disconnect()
}
