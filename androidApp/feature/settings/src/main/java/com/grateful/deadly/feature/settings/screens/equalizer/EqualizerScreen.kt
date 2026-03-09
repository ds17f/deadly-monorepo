package com.grateful.deadly.feature.settings.screens.equalizer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun EqualizerScreen(
    viewModel: EqualizerSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.equalizerState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Equalizer",
                style = MaterialTheme.typography.headlineMedium
            )
            Switch(
                checked = state.enabled,
                onCheckedChange = viewModel::setEnabled
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Preset chips
        Text(
            text = "PRESETS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.presets.forEach { preset ->
                FilterChip(
                    selected = state.currentPreset == preset,
                    onClick = { viewModel.selectPreset(preset) },
                    label = { Text(preset) },
                    enabled = state.enabled
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Band sliders
        Text(
            text = "BANDS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            state.bands.forEach { band ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${band.currentLevel.toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.enabled)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        textAlign = TextAlign.Center
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Slider(
                            value = band.currentLevel,
                            onValueChange = { viewModel.setBandLevel(band.index, it) },
                            valueRange = band.minLevel..band.maxLevel,
                            enabled = state.enabled,
                            modifier = Modifier
                                .requiredWidth(220.dp)
                                .wrapContentHeight()
                                .graphicsLayer(
                                    rotationZ = -90f,
                                    transformOrigin = TransformOrigin.Center
                                )
                        )
                    }

                    Text(
                        text = band.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.enabled)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = viewModel::resetToFlat,
            enabled = state.enabled,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Reset to Flat")
        }
    }
}
