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
data class SessionTrack(
    val title: String,
    val duration: Double,  // seconds
)

@Serializable
data class OutgoingPlaybackState(
    val showId: String,
    val recordingId: String,
    val trackIndex: Int,
    val positionMs: Long,
    val durationMs: Long,
    val trackTitle: String? = null,
    val status: String,
    val date: String? = null,
    val venue: String? = null,
    val location: String? = null,
    val tracks: List<SessionTrack>? = null,
)

@Serializable
data class ConnectConfig(
    val positionUpdateIntervalMs: Long = 5000,
    val seekDivergenceThresholdMs: Long = 2000,
    val redirectMaxAgeSec: Long = 120,
)

sealed class ConnectPlaybackEvent {
    data class PlayOn(val state: IncomingPlaybackState, val relayedAt: Long? = null) : ConnectPlaybackEvent()
    data object Stop : ConnectPlaybackEvent()
}
