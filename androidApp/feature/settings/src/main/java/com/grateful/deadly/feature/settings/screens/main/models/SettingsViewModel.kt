package com.grateful.deadly.feature.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * SettingsViewModel - Business logic for Settings screen
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    companion object {
        private const val TAG = "SettingsViewModel"
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