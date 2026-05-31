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
}
