package com.deadly.v2.core.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.deadly.v2.core.theme.api.ThemeAssetProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Built-in theme provider that loads assets from app resources.
 * 
 * Provides the default "Deadly" theme using the lightning bolt logo
 * and other built-in assets from the design module.
 */
@Singleton
class BuiltinThemeProvider @Inject constructor() : ThemeAssetProvider {
    
    companion object {
        private const val THEME_ID = "builtin.deadly"
        private const val THEME_NAME = "Deadly"
    }
    
    @Composable
    override fun primaryLogo(): Painter {
        return painterResource(id = com.deadly.v2.core.design.R.drawable.lightning_bolt_logo)
    }
    
    @Composable
    override fun splashLogo(): Painter {
        // Use same logo for splash as primary
        return painterResource(id = com.deadly.v2.core.design.R.drawable.lightning_bolt_logo)
    }
    
    override fun getThemeId(): String = THEME_ID
    
    override fun getThemeName(): String = THEME_NAME
}