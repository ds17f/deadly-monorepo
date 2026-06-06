package com.grateful.deadly.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ConnectDevice(
    val deviceId: String,
    val deviceType: String,
    val deviceName: String,
)

@Serializable
data class ConnectSessionTrack(
    val title: String,
    val durationMs: Int,
)

@Serializable
data class ConnectState(
    // Long, not Int: the server seeds version from wall-clock ms (Date.now())
    // so it stays monotonic across restarts — that exceeds Int32's range and
    // overflows the JSON parser if typed Int (drops every state silently).
    val version: Long,
    // Server boot id (wall-clock ms) — a change means the server restarted (see
    // api ConnectState.epoch). Long, not Int, for the same overflow reason as version.
    val epoch: Long = 0L,
    val showId: String? = null,
    val recordingId: String? = null,
    val tracks: List<ConnectSessionTrack> = emptyList(),
    val trackIndex: Int = 0,
    val positionMs: Int = 0,
    val positionTs: Double = 0.0,
    val durationMs: Int = 0,
    val playing: Boolean = false,
    val activeDeviceId: String? = null,
    val activeDeviceName: String? = null,
    val activeDeviceType: String? = null,
    val date: String? = null,
    val venue: String? = null,
    val location: String? = null,
)
