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
    val version: Int,
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
