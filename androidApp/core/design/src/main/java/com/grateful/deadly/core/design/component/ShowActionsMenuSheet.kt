package com.grateful.deadly.core.design.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.design.resources.IconResources

/**
 * ShowActionsMenuSheet — the single unified "⋯" overflow shared by the full
 * player and the playlist (ADR-0014).
 *
 * One taxonomy, learned once and reused on both surfaces:
 *   - Playback   — Choose Recording · Equalizer · Autoplay Next Show
 *   - This Show  — Setlist · Collections (only when in ≥1) · Download
 *   - Share      — Share
 *
 * Each surface simply passes `null` for the actions it already shows inline, so
 * a control is never one-tap-inline *and* in the menu. When a group collapses to
 * a single item on a surface, the group header is dropped and a plain divider is
 * used instead (a lone item under a bold label reads heavier than it is).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowActionsMenuSheet(
    recordingId: String?,
    title: String,
    showDate: String,
    venue: String,
    isAutoplayEnabled: Boolean,
    collectionsCount: Int,
    // Playback group — pass null to hide (shown inline on this surface)
    onChooseRecording: (() -> Unit)? = null,
    onEqualizer: (() -> Unit)? = null,
    onAutoplay: (() -> Unit)? = null,
    // This Show group
    onSetlist: (() -> Unit)? = null,
    onCollections: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    // Share group
    onShare: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Build each group's visible items in the canonical order.
    val playback = buildList {
        onChooseRecording?.let { add(MenuItem("Choose Recording", IconResources.PlayerControls.AlbumArt(), it)) }
        onEqualizer?.let { add(MenuItem("Equalizer", IconResources.PlayerControls.Equalizer(), it)) }
        onAutoplay?.let { add(MenuItem("Autoplay Next Show", IconResources.PlayerControls.Autoplay(), it, active = isAutoplayEnabled, dismissOnClick = false)) }
    }
    val thisShow = buildList {
        onSetlist?.let { add(MenuItem("Setlist", IconResources.Content.FormatListBulleted(), it)) }
        // Collections appears only when the show is actually in a collection.
        if (collectionsCount > 0) {
            onCollections?.let { add(MenuItem("Collections", IconResources.Navigation.Collections(), it)) }
        }
        onDownload?.let { add(MenuItem("Download", IconResources.Content.FileDownload(), it)) }
    }
    val share = buildList {
        onShare?.let { add(MenuItem("Share", IconResources.Content.Share(), it)) }
    }
    val groups = listOf(playback, thisShow, share).filter { it.isNotEmpty() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // Header card — which show these actions apply to.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.size(60.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    ShowArtwork(
                        recordingId = recordingId,
                        contentDescription = "Album Art",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = showDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = venue,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            groups.forEachIndexed { index, group ->
                HorizontalDivider()
                // Multi-item groups get a bold header; single-item groups read as
                // a plain divided row (ADR-0014).
                if (group.size >= 2) {
                    Text(
                        text = groupLabel(group),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                group.forEach { item ->
                    ActionMenuRow(
                        text = item.label,
                        icon = item.icon,
                        active = item.active,
                        onClick = {
                            item.onClick()
                            if (item.dismissOnClick) onDismiss()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private data class MenuItem(
    val label: String,
    val icon: Painter,
    val onClick: () -> Unit,
    val active: Boolean = false,
    /** Toggles (Autoplay) keep the sheet open so state change is visible. */
    val dismissOnClick: Boolean = true
)

/** Resolve the bold header for a multi-item group by its first member. */
private fun groupLabel(group: List<MenuItem>): String = when (group.first().label) {
    "Choose Recording", "Equalizer", "Autoplay Next Show" -> "Playback"
    "Setlist", "Collections", "Download" -> "This Show"
    else -> "Share"
}

@Composable
private fun ActionMenuRow(
    text: String,
    icon: Painter,
    onClick: () -> Unit,
    active: Boolean = false,
    modifier: Modifier = Modifier
) {
    val accent = if (active) MaterialTheme.colorScheme.primary else LocalContentColor.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = text,
            tint = accent,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = accent
        )
    }
}
