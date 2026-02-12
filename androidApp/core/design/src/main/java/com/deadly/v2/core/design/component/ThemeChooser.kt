package com.deadly.v2.core.design.component

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.deadly.v2.core.design.resources.IconResources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * ThemeChooser - Self-contained component for importing theme ZIP files
 * 
 * Provides a simple button that opens the system file picker restricted to ZIP files.
 * When a file is selected, it's copied to the app's private themes directory and
 * the callback is invoked with the copied file.
 * 
 * Features:
 * - Uses modern Android file picker (scoped storage)
 * - No storage permissions required
 * - Automatic directory creation
 * - Loading states and error handling
 * - Unique filename generation to avoid conflicts
 */
@Composable
fun ThemeChooser(
    onThemeImported: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isImporting by remember { mutableStateOf(false) }
    
    // File picker launcher for ZIP files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isImporting = true
                try {
                    val importedFile = importThemeFile(context, uri)
                    onThemeImported(importedFile)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Theme imported successfully!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to import theme: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    isImporting = false
                }
            }
        }
    }
    
    OutlinedButton(
        onClick = {
            if (!isImporting) {
                filePickerLauncher.launch("application/zip")
            }
        },
        enabled = !isImporting,
        modifier = modifier
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isImporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    painter = IconResources.Content.Folder(),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(if (isImporting) "Importing..." else "Import Theme")
        }
    }
}

/**
 * Copies the selected theme file to the app's private themes directory
 */
private suspend fun importThemeFile(context: Context, uri: android.net.Uri): File = withContext(Dispatchers.IO) {
    // Create themes directory if it doesn't exist
    val themesDir = File(context.filesDir, "themes")
    if (!themesDir.exists()) {
        themesDir.mkdirs()
    }
    
    // Generate a unique filename
    val originalName = getFileName(context, uri) ?: "theme.zip"
    val fileName = generateUniqueFileName(themesDir, originalName)
    val targetFile = File(themesDir, fileName)
    
    // Copy the file
    context.contentResolver.openInputStream(uri)?.use { inputStream ->
        FileOutputStream(targetFile).use { outputStream ->
            inputStream.copyTo(outputStream)
        }
    } ?: throw IllegalStateException("Could not open input stream for selected file")
    
    return@withContext targetFile
}

/**
 * Get the filename from the URI, with fallback to default name
 */
private fun getFileName(context: Context, uri: android.net.Uri): String? {
    return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            cursor.getString(nameIndex)
        } else null
    }
}

/**
 * Generate a unique filename to avoid conflicts
 */
private fun generateUniqueFileName(directory: File, originalName: String): String {
    if (!File(directory, originalName).exists()) {
        return originalName
    }
    
    val nameWithoutExtension = originalName.substringBeforeLast('.')
    val extension = originalName.substringAfterLast('.', "")
    
    var counter = 1
    while (true) {
        val newName = if (extension.isNotEmpty()) {
            "${nameWithoutExtension}_$counter.$extension"
        } else {
            "${nameWithoutExtension}_$counter"
        }
        
        if (!File(directory, newName).exists()) {
            return newName
        }
        counter++
    }
}