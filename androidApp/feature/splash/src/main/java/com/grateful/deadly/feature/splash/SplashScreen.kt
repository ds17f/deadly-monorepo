package com.grateful.deadly.feature.splash

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.feature.splash.model.Phase
import kotlinx.coroutines.delay

/**
 * Rotating quotes displayed during import.
 * Format: Pair(quote, song attribution)
 */
private val deadlyQuotes = listOf(
    "What a long strange trip it's been" to "Truckin'",
    "Once in a while you get shown the light" to "Scarlet Begonias",
    "Nothing left to do but smile, smile, smile" to "He's Gone",
    "Ain't no time to hate, barely time to wait" to "Uncle John's Band",
    "Sometimes the light's all shining on me" to "Truckin'",
    "Let there be songs to fill the air" to "Ripple",
    "Every silver lining's got a touch of grey" to "Touch of Grey",
    "If the thunder don't get you then the lightning will" to "The Wheel",
    "Gotta get down to the Cumberland Mine" to "Cumberland Blues",
    "Wake up to find out that you are the eyes of the world" to "Eyes of the World",
    "Going where the wind don't blow so strange" to "Brokedown Palace",
    "The sky was yellow and the sun was blue" to "Scarlet Begonias",
    "Without love in the dream it will never come true" to "Help on the Way",
    "Such a long, long time to be gone and a short time to be there" to "Box of Rain",
    "Lately it occurs to me what a long strange trip it's been" to "Truckin'",
)

@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var currentQuoteIndex by remember { mutableIntStateOf(0) }

    // Rotate quotes every 5 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            currentQuoteIndex = (currentQuoteIndex + 1) % deadlyQuotes.size
        }
    }

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
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // MARK: - Branding Section
            BrandingSection(isReady = uiState.isReady)

            Spacer(modifier = Modifier.height(40.dp))

            // MARK: - Quote Section
            QuoteSection(
                quote = deadlyQuotes[currentQuoteIndex],
                modifier = Modifier.height(80.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            // MARK: - Status Section
            StatusSection(
                uiState = uiState,
                currentTime = currentTime,
                onRetry = { viewModel.retryInitialization() },
                onSkip = { viewModel.skipInitialization() },
                onAbort = { viewModel.abortInitialization() },
                onSelectSource = { viewModel.selectDatabaseSource(it) }
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun BrandingSection(isReady: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Logo or checkmark
        if (isReady) {
            Icon(
                painter = IconResources.Status.Success(),
                contentDescription = "Ready",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(80.dp)
            )
        } else {
            Image(
                painter = painterResource(com.grateful.deadly.core.design.R.drawable.lightning_bolt_logo),
                contentDescription = "Deadly Logo",
                modifier = Modifier.size(120.dp)
            )
        }

        // App name
        Text(
            text = if (isReady) "Ready" else "Deadly",
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
            color = if (isReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )

        // Tagline
        if (!isReady) {
            Text(
                text = "The Killer App for the Golden Road",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuoteSection(
    quote: Pair<String, String>,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = quote,
        transitionSpec = {
            fadeIn(initialAlpha = 0f) togetherWith fadeOut(targetAlpha = 0f)
        },
        modifier = modifier,
        label = "quote"
    ) { currentQuote ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(
                text = "\"${currentQuote.first}\"",
                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "— ${currentQuote.second}",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusSection(
    uiState: com.grateful.deadly.feature.splash.service.SplashUiState,
    currentTime: Long,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
    onAbort: () -> Unit,
    onSelectSource: (com.grateful.deadly.core.database.service.DatabaseManager.DatabaseSource) -> Unit
) {
    when {
        uiState.showSourceSelection -> {
            SourceSelectionContent(
                availableSources = uiState.availableSources,
                onSelectSource = onSelectSource,
                onSkip = onAbort
            )
        }

        uiState.showError -> {
            ErrorContent(
                errorMessage = uiState.errorMessage,
                onRetry = onRetry,
                onSkip = onSkip
            )
        }

        uiState.showProgress -> {
            ProgressContent(
                uiState = uiState,
                currentTime = currentTime,
                onAbort = onAbort
            )
        }

        uiState.isReady -> {
            // Just show empty - branding section shows "Ready"
        }

        else -> {
            LoadingContent(message = uiState.message)
        }
    }
}

@Composable
private fun SourceSelectionContent(
    availableSources: List<com.grateful.deadly.core.database.service.DatabaseManager.DatabaseSource>,
    onSelectSource: (com.grateful.deadly.core.database.service.DatabaseManager.DatabaseSource) -> Unit,
    onSkip: () -> Unit
) {
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
            availableSources.forEach { source ->
                when (source) {
                    com.grateful.deadly.core.database.service.DatabaseManager.DatabaseSource.ZIP_BACKUP -> {
                        Button(
                            onClick = { onSelectSource(source) },
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
                    com.grateful.deadly.core.database.service.DatabaseManager.DatabaseSource.DATA_IMPORT -> {
                        OutlinedButton(
                            onClick = { onSelectSource(source) },
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

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text("Skip")
        }
    }
}

@Composable
private fun ErrorContent(
    errorMessage: String?,
    onRetry: () -> Unit,
    onSkip: () -> Unit
) {
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
            text = "Import Failed",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )

        Text(
            text = errorMessage ?: "Unknown error",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = onRetry) {
                Text("Retry")
            }
            OutlinedButton(onClick = onSkip) {
                Text("Skip")
            }
        }
    }
}

@Composable
private fun ProgressContent(
    uiState: com.grateful.deadly.feature.splash.service.SplashUiState,
    currentTime: Long,
    onAbort: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Phase-specific progress indicator
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

        // Phase message
        Text(
            text = uiState.message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Current item being processed
        if (uiState.progress.currentItem.isNotBlank()) {
            Text(
                text = uiState.progress.currentItem,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }

        // Elapsed time
        if (uiState.progress.startTimeMs > 0L) {
            Text(
                text = "Elapsed: ${uiState.progress.getElapsedTimeString(currentTime)}",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }

        // Skip button
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onAbort,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text("Skip")
        }
    }
}

@Composable
private fun LoadingContent(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            strokeWidth = 3.dp
        )

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
