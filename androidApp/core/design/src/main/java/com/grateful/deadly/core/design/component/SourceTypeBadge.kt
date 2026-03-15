package com.grateful.deadly.core.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.model.RecordingSourceType

/**
 * Compact badge showing the best available source type for a show.
 *
 * Uses two icon categories for simplicity:
 * - Album icon for high-fidelity sources (SBD, FM, Matrix, Remaster)
 * - Mic icon for audience recordings
 * - Question mark for unknown
 *
 * The text label shows the specific type (SBD, FM, Matrix, etc.).
 */
@Composable
fun SourceTypeBadge(sourceType: RecordingSourceType, modifier: Modifier = Modifier) {
    val (icon, label) = when (sourceType) {
        RecordingSourceType.SOUNDBOARD, RecordingSourceType.FM,
        RecordingSourceType.MATRIX, RecordingSourceType.REMASTER ->
            Icons.Filled.Album to sourceType.displayName
        RecordingSourceType.AUDIENCE ->
            Icons.Filled.Mic to sourceType.displayName
        RecordingSourceType.UNKNOWN ->
            Icons.Filled.QuestionMark to "?"
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}
