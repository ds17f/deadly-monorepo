package com.grateful.deadly.core.notifications

interface NotificationApiService {
    /**
     * Fetch messages newer than [since] (the client's cursor). Pass 0 for a
     * fresh client to get the capped cold-start batch (active messages only).
     * The endpoint is public — no auth header is sent.
     */
    suspend fun fetch(since: Long): Result<NotificationFetchResult>
}
