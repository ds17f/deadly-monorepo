package com.grateful.deadly.core.api.connect

import kotlinx.serialization.Serializable

enum class ConnectConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
}

@Serializable
data class ConnectDevice(
    val deviceId: String,
    val type: String,
    val name: String,
    val capabilities: List<String> = emptyList(),
)
