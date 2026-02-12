package com.deadly.v2.core.theme.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import javax.inject.Inject

/**
 * CompositionLocal for providing the current theme throughout the Compose tree
 */
val LocalThemeAssets = compositionLocalOf<ThemeAssetProvider> {
    error("No ThemeAssetProvider provided")
}

/**
 * Theme provider that wraps content with theme context using DI.
 * 
 * Usage:
 * ```
 * DeadlyTheme(themeProvider = injectedProvider) {
 *     // Your app content
 * }
 * ```
 */
@Composable
fun DeadlyTheme(
    themeProvider: ThemeAssetProvider,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalThemeAssets provides themeProvider
    ) {
        content()
    }
}

/**
 * Access the current theme assets from any Composable
 */
object ThemeAssets {
    val current: ThemeAssetProvider
        @Composable
        get() = LocalThemeAssets.current
}