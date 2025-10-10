package com.deadly.v2.core.theme

import android.content.Context
import android.util.Log
import com.deadly.v2.core.theme.api.ThemeAssetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages theme switching and provides the current active theme provider.
 * 
 * Coordinates between built-in and ZIP-based theme providers, handling
 * initialization and switching between different themes.
 */
@Singleton
class ThemeManager @Inject constructor(
    private val builtinProvider: BuiltinThemeProvider,
    private val zipProviderFactory: ZipThemeProvider.Factory,
    private val context: Context
) {
    
    companion object {
        private const val TAG = "ThemeManager"
    }
    
    private val _currentProvider = MutableStateFlow<ThemeAssetProvider>(builtinProvider)
    val currentProvider: StateFlow<ThemeAssetProvider> = _currentProvider.asStateFlow()
    
    private val _availableThemes = MutableStateFlow<List<ThemeInfo>>(
        listOf(
            ThemeInfo(
                id = builtinProvider.getThemeId(),
                name = builtinProvider.getThemeName(),
                isBuiltin = true,
                zipPath = null
            )
        )
    )
    val availableThemes: StateFlow<List<ThemeInfo>> = _availableThemes.asStateFlow()
    
    /**
     * Switch to the built-in theme
     */
    fun useBuiltinTheme() {
        Log.d(TAG, "useBuiltinTheme: Switching to builtin theme")
        _currentProvider.value = builtinProvider
    }
    
    /**
     * Switch to a ZIP-based theme
     * 
     * @param zipPath Path to the theme ZIP file
     * @throws IllegalStateException if the ZIP theme fails to load
     */
    suspend fun useZipTheme(zipPath: String) {
        Log.d(TAG, "useZipTheme: Attempting to load ZIP theme from: $zipPath")
        try {
            val zipProvider = zipProviderFactory.create(zipPath)
            Log.d(TAG, "useZipTheme: Created ZIP provider, initializing...")
            zipProvider.initialize()
            Log.d(TAG, "useZipTheme: ZIP provider initialized successfully")
            
            _currentProvider.value = zipProvider
            Log.d(TAG, "useZipTheme: Set current provider to ZIP theme: ${zipProvider.getThemeName()}")
            
            // Add to available themes if not already present
            val currentThemes = _availableThemes.value.toMutableList()
            val themeInfo = ThemeInfo(
                id = zipProvider.getThemeId(),
                name = zipProvider.getThemeName(),
                isBuiltin = false,
                zipPath = zipPath
            )
            
            if (currentThemes.none { it.id == themeInfo.id }) {
                currentThemes.add(themeInfo)
                _availableThemes.value = currentThemes
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "useZipTheme: Failed to load ZIP theme: $zipPath", e)
            throw IllegalStateException("Failed to load ZIP theme: $zipPath", e)
        }
    }
    
    /**
     * Get the currently active theme provider
     */
    fun getCurrentProvider(): ThemeAssetProvider = _currentProvider.value
    
    /**
     * MVP: Auto-initialize with newest theme or fallback to builtin
     * 
     * Scans for themes in app's files/themes directory and automatically
     * loads the newest ZIP theme if found, otherwise uses builtin theme.
     */
    suspend fun autoInitialize() {
        Log.d(TAG, "autoInitialize: Starting theme auto-initialization")
        val themesDir = File(context.filesDir, "themes")
        Log.d(TAG, "autoInitialize: Checking themes directory: ${themesDir.absolutePath}")
        
        if (!themesDir.exists()) {
            Log.d(TAG, "autoInitialize: Themes directory does not exist, using builtin theme")
            useBuiltinTheme()
            return
        }
        
        // Find all .zip files in themes directory
        val zipFiles = themesDir.listFiles { _, name -> name.endsWith(".zip") }
        Log.d(TAG, "autoInitialize: Found ${zipFiles?.size ?: 0} ZIP files in themes directory")
        
        zipFiles?.forEach { file ->
            Log.d(TAG, "autoInitialize: Found ZIP file: ${file.name} (${file.lastModified()})")
        }
        
        if (zipFiles.isNullOrEmpty()) {
            Log.d(TAG, "autoInitialize: No ZIP themes found, using builtin theme")
            useBuiltinTheme()
            return
        }
        
        // Find the newest theme file by last modified timestamp
        val newestTheme = zipFiles.maxByOrNull { it.lastModified() }
        Log.d(TAG, "autoInitialize: Newest theme file: ${newestTheme?.name}")
        
        if (newestTheme != null) {
            try {
                Log.d(TAG, "autoInitialize: Attempting to load newest theme: ${newestTheme.absolutePath}")
                useZipTheme(newestTheme.absolutePath)
                Log.d(TAG, "autoInitialize: Successfully loaded ZIP theme")
            } catch (e: Exception) {
                Log.e(TAG, "autoInitialize: ZIP theme loading failed, falling back to builtin", e)
                useBuiltinTheme()
            }
        } else {
            Log.d(TAG, "autoInitialize: No valid theme found, using builtin theme")
            useBuiltinTheme()
        }
    }
    
    /**
     * Clear all themes by deleting the themes directory
     * 
     * Removes all imported ZIP themes and extracted files, forcing the app
     * to use the builtin theme on next startup.
     */
    suspend fun clearAllThemes() = withContext(Dispatchers.IO) {
        Log.d(TAG, "clearAllThemes: Starting theme directory cleanup")
        val themesDir = File(context.filesDir, "themes")
        
        if (themesDir.exists()) {
            try {
                // Delete all contents recursively
                themesDir.deleteRecursively()
                Log.d(TAG, "clearAllThemes: Successfully deleted themes directory")
                
                // Recreate empty directory for future use
                themesDir.mkdirs()
                Log.d(TAG, "clearAllThemes: Recreated empty themes directory")
                
            } catch (e: Exception) {
                Log.e(TAG, "clearAllThemes: Failed to clear themes directory", e)
                throw IllegalStateException("Failed to clear themes directory", e)
            }
        } else {
            Log.d(TAG, "clearAllThemes: Themes directory does not exist, nothing to clear")
        }
    }
    
    /**
     * Scan for theme files in the app's themes directory
     */
    suspend fun scanForThemes() {
        val themesDir = File(context.filesDir, "themes")
        if (!themesDir.exists()) return
        
        val themes = mutableListOf<ThemeInfo>()
        themes.add(ThemeInfo(
            id = builtinProvider.getThemeId(),
            name = builtinProvider.getThemeName(),
            isBuiltin = true,
            zipPath = null
        ))
        
        themesDir.listFiles { _, name -> name.endsWith(".zip") }?.forEach { zipFile ->
            try {
                val zipProvider = zipProviderFactory.create(zipFile.absolutePath)
                zipProvider.initialize()
                
                themes.add(ThemeInfo(
                    id = zipProvider.getThemeId(),
                    name = zipProvider.getThemeName(),
                    isBuiltin = false,
                    zipPath = zipFile.absolutePath
                ))
            } catch (e: Exception) {
                // Skip invalid theme files
            }
        }
        
        _availableThemes.value = themes
    }
}

/**
 * Information about an available theme
 */
data class ThemeInfo(
    val id: String,
    val name: String,
    val isBuiltin: Boolean,
    val zipPath: String?
)