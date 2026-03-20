package com.grateful.deadly.core.api.connect

import kotlinx.coroutines.flow.SharedFlow
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

@Serializable
data class UserPlaybackState(
    val showId: String? = null,
    val recordingId: String? = null,
    val trackIndex: Int = 0,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val trackTitle: String? = null,
    val date: String? = null,
    val venue: String? = null,
    val location: String? = null,
    val activeDeviceId: String? = null,
    val activeDeviceName: String? = null,
    val activeDeviceType: String? = null,
    val isPlaying: Boolean = false,
    val updatedAt: Long = 0,
)

@Serializable
data class IncomingPlaybackState(
    val showId: String,
    val recordingId: String,
    val trackIndex: Int = 0,
    val positionMs: Long = 0,
    val durationMs: Long? = null,
    val trackTitle: String? = null,
    val status: String? = null,
    val date: String? = null,
    val venue: String? = null,
    val location: String? = null,
)

@Serializable
data class PlaybackCommand(
    val action: String,
    val seekMs: Long? = null,
)

sealed class ConnectPlaybackEvent {
    data class PlayOn(val state: IncomingPlaybackState) : ConnectPlaybackEvent()
    data class Command(val command: PlaybackCommand) : ConnectPlaybackEvent()
    data object Stop : ConnectPlaybackEvent()
}
