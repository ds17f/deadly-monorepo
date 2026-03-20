package com.grateful.deadly.core.api.connect

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Connection lifecycle state.
 */
enum class ConnectConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
}

/**
 * A device registered with the Connect system.
 */
@Serializable
data class ConnectDevice(
    val deviceId: String,
    val type: String,      // "ios" | "android" | "web"
    val name: String,
    val capabilities: List<String> = emptyList(),
)

/**
 * Playback state sent over the wire.
 * Mirrors api/src/connect/types.ts PlaybackState.
 */
@Serializable
data class ConnectPlaybackState(
    val showId: String,
    val recordingId: String,
    val trackIndex: Int,
    val positionMs: Long,
    val durationMs: Long? = null,
    val trackTitle: String? = null,
    val status: String,  // "playing" | "paused" | "stopped"
    val date: String? = null,
    val venue: String? = null,
    val location: String? = null,
)

/**
 * Per-user playback state including active device info.
 * Mirrors api/src/connect/types.ts UserPlaybackState.
 */
@Serializable
data class UserPlaybackState(
    val showId: String,
    val recordingId: String,
    val trackIndex: Int,
    val positionMs: Long,
    val durationMs: Long,
    val trackTitle: String? = null,
    val date: String? = null,
    val venue: String? = null,
    val location: String? = null,
    val activeDeviceId: String? = null,
    val activeDeviceName: String? = null,
    val activeDeviceType: String? = null,
    val isPlaying: Boolean,
    val updatedAt: Long,
)

/**
 * Remote playback command.
 */
@Serializable
data class PlaybackCommand(
    val action: String,      // "play" | "pause" | "stop" | "next" | "prev" | "seek"
    val seekMs: Long? = null,
)
