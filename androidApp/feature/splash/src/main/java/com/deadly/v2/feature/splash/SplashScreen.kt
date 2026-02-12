package com.deadly.v2.feature.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.deadly.v2.feature.splash.model.Phase
import com.deadly.v2.core.design.resources.IconResources
import com.deadly.v2.core.theme.api.ThemeAssets
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    
    // Update timer every second while progress is showing
    LaunchedEffect(uiState.showProgress) {
        if (uiState.showProgress) {
            while (true) {
                delay(1000)
                currentTime = System.currentTimeMillis()
            }
        }
    }
    
    // Navigate when ready
    LaunchedEffect(uiState.isReady) {
        if (uiState.isReady) {
            delay(1000) // Brief delay to show completion message
            onSplashComplete()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Large App Logo
            Image(
                painter = ThemeAssets.current.splashLogo(),
                contentDescription = "Deadly Logo",
                modifier = Modifier.size(200.dp)
            )
            
            Text(
                text = "Deadly",
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "V2 Database - Enhanced Performance",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Show initialization progress, source selection, or error state
            when {
                uiState.showSourceSelection -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Text(
                            text = "Choose Database Source",
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                        
                        Text(
                            text = "Multiple initialization options are available",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            uiState.availableSources.forEach { source ->
                                when (source) {
                                    com.deadly.v2.core.database.service.DatabaseManager.DatabaseSource.ZIP_BACKUP -> {
                                        Button(
                                            onClick = { viewModel.selectDatabaseSource(source) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text("Restore from Backup")
                                                Text(
                                                    "Fast - uses pre-built database",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                    }
                                    com.deadly.v2.core.database.service.DatabaseManager.DatabaseSource.DATA_IMPORT -> {
                                        OutlinedButton(
                                            onClick = { viewModel.selectDatabaseSource(source) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text("Import Fresh Data")
                                                Text(
                                                    "Complete - builds from latest data files",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Skip option
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedButton(
                            onClick = { viewModel.abortInitialization() },
                            modifier = Modifier.padding(horizontal = 32.dp)
                        ) {
                            Text("Skip V2 Database")
                        }
                    }
                }
                
                uiState.showError -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            painter = IconResources.Status.Error(),
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        
                        Text(
                            text = "V2 Database Setup Failed",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        
                        Text(
                            text = uiState.errorMessage ?: "Unknown error",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedButton(onClick = { viewModel.retryInitialization() }) {
                                Text("Retry")
                            }
                            Button(onClick = { viewModel.skipInitialization() }) {
                                Text("Skip V2")
                            }
                        }
                    }
                }
                
                uiState.showProgress -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (uiState.progress.phase) {
                            Phase.DOWNLOADING -> {
                                if (uiState.progress.totalShows > 0 && uiState.progress.totalShows != uiState.progress.processedShows) {
                                    val progress = uiState.progress.processedShows.toFloat() / uiState.progress.totalShows.toFloat()
                                    LinearProgressIndicator(
                                        progress = { if (progress.isFinite()) progress else 0f },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    
                                    Text(
                                        text = "${uiState.progress.processedShows} / ${uiState.progress.totalShows} MB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        strokeWidth = 3.dp
                                    )
                                }
                            }
                            
                            Phase.IMPORTING_SHOWS -> {
                                if (uiState.progress.totalShows > 0) {
                                    val progress = uiState.progress.progressPercentage / 100f
                                    LinearProgressIndicator(
                                        progress = { if (progress.isFinite()) progress else 0f },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    
                                    Text(
                                        text = "${uiState.progress.processedShows} / ${uiState.progress.totalShows} shows",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        strokeWidth = 3.dp
                                    )
                                }
                            }
                            
                            Phase.IMPORTING_RECORDINGS -> {
                                if (uiState.progress.totalRecordings > 0) {
                                    val progress = uiState.progress.progressPercentage / 100f
                                    LinearProgressIndicator(
                                        progress = { if (progress.isFinite()) progress else 0f },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    
                                    Text(
                                        text = "${uiState.progress.processedRecordings} / ${uiState.progress.totalRecordings} recordings",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    if (uiState.progress.totalTracks > 0) {
                                        Text(
                                            text = "${uiState.progress.processedTracks} tracks processed",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        strokeWidth = 3.dp
                                    )
                                }
                            }
                            
                            Phase.COMPUTING_VENUES -> {
                                if (uiState.progress.totalVenues > 0) {
                                    val progress = uiState.progress.progressPercentage / 100f
                                    LinearProgressIndicator(
                                        progress = { if (progress.isFinite()) progress else 0f },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    
                                    Text(
                                        text = "${uiState.progress.processedVenues} / ${uiState.progress.totalVenues} venues",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        strokeWidth = 3.dp
                                    )
                                }
                            }
                            
                            else -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp
                                )
                            }
                        }
                        
                        Text(
                            text = uiState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        // Show current item being processed
                        if (uiState.progress.currentItem.isNotBlank()) {
                            Text(
                                text = uiState.progress.currentItem,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                        
                        // Show elapsed time
                        if (uiState.progress.startTimeMs > 0L) {
                            Text(
                                text = "Elapsed: ${uiState.progress.getElapsedTimeString(currentTime)}",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                        
                        // Abort button to skip V2 initialization
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedButton(
                            onClick = { viewModel.abortInitialization() },
                            modifier = Modifier.padding(horizontal = 32.dp)
                        ) {
                            Text("Skip V2 Import")
                        }
                    }
                }
                
                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                        
                        Text(
                            text = uiState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}