package com.grateful.deadly.feature.player.screens.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grateful.deadly.feature.player.screens.main.models.PanelUiState

@Composable
fun PlayerMaterialPanels(
    panelState: PanelUiState,
    modifier: Modifier = Modifier
) {
    if (panelState.isLoading) return

    val hasAnyContent = panelState.credits != null
            || panelState.venueInfo != null
            || panelState.lyrics != null

    if (!hasAnyContent) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        panelState.lyrics?.let { text ->
            MaterialPanel(
                title = "Lyrics",
                content = text
            )
        }

        panelState.venueInfo?.let { info ->
            MaterialPanel(
                title = "About the Venue",
                content = info
            )
        }

        panelState.credits?.let { members ->
            MaterialPanel(
                title = "Credits",
                content = members.joinToString("\n") { "${it.name} \u2014 ${it.instruments}" }
            )
        }
    }
}

@Composable
private fun MaterialPanel(
    title: String,
    content: String,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = content,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2
            )
        }
    }
}
