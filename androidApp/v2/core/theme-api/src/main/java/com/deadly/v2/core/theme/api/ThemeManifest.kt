package com.deadly.v2.core.theme.api

import kotlinx.serialization.Serializable

/**
 * Manifest file for theme packages, defines theme metadata and asset mappings
 */
@Serializable
data class ThemeManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String? = null,
    val author: String? = null,
    val assets: AssetMapping
)

/**
 * Maps theme asset types to their file names within the theme package
 */
@Serializable
data class AssetMapping(
    val primaryLogo: String,      // Main app logo (e.g., "primary_logo.png")
    val splashLogo: String,       // Splash screen logo (e.g., "splash_logo.png")
    // Future expansion: backgrounds, secondary logos, etc.
)