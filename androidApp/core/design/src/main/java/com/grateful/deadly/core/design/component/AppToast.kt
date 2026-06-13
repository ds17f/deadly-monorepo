package com.grateful.deadly.core.design.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * AppToast — the app-wide transient toast pill (ADR-0014).
 *
 * Styled to match the app's other docked overlays (surfaceVariant pill, soft
 * shadow) rather than the stock Material snackbar, and rendered at the top of
 * the root z-stack so it's visible above every screen + the mini player.
 *
 * Usually non-actionable (a confirmation). When [actionLabel] + [onClick] are
 * supplied the whole pill becomes tappable and shows the label as an accented
 * affordance — used for the new-message toast ("View" → inbox), which the stock
 * snackbar rendered behind the mini player.
 */
@Composable
fun AppToast(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(percent = 50),
        shadowElevation = 8.dp,
        tonalElevation = 3.dp
    ) {
        if (actionLabel != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(16.dp))
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
    }
}
