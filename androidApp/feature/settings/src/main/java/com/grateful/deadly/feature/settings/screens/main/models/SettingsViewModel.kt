package com.grateful.deadly.feature.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grateful.deadly.core.api.auth.AuthService
import com.grateful.deadly.core.api.auth.AuthState
import com.grateful.deadly.core.api.usersync.FavoritesPushService
import com.grateful.deadly.core.api.usersync.UserSyncService
import com.grateful.deadly.core.database.AnalyticsService
import com.grateful.deadly.core.database.AppPreferences
import com.grateful.deadly.core.database.AppReviewManager
import com.grateful.deadly.core.database.migration.MigrationImportService
import com.grateful.deadly.core.database.migration.MigrationResult
import com.grateful.deadly.core.database.service.BackupImportExportService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * SettingsViewModel - Business logic for Settings screen
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val migrationImportService: MigrationImportService,
    private val backupImportExportService: BackupImportExportService,
    private val appPreferences: AppPreferences,
    private val authService: AuthService,
    private val userSyncService: UserSyncService,
    private val favoritesPushService: FavoritesPushService,
    private val analyticsService: AnalyticsService,
    private val appReviewManager: AppReviewManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    sealed class MigrationImportState {
        data object Idle : MigrationImportState()
        data object Importing : MigrationImportState()
        data class Success(val result: MigrationResult) : MigrationImportState()
        data class Error(val message: String) : MigrationImportState()
    }

    private val _migrationImportState = MutableStateFlow<MigrationImportState>(MigrationImportState.Idle)
    val migrationImportState: StateFlow<MigrationImportState> = _migrationImportState

    val includeShowsWithoutRecordings: StateFlow<Boolean> = appPreferences.includeShowsWithoutRecordings

    fun toggleIncludeShowsWithoutRecordings() {
        val newValue = !appPreferences.includeShowsWithoutRecordings.value
        appPreferences.setIncludeShowsWithoutRecordings(newValue)
        analyticsService.track("feature_use", mapOf(
            "feature" to "toggle_shows_without_recordings",
            "category" to "preference",
            "enabled" to newValue,
        ))
    }

    val analyticsEnabled: StateFlow<Boolean> = appPreferences.analyticsEnabled

    fun toggleAnalyticsEnabled() {
        val newValue = !appPreferences.analyticsEnabled.value
        // Track the toggle event before the flag changes
        val event = if (newValue) "analytics_opt_in" else "analytics_opt_out"
        analyticsService.track("feature_use", mapOf("feature" to event, "category" to "account"))
        analyticsService.flush()
        appPreferences.setAnalyticsEnabled(newValue)
    }

    val sourceBadgeStyle: StateFlow<String> = appPreferences.sourceBadgeStyle

    fun setSourceBadgeStyle(value: String) {
        appPreferences.setSourceBadgeStyle(value)
        analyticsService.track("feature_use", mapOf(
            "feature" to "set_source_badge_style",
            "category" to "preference",
            "value" to value,
        ))
    }

    val playerControlsStyle: StateFlow<String> = appPreferences.playerControlsStyle

    fun setPlayerControlsStyle(value: String) {
        appPreferences.setPlayerControlsStyle(value)
        analyticsService.track("feature_use", mapOf(
            "feature" to "set_player_controls_style",
            "category" to "preference",
            "value" to value,
        ))
    }

    val homeTrendingWindow: StateFlow<String> = appPreferences.homeTrendingWindow

    fun setHomeTrendingWindow(value: String) {
        appPreferences.setHomeTrendingWindow(value)
        analyticsService.track("feature_use", mapOf(
            "feature" to "set_home_trending_window",
            "category" to "preference",
            "value" to value,
        ))
    }

    val homeTrendingAboveToday: StateFlow<Boolean> = appPreferences.homeTrendingAboveToday

    fun toggleHomeTrendingAboveToday() {
        val newValue = !appPreferences.homeTrendingAboveToday.value
        appPreferences.setHomeTrendingAboveToday(newValue)
        analyticsService.track("feature_use", mapOf(
            "feature" to "set_home_trending_above_today",
            "category" to "preference",
            "value" to newValue.toString(),
        ))
    }

    val homeTrendingIncludeAnniversaries: StateFlow<Boolean> =
        appPreferences.homeTrendingIncludeAnniversaries

    fun toggleHomeTrendingIncludeAnniversaries() {
        val newValue = !appPreferences.homeTrendingIncludeAnniversaries.value
        appPreferences.setHomeTrendingIncludeAnniversaries(newValue)
        analyticsService.track("feature_use", mapOf(
            "feature" to "set_home_trending_include_anniversaries",
            "category" to "preference",
            "value" to newValue.toString(),
        ))
    }

    val homePopularEnabled: StateFlow<Boolean> = appPreferences.homePopularEnabled

    fun toggleHomePopularEnabled() {
        val newValue = !appPreferences.homePopularEnabled.value
        appPreferences.setHomePopularEnabled(newValue)
        analyticsService.track("feature_use", mapOf(
            "feature" to "set_home_popular_enabled",
            "category" to "preference",
            "value" to newValue.toString(),
        ))
    }

    val homePopularCardSize: StateFlow<String> = appPreferences.homePopularCardSize

    fun setHomePopularCardSize(value: String) {
        appPreferences.setHomePopularCardSize(value)
        analyticsService.track("feature_use", mapOf(
            "feature" to "set_home_popular_card_size",
            "category" to "preference",
            "value" to value,
        ))
    }

    val homePopularDecade: StateFlow<String> = appPreferences.homePopularDecade

    fun setHomePopularDecade(value: String) {
        appPreferences.setHomePopularDecade(value)
        analyticsService.track("feature_use", mapOf(
            "feature" to "set_home_popular_decade",
            "category" to "preference",
            "value" to value,
        ))
    }

    val homeRecentRows: StateFlow<Int> = appPreferences.homeRecentRows

    fun setHomeRecentRows(value: Int) {
        appPreferences.setHomeRecentRows(value)
        analyticsService.track("feature_use", mapOf(
            "feature" to "set_home_recent_rows",
            "category" to "preference",
            "value" to value.toString(),
        ))
    }

    val homeTrendingCardSize: StateFlow<String> = appPreferences.homeTrendingCardSize
    val homeTodayCardSize: StateFlow<String> = appPreferences.homeTodayCardSize
    val homeCollectionsCardSize: StateFlow<String> = appPreferences.homeCollectionsCardSize

    fun setHomeTrendingCardSize(value: String) {
        appPreferences.setHomeTrendingCardSize(value)
        analyticsService.track("feature_use", mapOf(
            "feature" to "set_home_trending_card_size",
            "category" to "preference",
            "value" to value,
        ))
    }

    fun setHomeTodayCardSize(value: String) {
        appPreferences.setHomeTodayCardSize(value)
        analyticsService.track("feature_use", mapOf(
            "feature" to "set_home_today_card_size",
            "category" to "preference",
            "value" to value,
        ))
    }

    fun setHomeCollectionsCardSize(value: String) {
        appPreferences.setHomeCollectionsCardSize(value)
        analyticsService.track("feature_use", mapOf(
            "feature" to "set_home_collections_card_size",
            "category" to "preference",
            "value" to value,
        ))
    }

    fun resetHomePreferences() {
        appPreferences.resetHomePreferences()
        analyticsService.track("feature_use", mapOf(
            "feature" to "reset_home_preferences",
            "category" to "preference",
        ))
    }

    val authState: StateFlow<AuthState> = authService.authState

    val useBetaMode: StateFlow<Boolean> = appPreferences.useBetaModeFlow

    val serverEnvironment: StateFlow<String> = appPreferences.serverEnvironment
    val customServerUrl: StateFlow<String> = appPreferences.customServerUrl
    val customDevEmail: StateFlow<String> = appPreferences.customDevEmail

    fun setServerEnvironment(value: String) {
        appPreferences.setServerEnvironment(value)
        val impl = authService as? com.grateful.deadly.core.auth.AuthServiceImpl
        impl?.onEnvironmentChanged()
        if (value == "custom") {
            viewModelScope.launch { impl?.fetchDevToken() }
        }
    }

    fun setCustomServerUrl(value: String) {
        appPreferences.setCustomServerUrl(value)
    }

    fun setCustomDevEmail(value: String) {
        appPreferences.setCustomDevEmail(value)
    }

    fun fetchDevToken() {
        val impl = authService as? com.grateful.deadly.core.auth.AuthServiceImpl ?: run {
            Log.w(TAG, "fetchDevToken: authService is not AuthServiceImpl")
            return
        }
        viewModelScope.launch { impl.fetchDevToken() }
    }

    fun toggleUseBetaMode() {
        val newValue = !appPreferences.useBetaMode
        appPreferences.setServerEnvironment(if (newValue) "beta" else "prod")
        (authService as? com.grateful.deadly.core.auth.AuthServiceImpl)?.onEnvironmentChanged()
    }

    val forceOnline: StateFlow<Boolean> = appPreferences.forceOnline

    fun toggleForceOnline() {
        appPreferences.setForceOnline(!appPreferences.forceOnline.value)
    }

    fun triggerReviewPrompt() {
        appReviewManager.forcePrompt()
    }

    fun flushAnalytics(onComplete: (Boolean, Int, String?) -> Unit) {
        analyticsService.flushNow(onComplete)
    }

    val developerModeUnlocked: StateFlow<Boolean> = appPreferences.developerModeUnlocked

    fun unlockDeveloperMode() {
        appPreferences.setDeveloperModeUnlocked(true)
    }

    fun lockDeveloperMode() {
        appPreferences.setDeveloperModeUnlocked(false)
    }

    private val _syncLog = MutableStateFlow<List<String>>(emptyList())
    val syncLog: StateFlow<List<String>> = _syncLog

    private val _syncInFlight = MutableStateFlow(false)
    val syncInFlight: StateFlow<Boolean> = _syncInFlight

    fun pullUserSync() {
        if (_syncInFlight.value) return
        _syncInFlight.value = true
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            val result = userSyncService.pullFullBackup()
            val lines = result.fold(
                onSuccess = { backup ->
                    val elapsed = System.currentTimeMillis() - started
                    listOf(
                        "[$ts] GET /api/user/sync OK in ${elapsed}ms",
                        "  version=${backup.version} app=${backup.app}",
                        "  favorites.shows=${backup.favorites.shows.size}",
                        "  favorites.tracks=${backup.favorites.tracks.size}",
                        "  reviews=${backup.reviews.size}",
                        "  recordingPreferences=${backup.recordingPreferences.size}",
                        "  recentShows=${backup.recentShows?.size ?: 0}",
                        "  playbackPosition=${if (backup.playbackPosition == null) "none" else "present"}",
                        "  settings=${if (backup.settings == null) "none" else "present"}",
                    )
                },
                onFailure = { e ->
                    listOf("[$ts] FAILED: ${e.message ?: e.javaClass.simpleName}")
                },
            )
            _syncLog.value = lines + _syncLog.value
            _syncInFlight.value = false
        }
    }

    fun clearSyncLog() {
        _syncLog.value = emptyList()
    }

    private val _favoritesPushPending = MutableStateFlow(0)
    val favoritesPushPending: StateFlow<Int> = _favoritesPushPending

    init {
        viewModelScope.launch {
            _favoritesPushPending.value = favoritesPushService.pendingCount()
        }
    }

    fun pushPendingFavorites() {
        if (_syncInFlight.value) return
        _syncInFlight.value = true
        viewModelScope.launch {
            val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            val results = favoritesPushService.flushPending()
            val lines = buildList {
                add("[$ts] Push: ${results.size} entries")
                if (results.isEmpty()) add("  (outbox empty)")
                for (r in results) {
                    val status = if (r.success) "OK" else "FAIL"
                    val tail = r.error?.let { " ($it)" } ?: ""
                    add("  ${r.operation} ${r.refId} → $status$tail")
                }
            }
            _syncLog.value = lines + _syncLog.value
            _favoritesPushPending.value = favoritesPushService.pendingCount()
            _syncInFlight.value = false
        }
    }

    fun pushAllLocalData() {
        if (_syncInFlight.value) return
        _syncInFlight.value = true
        viewModelScope.launch {
            val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            val results = favoritesPushService.enqueueAllLocalAndFlush()
            val lines = buildList {
                add("[$ts] Push all: ${results.size} entries")
                if (results.isEmpty()) add("  (nothing local)")
                for (r in results) {
                    val status = if (r.success) "OK" else "FAIL"
                    val tail = r.error?.let { " ($it)" } ?: ""
                    add("  [${r.kind}] ${r.operation} ${r.refId} → $status$tail")
                }
            }
            _syncLog.value = lines + _syncLog.value
            _favoritesPushPending.value = favoritesPushService.pendingCount()
            _syncInFlight.value = false
        }
    }

    fun signInWithGoogle(activity: android.app.Activity, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                authService.signInWithGoogle(activity)
            } catch (e: Exception) {
                Log.e(TAG, "Google sign-in failed", e)
                onError(e.message ?: "Sign-in failed")
            }
        }
    }

    fun signOut() {
        analyticsService.track("feature_use", mapOf("feature" to "sign_out", "category" to "account"))
        viewModelScope.launch {
            authService.signOut()
        }
    }

    fun onImportMigration(uri: Uri) {
        viewModelScope.launch {
            try {
                _migrationImportState.value = MigrationImportState.Importing
                val jsonString = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().readText()
                    } ?: throw IllegalStateException("Could not open file")
                }
                val result = withContext(Dispatchers.IO) {
                    migrationImportService.importFromJson(jsonString)
                }
                _migrationImportState.value = MigrationImportState.Success(result)
            } catch (e: Exception) {
                Log.e(TAG, "Migration import failed", e)
                _migrationImportState.value = MigrationImportState.Error(e.message ?: "Import failed")
            }
        }
    }

    fun onDismissMigrationResult() {
        _migrationImportState.value = MigrationImportState.Idle
    }
    
    /**
     * Delete the data.zip file from the files directory
     * 
     * @param onComplete Callback with success/failure result
     */
    fun onDeleteDataZip(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    val dataZipFile = File(context.filesDir, "data.zip")
                    if (dataZipFile.exists()) {
                        val deleted = dataZipFile.delete()
                        Log.d(TAG, "onDeleteDataZip: data.zip deletion result: $deleted")
                        deleted
                    } else {
                        Log.d(TAG, "onDeleteDataZip: data.zip file does not exist")
                        true // Consider non-existent file as success
                    }
                }
                onComplete(success)
            } catch (e: Exception) {
                Log.e(TAG, "onDeleteDataZip: Failed to delete data.zip", e)
                onComplete(false)
            }
        }
    }
    
    fun importFavorites(uri: Uri, onComplete: (Result<String>) -> Unit) {
        viewModelScope.launch {
            try {
                val jsonString = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().readText()
                    } ?: throw IllegalStateException("Could not open file")
                }
                val result = withContext(Dispatchers.IO) {
                    backupImportExportService.importBackup(jsonString)
                }
                val summary = buildString {
                    append("Imported ${result.favoritesImported} favorites")
                    if (result.reviewsImported > 0) append(", ${result.reviewsImported} reviews")
                    if (result.tracksImported > 0) append(", ${result.tracksImported} tracks")
                    if (result.preferencesImported > 0) append(", ${result.preferencesImported} recording prefs")
                }
                onComplete(Result.success(summary))
            } catch (e: Exception) {
                Log.e(TAG, "Backup import failed", e)
                onComplete(Result.failure(e))
            }
        }
    }

    fun exportFavorites(onComplete: (Result<String>) -> Unit) {
        viewModelScope.launch {
            try {
                val jsonString = withContext(Dispatchers.IO) {
                    backupImportExportService.export()
                }
                val downloadsDir = File("/storage/emulated/0/Download/")
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val dateString = dateFormat.format(Date())
                val filename = "the-deadly-backup-$dateString.json"
                val exportFile = File(downloadsDir, filename)
                withContext(Dispatchers.IO) {
                    exportFile.writeText(jsonString)
                }
                Log.d(TAG, "Backup exported to: ${exportFile.absolutePath}")
                onComplete(Result.success(filename))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export backup", e)
                onComplete(Result.failure(e))
            }
        }
    }

    /**
     * Delete all deady_db* files from the databases directory
     * 
     * @param onComplete Callback with success/failure result
     */
    fun onDeleteDatabaseFiles(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    // Try multiple possible database locations
                    val possibleDirs = listOf(
                        File(context.getDatabasePath("dummy").parent!!), // Standard Android databases location
                        File(context.applicationInfo.dataDir, "databases"), // Alternative location
                        File(context.filesDir, "databases") // Another possible location
                    )
                    
                    var foundAnyFiles = false
                    var allDeleted = true
                    
                    for (databaseDir in possibleDirs) {
                        Log.d(TAG, "onDeleteDatabaseFiles: Checking directory: ${databaseDir.absolutePath}")
                        
                        if (!databaseDir.exists()) {
                            Log.d(TAG, "onDeleteDatabaseFiles: Directory does not exist: ${databaseDir.absolutePath}")
                            continue
                        }
                        
                        val dbFiles = databaseDir.listFiles { file ->
                            file.name.startsWith("deadly_db")
                        } ?: emptyArray()
                        
                        Log.d(TAG, "onDeleteDatabaseFiles: Found ${dbFiles.size} deadly_db* files in ${databaseDir.absolutePath}")
                        
                        if (dbFiles.isNotEmpty()) {
                            foundAnyFiles = true
                        }
                        
                        for (file in dbFiles) {
                            try {
                                Log.d(TAG, "onDeleteDatabaseFiles: Attempting to delete ${file.absolutePath}")
                                val deleted = file.delete()
                                Log.d(TAG, "onDeleteDatabaseFiles: ${file.name} deletion result: $deleted")
                                if (!deleted) {
                                    allDeleted = false
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "onDeleteDatabaseFiles: Failed to delete ${file.name}", e)
                                allDeleted = false
                            }
                        }
                    }
                    
                    // If no files were found at all, consider it success (nothing to delete)
                    if (!foundAnyFiles) {
                        Log.d(TAG, "onDeleteDatabaseFiles: No deadly_db* files found in any location")
                        true
                    } else {
                        allDeleted
                    }
                }
                onComplete(success)
            } catch (e: Exception) {
                Log.e(TAG, "onDeleteDatabaseFiles: Failed to delete database files", e)
                onComplete(false)
            }
        }
    }
}