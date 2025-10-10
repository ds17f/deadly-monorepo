package com.deadly.v2.core.design.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deadly.v2.core.design.resources.IconResources

/**
 * Flexible V2 Share component that can be used in different contexts:
 * - Icon only (for action bars)
 * - Icon + text row (for menus and panels)
 * - Customizable sizing and spacing
 */
@Composable
fun ShareComponent(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
    showText: Boolean = true,
    iconSize: Dp = 24.dp,
    spacing: Dp = 16.dp,
    text: String = "Share"
) {
    Row(
        modifier = modifier
            .clickable { onClick() }
            .then(
                if (showText) {
                    Modifier.padding(vertical = 16.dp)
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showIcon) {
            Icon(
                painter = IconResources.Content.Share(),
                contentDescription = text,
                modifier = Modifier.size(iconSize)
            )
        }
        
        if (showIcon && showText) {
            Spacer(modifier = Modifier.width(spacing))
        }
        
        if (showText) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/**
 * Convenience composable for icon-only usage (action bars, toolbars)
 */
@Composable
fun ShareIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp
) {
    ShareComponent(
        onClick = onClick,
        modifier = modifier,
        showIcon = true,
        showText = false,
        iconSize = iconSize
    )
}

/**
 * Convenience composable for menu row usage (bottom sheets, menus)
 */
@Composable
fun ShareMenuRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp
) {
    ShareComponent(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        showIcon = true,
        showText = true,
        iconSize = iconSize
    )
}