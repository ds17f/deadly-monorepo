package com.deadly.v2.core.design.component.debug

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

/**
 * Floating debug activation button that appears when debug mode is enabled.
 * Positioned in the bottom-right corner of the screen.
 */
@Composable
fun DebugActivator(
    isVisible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return
    
    // Fixed: Don't create a Box that fills the entire screen - just render the button directly
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .zIndex(999f),
        containerColor = Color(0xFFFF5722),
        contentColor = Color.White,
        shape = CircleShape
    ) {
        Text(
            text = "🐛",
            fontSize = 20.sp
        )
    }
}