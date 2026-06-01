package com.grateful.deadly.core.api.usersync

/**
 * Pulls the V3 backup from the server and merges it into the local DB
 * using last-writer-wins by updated_at.
 *
 * Scope: favorite shows only in this pass (mirrors the push side, which
 * also only covers favorite shows). See PLANS/mobile-server-sync.md.
 *
 * Apply writes go through the DAO directly — they MUST NOT call
 * [FavoritesPushService.enqueueAndPush], or every pull would re-push
 * what we just imported.
 */
interface UserSyncApplyService {
    /** Pull + merge. Returns a summary suitable for logging or display. */
    suspend fun pullAndApply(): Result<ApplyResult>
}

data class ApplyResult(
    val favoriteShowsScanned: Int,
    val favoriteShowsApplied: Int,
    val favoriteShowsSkippedLocalNewer: Int,
    val favoriteShowsSkippedMissingShow: Int,
    val favoriteSongsScanned: Int = 0,
    val favoriteSongsApplied: Int = 0,
    val favoriteSongsSkippedLocalNewer: Int = 0,
    val favoriteSongsSkippedMissingShow: Int = 0,
    val reviewsScanned: Int = 0,
    val reviewsApplied: Int = 0,
    val reviewsSkippedLocalNewer: Int = 0,
    val reviewsSkippedMissingShow: Int = 0,
)
