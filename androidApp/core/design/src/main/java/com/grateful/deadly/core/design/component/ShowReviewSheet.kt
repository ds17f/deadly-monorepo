package com.grateful.deadly.core.design.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.core.model.ShowReview

/**
 * Bottom sheet for reviewing a show: overall rating, recording/playing quality,
 * standout player chips, and free-text notes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowReviewSheet(
    showDate: String,
    venue: String,
    location: String,
    review: ShowReview,
    lineupMembers: List<String>,
    currentRecordingId: String? = null,
    bestRecordingId: String? = null,
    onSave: (
        notes: String?,
        overallRating: Float?,
        recordingQuality: Int?,
        playingQuality: Int?,
        standoutPlayers: List<String>
    ) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var notes by remember(review.showId, review.reviewedRecordingId) { mutableStateOf(review.notes ?: "") }
    var overallRating by remember(review.showId, review.reviewedRecordingId) { mutableStateOf(review.overallRating?.toInt() ?: 0) }
    var recordingQuality by remember(review.showId, review.reviewedRecordingId) { mutableStateOf(review.recordingQuality ?: 0) }
    var playingQuality by remember(review.showId, review.reviewedRecordingId) { mutableStateOf(review.playingQuality ?: 0) }
    var standoutPlayers by remember(review.showId, review.reviewedRecordingId) {
        mutableStateOf(review.playerTags.filter { it.isStandout }.map { it.playerName }.toSet())
    }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(text = showDate, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(text = venue, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (location.isNotBlank()) {
                Text(text = location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider()

            // Overall rating
            ReviewRatingRow(label = "Overall", rating = overallRating, onRatingChanged = { overallRating = it })

            // Recording quality
            ReviewRatingRow(label = "Recording", rating = recordingQuality, onRatingChanged = { recordingQuality = it })

            // Recording context — show which recording is being rated
            if (currentRecordingId != null || review.reviewedRecordingId != null) {
                val displayId = currentRecordingId ?: review.reviewedRecordingId
                val isNonDefault = bestRecordingId != null && displayId != null && displayId != bestRecordingId
                Row(
                    modifier = Modifier.padding(start = 100.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recording: ${displayId?.takeLast(12) ?: "unknown"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isNonDefault) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "(non-default)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            // Playing quality
            ReviewRatingRow(label = "Playing", rating = playingQuality, onRatingChanged = { playingQuality = it })

            // Standout players
            if (lineupMembers.isNotEmpty()) {
                Text(text = "Standout Players", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    lineupMembers.forEach { member ->
                        val selected = member in standoutPlayers
                        FilterChip(
                            selected = selected,
                            onClick = {
                                standoutPlayers = if (selected) standoutPlayers - member else standoutPlayers + member
                            },
                            label = { Text(member) },
                            leadingIcon = if (selected) {
                                { Icon(painter = IconResources.Content.Star(), contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )

            // Save
            Button(
                onClick = {
                    onSave(
                        notes.ifBlank { null },
                        if (overallRating > 0) overallRating.toFloat() else null,
                        if (recordingQuality > 0) recordingQuality else null,
                        if (playingQuality > 0) playingQuality else null,
                        standoutPlayers.toList()
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Review")
            }

            // Delete
            if (onDelete != null && review.hasContent) {
                OutlinedButton(
                    onClick = { showDeleteConfirmation = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete Review")
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Review") },
            text = { Text("This will permanently delete your review for this show.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete?.invoke()
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ReviewRatingRow(
    label: String,
    rating: Int,
    onRatingChanged: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(100.dp))
        Row {
            (1..5).forEach { star ->
                IconButton(
                    onClick = { onRatingChanged(if (rating == star) 0 else star) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = if (star <= rating) IconResources.Content.Star() else IconResources.Content.StarBorder(),
                        contentDescription = "$star stars",
                        tint = if (star <= rating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
