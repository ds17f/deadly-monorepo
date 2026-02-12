package com.deadly.v2.core.theme.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/**
 * Interface for providing theme assets to Composables.
 * 
 * Implementations handle loading assets from different sources:
 * - BuiltinThemeProvider: loads from app resources
 * - ZipThemeProvider: loads from extracted theme packages
 */
interface ThemeAssetProvider {
    
    /**
     * Primary app logo used in navigation, headers, etc.
     */
    @Composable
    fun primaryLogo(): Painter
    
    /**
     * Logo variant for splash screens, may be same as primary
     */
    @Composable
    fun splashLogo(): Painter
    
    /**
     * Unique identifier for this theme
     */
    fun getThemeId(): String
    
    /**
     * Human-readable theme name
     */
    fun getThemeName(): String
}