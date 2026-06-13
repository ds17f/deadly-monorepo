package com.grateful.deadly.core.notifications

interface NotificationApiService {
    /**
     * Fetch messages newer than [since] (the client's cursor). Pass 0 for a
     * fresh client to get the capped cold-start batch (active messages only).
     * The endpoint is public — no auth header is sent.
     */
    suspend fun fetch(since: Long): Result<NotificationFetchResult>

    // ── Per-user read/dismiss overlay (ADR-0015), authed ────────────────
    // These ride the authed /api/user path (an Authorization header), unlike
    // the public feed above. They fail with "Not signed in" when there's no
    // token; callers gate on sign-in first.

    /** Pull the signed-in user's full read/dismiss overlay. */
    suspend fun pullState(): Result<List<NotificationStateRow>>

    /** Granular push — backs markRead (seenAt) / archive (dismissedAt). */
    suspend fun pushState(id: Long, seenAt: Long?, dismissedAt: Long?): Result<Unit>

    /**
     * Bulk push — backs markAllRead / archiveAll. `ids = null` targets every
     * currently-active message server-side.
     */
    suspend fun pushStateBulk(seenAt: Long?, dismissedAt: Long?, ids: List<Long>?): Result<Unit>
}
