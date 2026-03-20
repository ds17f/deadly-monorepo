package com.grateful.deadly.core.api.connect

import kotlinx.coroutines.flow.StateFlow

interface ConnectService {
    val deviceId: String
    val connectionState: StateFlow<ConnectConnectionState>
    val devices: StateFlow<List<ConnectDevice>>
    fun connect()
    fun disconnect()
}
