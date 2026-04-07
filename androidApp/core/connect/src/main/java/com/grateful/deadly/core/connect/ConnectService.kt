package com.grateful.deadly.core.connect

import com.grateful.deadly.core.model.ConnectDevice
import com.grateful.deadly.core.model.ConnectState
import kotlinx.coroutines.flow.StateFlow

interface ConnectService {
    val devices: StateFlow<List<ConnectDevice>>
    val connectState: StateFlow<ConnectState?>
    val isConnected: StateFlow<Boolean>

    fun startIfAuthenticated()
    fun stop()
}
