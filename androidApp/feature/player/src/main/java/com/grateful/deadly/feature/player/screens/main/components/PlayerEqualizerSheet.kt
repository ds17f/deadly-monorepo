package com.grateful.deadly.feature.player.screens.main.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.media.equalizer.EqualizerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerEqualizerSheet(
    state: EqualizerState,
    onDismiss: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onPresetSelected: (String) -> Unit,
    onBandLevelChanged: (Int, Float) -> Unit,
    onReset: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header with toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Equalizer",
                    style = MaterialTheme.typography.titleLarge
                )
                Switch(
                    checked = state.enabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Preset chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.presets.forEach { preset ->
                    FilterChip(
                        selected = state.currentPreset == preset,
                        onClick = { onPresetSelected(preset) },
                        label = { Text(preset) },
                        enabled = state.enabled
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Band sliders
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                state.bands.forEach { band ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Gain label
                        Text(
                            text = "${band.currentLevel.toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.enabled)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            textAlign = TextAlign.Center
                        )

                        // Vertical slider (rotated horizontal)
                        VerticalSlider(
                            value = band.currentLevel,
                            onValueChange = { onBandLevelChanged(band.index, it) },
                            valueRange = band.minLevel..band.maxLevel,
                            enabled = state.enabled,
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp)
                        )

                        // Frequency label
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

            Spacer(modifier = Modifier.height(12.dp))

            // Reset button
            TextButton(
                onClick = onReset,
                enabled = state.enabled,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Reset to Flat")
            }
        }
    }
}

/**
 * A vertical slider implemented by drawing a rotated horizontal Slider.
 */
@Composable
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    // Use a Column with a rotated Slider for vertical orientation
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier
                .requiredWidth(180.dp)
                .wrapContentHeight()
                .graphicsLayer(rotationZ = -90f, transformOrigin = TransformOrigin.Center)
        )
    }
}
