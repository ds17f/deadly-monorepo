package com.grateful.deadly.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Grateful Dead inspired color palette
private val DeadRed = Color(0xFFDC143C)      // Crimson red
private val DeadGold = Color(0xFFFFD700)     // Golden yellow
private val DeadGreen = Color(0xFF228B22)    // Forest green

private val DarkColorScheme = darkColorScheme(
    primary = DeadRed,
    onPrimary = Color.White,
    secondary = DeadGold,
    onSecondary = Color.Black,
    tertiary = DeadGreen,
    onTertiary = Color.White,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = DeadRed,
    onPrimary = Color.White,
    secondary = DeadGold,
    onSecondary = Color.Black,
    tertiary = DeadGreen,
    onTertiary = Color.White,
    background = Color.White,
    surface = Color(0xFFFFFBFE),
    onBackground = Color.Black,
    onSurface = Color.Black
)

/**
 * Material3 theme with Grateful Dead colors
 * Automatically respects system dark mode
 */
@Composable
fun DeadlyMaterialTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
