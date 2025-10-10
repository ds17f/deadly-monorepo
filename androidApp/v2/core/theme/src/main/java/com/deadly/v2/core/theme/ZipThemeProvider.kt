package com.deadly.v2.core.theme

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import com.deadly.v2.core.theme.api.ThemeAssetProvider
import com.deadly.v2.core.theme.api.ThemeManifest
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipFile

/**
 * Theme provider that loads assets from extracted ZIP theme packages.
 * 
 * Handles loading theme manifests and extracting assets from ZIP files
 * into the app's internal storage for use by Compose UI.
 */
class ZipThemeProvider @AssistedInject constructor(
    @Assisted private val themeZipPath: String,
    @ApplicationContext private val context: Context
) : ThemeAssetProvider {
    
    @AssistedFactory
    interface Factory {
        fun create(themeZipPath: String): ZipThemeProvider
    }
    
    companion object {
        private const val TAG = "ZipThemeProvider"
    }
    
    private var _manifest: ThemeManifest? = null
    private var _extractedDir: File? = null
    
    private suspend fun ensureExtracted(): File = withContext(Dispatchers.IO) {
        _extractedDir?.let { 
            Log.d(TAG, "ensureExtracted: Already extracted to ${it.absolutePath}")
            return@withContext it 
        }
        
        Log.d(TAG, "ensureExtracted: Starting extraction for $themeZipPath")
        val themeFile = File(themeZipPath)
        if (!themeFile.exists()) {
            Log.e(TAG, "ensureExtracted: Theme file not found: $themeZipPath")
            throw IllegalStateException("Theme file not found: $themeZipPath")
        }
        
        // Create extraction directory in internal storage
        val themeId = themeFile.nameWithoutExtension
        val extractDir = File(context.filesDir, "themes/$themeId")
        Log.d(TAG, "ensureExtracted: Creating extraction directory: ${extractDir.absolutePath}")
        extractDir.mkdirs()
        
        // Extract ZIP contents
        Log.d(TAG, "ensureExtracted: Extracting ZIP contents...")
        ZipFile(themeFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                if (!entry.isDirectory) {
                    // Skip the top-level folder from ZIP entries (e.g., "grateful_dead_classic/theme.json" -> "theme.json")
                    val entryPath = entry.name
                    val fileName = entryPath.substringAfterLast('/')
                    
                    if (fileName.isNotEmpty()) {
                        val targetFile = File(extractDir, fileName)
                        Log.d(TAG, "ensureExtracted: Extracting ${entry.name} -> ${fileName}")
                        
                        zip.getInputStream(entry).use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
        }
        
        _extractedDir = extractDir
        Log.d(TAG, "ensureExtracted: Extraction completed to ${extractDir.absolutePath}")
        return@withContext extractDir
    }
    
    private suspend fun getManifest(): ThemeManifest = withContext(Dispatchers.IO) {
        _manifest?.let { 
            Log.d(TAG, "getManifest: Using cached manifest for theme: ${it.name}")
            return@withContext it 
        }
        
        Log.d(TAG, "getManifest: Loading manifest...")
        val extractDir = ensureExtracted()
        val manifestFile = File(extractDir, "theme.json")
        Log.d(TAG, "getManifest: Looking for manifest at: ${manifestFile.absolutePath}")
        
        if (!manifestFile.exists()) {
            Log.e(TAG, "getManifest: Theme manifest not found: theme.json")
            throw IllegalStateException("Theme manifest not found: theme.json")
        }
        
        val manifestContent = manifestFile.readText()
        Log.d(TAG, "getManifest: Manifest content: $manifestContent")
        val manifest = Json.decodeFromString<ThemeManifest>(manifestContent)
        Log.d(TAG, "getManifest: Successfully loaded manifest for theme: ${manifest.name} (${manifest.id})")
        _manifest = manifest
        return@withContext manifest
    }
    
    @Composable
    private fun loadAssetPainter(assetFileName: String): Painter {
        val context = LocalContext.current
        
        return remember(assetFileName) {
            try {
                Log.d(TAG, "loadAssetPainter: Loading asset: $assetFileName")
                val extractDir = _extractedDir ?: error("Theme not extracted")
                val assetFile = File(extractDir, assetFileName)
                Log.d(TAG, "loadAssetPainter: Asset file path: ${assetFile.absolutePath}")
                
                if (!assetFile.exists()) {
                    Log.e(TAG, "loadAssetPainter: Asset file not found: $assetFileName")
                    error("Asset file not found: $assetFileName")
                }
                
                val bitmap = BitmapFactory.decodeFile(assetFile.absolutePath)
                    ?: error("Failed to decode image: $assetFileName")
                
                Log.d(TAG, "loadAssetPainter: Successfully loaded asset: $assetFileName")
                BitmapPainter(bitmap.asImageBitmap())
            } catch (e: Exception) {
                Log.e(TAG, "loadAssetPainter: Failed to load theme asset: $assetFileName", e)
                throw IllegalStateException("Failed to load theme asset: $assetFileName", e)
            }
        }
    }
    
    @Composable
    override fun primaryLogo(): Painter {
        val manifest = _manifest ?: error("Theme not loaded")
        return loadAssetPainter(manifest.assets.primaryLogo)
    }
    
    @Composable
    override fun splashLogo(): Painter {
        val manifest = _manifest ?: error("Theme not loaded")
        return loadAssetPainter(manifest.assets.splashLogo)
    }
    
    override fun getThemeId(): String {
        return _manifest?.id ?: "unknown"
    }
    
    override fun getThemeName(): String {
        return _manifest?.name ?: "Unknown Theme"
    }
    
    /**
     * Initialize the theme by loading manifest and extracting assets.
     * Must be called before using the provider.
     */
    suspend fun initialize() {
        Log.d(TAG, "initialize: Starting ZIP theme initialization for: $themeZipPath")
        getManifest() // This will extract and load the manifest
        Log.d(TAG, "initialize: ZIP theme initialization completed")
    }
}