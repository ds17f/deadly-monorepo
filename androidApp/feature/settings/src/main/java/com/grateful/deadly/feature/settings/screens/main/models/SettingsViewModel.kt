package com.grateful.deadly.feature.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grateful.deadly.core.api.auth.AuthService
import com.grateful.deadly.core.api.auth.AuthState
import com.grateful.deadly.core.database.AppPreferences
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
        appPreferences.setIncludeShowsWithoutRecordings(!appPreferences.includeShowsWithoutRecordings.value)
    }

    val sourceBadgeStyle: StateFlow<String> = appPreferences.sourceBadgeStyle

    fun setSourceBadgeStyle(value: String) {
        appPreferences.setSourceBadgeStyle(value)
    }

    val authState: StateFlow<AuthState> = authService.authState

    val useBetaMode: StateFlow<Boolean> = appPreferences.useBetaModeFlow

    fun toggleUseBetaMode() {
        val newValue = !appPreferences.useBetaMode
        appPreferences.setUseBetaMode(newValue)
        (authService as? com.grateful.deadly.core.auth.AuthServiceImpl)?.onEnvironmentChanged()
    }

    val forceOnline: StateFlow<Boolean> = appPreferences.forceOnline

    fun toggleForceOnline() {
        appPreferences.setForceOnline(!appPreferences.forceOnline.value)
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