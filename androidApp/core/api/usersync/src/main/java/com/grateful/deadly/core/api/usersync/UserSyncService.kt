package com.grateful.deadly.core.api.usersync

interface UserSyncService {
    suspend fun pullFullBackup(): Result<SyncBackupV3>
}
