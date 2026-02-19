package com.grateful.deadly.feature.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grateful.deadly.core.database.AppPreferences
import com.grateful.deadly.core.database.migration.MigrationImportService
import com.grateful.deadly.core.database.migration.MigrationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * SettingsViewModel - Business logic for Settings screen
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val migrationImportService: MigrationImportService,
    private val appPreferences: AppPreferences,
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

    val showOnlyRecordedShows: StateFlow<Boolean> = appPreferences.showOnlyRecordedShows

    fun toggleShowOnlyRecordedShows() {
        appPreferences.setShowOnlyRecordedShows(!appPreferences.showOnlyRecordedShows.value)
    }

    val forceOnline: StateFlow<Boolean> = appPreferences.forceOnline

    fun toggleForceOnline() {
        appPreferences.setForceOnline(!appPreferences.forceOnline.value)
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