package com.grateful.deadly.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.core.model.AdvanceMode

/**
 * The "Autoplay" control glyph (ADR-0010 Amendment). A constant ∞ anchor — so
 * users learn "∞ = autoplay" — with a small corner badge naming the active mode
 * (list = Show Queue, calendar = Chronological). [AdvanceMode.NONE] dims the ∞
 * and drops the badge, reading as "autoplay, idle".
 */
@Composable
fun AutoplayModeIcon(
    mode: AdvanceMode,
    modifier: Modifier = Modifier,
) {
    val active = mode != AdvanceMode.NONE
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier = modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Icon(
            painter = IconResources.PlayerControls.Autoplay(),
            contentDescription = if (active) "Autoplay: ${mode.displayName}" else "Autoplay",
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        when (mode) {
            AdvanceMode.SHOW_QUEUE -> IconResources.PlayerControls.ShowQueueMark()
            AdvanceMode.CHRONOLOGICAL -> IconResources.PlayerControls.Calendar()
            AdvanceMode.NONE -> null
        }?.let { badge ->
            Icon(
                painter = badge,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 3.dp, y = 3.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(1.dp),
            )
        }
    }
}
