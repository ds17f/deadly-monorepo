package com.deadly.v2.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.system.exitProcess
import androidx.hilt.navigation.compose.hiltViewModel
import com.deadly.v2.core.design.component.ThemeChooser

/**
 * SettingsScreen - V2 Settings interface
 * 
 * Simple settings content for theme management and configuration.
 * Scaffold-free content designed for use within MainNavigation's AppScaffold.
 * 
 * Provides access to app configuration options including:
 * - Theme management and import
 * - Future: App preferences, about info, etc.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
            // Themes Section
            item {
                SettingsSection(title = "Themes") {
                    ThemeChooser(
                        onThemeImported = viewModel::onThemeImported,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    ClearThemesButton(
                        onClearThemes = viewModel::onClearThemes,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            // Cache Management Section
            item {
                SettingsSection(title = "Cache Management") {
                    ClearArchiveCacheButton(modifier = Modifier.fillMaxWidth())
                }
            }
            
            // Data Management Section
            item {
                SettingsSection(title = "Data Management") {
                    DeleteDataZipButton(
                        onDeleteDataZip = viewModel::onDeleteDataZip,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    DeleteDatabaseFilesButton(
                        onDeleteDatabaseFiles = viewModel::onDeleteDatabaseFiles,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            // V1 App Restore Section
            item {
                SettingsSection(title = "App Version") {
                    BackToV1Button(modifier = Modifier.fillMaxWidth())
                }
            }
            
            // Future sections can be added here
            // item {
            //     SettingsSection(title = "Preferences") {
            //         // App preferences UI
            //     }
            // }
            //
            // item {
            //     SettingsSection(title = "About") {
            //         // About app info
            //     }
            // }
    }
}

/**
 * Reusable settings section component
 */
@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            content()
        }
    }
}

/**
 * Button to clear all themes with confirmation dialog
 */
@Composable
private fun ClearThemesButton(
    onClearThemes: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    
    OutlinedButton(
        onClick = { showConfirmDialog = true },
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            MaterialTheme.colorScheme.error
        )
    ) {
        Text("Clear All Themes")
    }
    
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Clear All Themes") },
            text = { 
                Text("This will delete all imported themes and restart the app to restore the default theme. Continue?") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        onClearThemes()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Button to restore V1 app with confirmation dialog
 */
@Composable
private fun BackToV1Button(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showConfirmDialog by remember { mutableStateOf(false) }
    
    OutlinedButton(
        onClick = { showConfirmDialog = true },
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text("Back to V1 App")
    }
    
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Return to V1 App") },
            text = { 
                Text("This will restart the app and return to the original V1 interface.") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        disableV2App(context)
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun disableV2App(context: android.content.Context) {
    try {
        val toggleFile = File(context.filesDir, "enable-v2-app")
        toggleFile.delete()
        // Restart app
        exitProcess(0)
    } catch (e: Exception) {
        // Simple error handling - just ignore for now since this is temporary
    }
}

/**
 * Button to clear archive cache with confirmation dialog
 */
@Composable
private fun ClearArchiveCacheButton(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showConfirmDialog by remember { mutableStateOf(false) }
    
    OutlinedButton(
        onClick = { showConfirmDialog = true },
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.secondary
        )
    ) {
        Text("Clear Archive Cache")
    }
    
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Clear Archive Cache") },
            text = { 
                Text("This will delete all cached track lists and reviews. The data will be re-downloaded when needed.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        clearArchiveCache(context)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun clearArchiveCache(context: android.content.Context) {
    try {
        // Clear the archive cache directory
        val cacheDir = File(context.cacheDir, "archive")
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        }
    } catch (e: Exception) {
        // Simple error handling - just ignore for now
    }
}

/**
 * Button to delete data.zip file with confirmation dialog
 */
@Composable
private fun DeleteDataZipButton(
    onDeleteDataZip: (onComplete: (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showCompletionDialog by remember { mutableStateOf(false) }
    var deletionSuccess by remember { mutableStateOf(false) }
    
    OutlinedButton(
        onClick = { showConfirmDialog = true },
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            MaterialTheme.colorScheme.error
        )
    ) {
        Text("Delete Data.zip")
    }
    
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Delete Data.zip File") },
            text = { 
                Text("This will delete the data.zip file from the files directory. The app will need to re-download show data when needed. Continue?") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        onDeleteDataZip { success ->
                            deletionSuccess = success
                            showCompletionDialog = true
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    if (showCompletionDialog) {
        AlertDialog(
            onDismissRequest = { showCompletionDialog = false },
            title = { 
                Text(if (deletionSuccess) "Data.zip Deleted" else "Deletion Failed") 
            },
            text = { 
                Text(
                    if (deletionSuccess) "The data.zip file has been successfully deleted." 
                    else "Failed to delete the data.zip file. It may not exist or be in use."
                )
            },
            confirmButton = {
                TextButton(onClick = { showCompletionDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

/**
 * Button to delete database files with confirmation dialog
 */
@Composable
private fun DeleteDatabaseFilesButton(
    onDeleteDatabaseFiles: (onComplete: (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showCompletionDialog by remember { mutableStateOf(false) }
    var deletionSuccess by remember { mutableStateOf(false) }
    
    OutlinedButton(
        onClick = { showConfirmDialog = true },
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            MaterialTheme.colorScheme.error
        )
    ) {
        Text("Delete Database Files")
    }
    
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Delete Database Files") },
            text = { 
                Text("This will delete all deadly_db* files from the databases directory. All stored shows, favorites, and app data will be lost. Continue?") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        onDeleteDatabaseFiles { success ->
                            deletionSuccess = success
                            showCompletionDialog = true
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    if (showCompletionDialog) {
        AlertDialog(
            onDismissRequest = { showCompletionDialog = false },
            title = { 
                Text(if (deletionSuccess) "Database Files Deleted" else "Deletion Failed") 
            },
            text = { 
                Text(
                    if (deletionSuccess) "The database files have been successfully deleted. The app will create fresh data on next use." 
                    else "Failed to delete some or all database files. They may be in use by the app."
                )
            },
            confirmButton = {
                TextButton(onClick = { showCompletionDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}