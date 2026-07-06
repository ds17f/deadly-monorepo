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

// ADR-0010 §7: shared end-of-show countdown. deadline is an absolute server
// timestamp (ms); Double for the same reason as positionTs — the server may
// send a fractional value and an Int/Long parse would drop the whole state.
@Serializable
data class PendingAdvance(
    val showId: String,
    val deadline: Double,
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
    // Bumped only by an explicit server-side seek (never by position reports).
    // The active device seeks when this advances, not on positionMs deltas — so
    // self-echoes can't cause skips and small remote seeks still land. Additive/
    // optional; defaults to 0 against a server that doesn't send it. See ADR-0017.
    val seekNonce: Int = 0,
    // Bumped only by an explicit server-side next/prev (never by a load or a
    // position report). The active device follows a remote track change when this
    // advances AND it didn't issue it — keying on intent, not trackIndex
    // coincidence. Generalizes seekNonce to track changes (ADR-0019). Additive/
    // optional; defaults to 0 against a server that doesn't send it.
    val trackNonce: Int = 0,
    val playing: Boolean = false,
    val activeDeviceId: String? = null,
    val activeDeviceName: String? = null,
    val activeDeviceType: String? = null,
    val date: String? = null,
    val venue: String? = null,
    val location: String? = null,
    // ADR-0010 §7: shared end-of-show countdown. Optional/additive.
    val pendingAdvance: PendingAdvance? = null,
)
