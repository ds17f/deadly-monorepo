package com.deadly.v2.feature.playlist.screens.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deadly.v2.core.design.resources.IconResources
import com.deadly.v2.core.model.RecordingSelectionState

/**
 * PlaylistV2RecordingSelectionSheet - Modal bottom sheet for recording selection
 * 
 * V2 implementation of recording selection following V1 design patterns but using
 * V2 View Models and architecture. Displays alternative recordings with recommendation
 * status and allows user selection and preference setting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistRecordingSelectionSheet(
    state: RecordingSelectionState,
    onRecordingSelected: (String) -> Unit, // recordingId
    onSetAsDefault: (String) -> Unit, // recordingId
    onResetToRecommended: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Text(
                text = "Choose Recording",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = state.showTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                
                state.errorMessage != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Error: ${state.errorMessage}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                else -> {
                    // Recording Options
                    LazyColumn(
                        modifier = Modifier
                            .selectableGroup()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Current recording first
                        state.currentRecording?.let { currentRecording ->
                            item {
                                PlaylistRecordingOptionCard(
                                    recordingOption = currentRecording,
                                    onClick = { 
                                        onRecordingSelected(currentRecording.identifier) 
                                    }
                                )
                            }
                        }
                        
                        // Alternative recordings
                        items(state.alternativeRecordings) { option ->
                            PlaylistRecordingOptionCard(
                                recordingOption = option,
                                onClick = { 
                                    onRecordingSelected(option.identifier) 
                                }
                            )
                        }
                    }
                    
                    // Action buttons
                    val selectedRecording = state.alternativeRecordings.find { it.isSelected }
                    val recommendedRecording = state.alternativeRecordings.find { 
                        it.isRecommended && it.matchReason == "Recommended" 
                    }
                    val currentIsRecommended = state.currentRecording?.isRecommended == true && 
                        state.currentRecording?.matchReason == "Recommended"
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Reset to Recommended button (show if there's a recommended recording and current isn't it)
                    if (recommendedRecording != null && !currentIsRecommended && onResetToRecommended != null) {
                        OutlinedButton(
                            onClick = { 
                                onResetToRecommended()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = IconResources.Content.Star(),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reset to Recommended")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    // Set as Default button (only show if different recording selected)
                    selectedRecording?.let { selected ->
                        if (selected.identifier != state.currentRecording?.identifier) {
                            Button(
                                onClick = { 
                                    onSetAsDefault(selected.identifier)
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    painter = IconResources.Content.Star(),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Set as Default Recording")
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}