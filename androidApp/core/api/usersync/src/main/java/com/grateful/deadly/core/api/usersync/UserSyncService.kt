package com.grateful.deadly.core.api.usersync

interface UserSyncService {
    suspend fun pullFullBackup(): Result<SyncBackupV3>
    suspend fun putFavoriteShow(show: SyncFavoriteShowV3): Result<Unit>
    suspend fun deleteFavoriteShow(showId: String): Result<Unit>
    suspend fun putFavoriteSong(song: SyncFavoriteTrackV3): Result<Unit>
    /** Delete by natural key (showId + trackTitle). Server resolves the row;
     *  mobile clients don't know the autoincrement id. */
    suspend fun deleteFavoriteSong(showId: String, trackTitle: String): Result<Unit>
    /** Announce a play of [showId]. The server stamps last_played_at and
     *  bumps the play count; no client timestamp is sent. */
    suspend fun putRecent(showId: String): Result<Unit>
    /** Upsert a review (rating/notes/qualities + player tags travel together;
     *  the server replaces all tags for the show). */
    suspend fun putReview(review: SyncReviewV3): Result<Unit>
    /** Delete a review (and its player tags) by showId. */
    suspend fun deleteReview(showId: String): Result<Unit>
    /** Upsert the preferred recording for a show. */
    suspend fun putRecordingPref(showId: String, recordingId: String): Result<Unit>
    /** Clear the preferred recording for a show (tombstone). */
    suspend fun deleteRecordingPref(showId: String): Result<Unit>
    /** Add / update a Show Queue (backlog) entry. */
    suspend fun putBacklogItem(item: SyncBacklogItemV3): Result<Unit>
    /** Remove (pop) a Show Queue entry (tombstone). */
    suspend fun deleteBacklogItem(showId: String): Result<Unit>
    /** Rewrite the Show Queue order. */
    suspend fun reorderBacklog(showIds: List<String>): Result<Unit>
}
