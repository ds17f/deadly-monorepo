package com.grateful.deadly.core.model

/**
 * A single entry in the persistent show queue (ADR-0010).
 *
 * The queue holds *upcoming* shows only — the currently-playing show is a
 * separate pointer. The unit is always a whole show; track/playlist queuing is
 * deferred (ROADMAP §8).
 *
 * @param id Stable row id (used for reorder/remove).
 * @param showId The show to play.
 * @param recordingId Specific recording, or null to resolve the recommended
 *   recording at play time.
 * @param resumeTrackIndex Non-null only for a re-queued interrupted show: the
 *   track to resume from. Normal entries are null (shows start from the top).
 * @param resumePositionMs Paired with [resumeTrackIndex].
 */
data class QueuedShow(
    val id: Long,
    val showId: String,
    val recordingId: String? = null,
    val resumeTrackIndex: Int? = null,
    val resumePositionMs: Long? = null
)
