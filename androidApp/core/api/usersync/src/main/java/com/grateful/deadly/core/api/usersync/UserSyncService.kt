package com.grateful.deadly.core.api.usersync

interface UserSyncService {
    suspend fun pullFullBackup(): Result<SyncBackupV3>
    suspend fun putFavoriteShow(show: SyncFavoriteShowV3): Result<Unit>
    suspend fun deleteFavoriteShow(showId: String): Result<Unit>
}
