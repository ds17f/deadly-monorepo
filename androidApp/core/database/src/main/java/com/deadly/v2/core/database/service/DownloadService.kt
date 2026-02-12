package com.deadly.v2.core.database.service

import android.content.Context
import android.util.Log
import com.deadly.v2.core.network.github.service.GitHubDataService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gitHubDataService: GitHubDataService
) {
    
    companion object {
        private const val TAG = "DownloadService"
        private const val BUFFER_SIZE = 8192
    }
    
    data class DownloadProgress(
        val fileName: String = "",
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val isCompleted: Boolean = false,
        val error: String? = null
    ) {
        val progressPercent: Float get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
    }
    
    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: Flow<DownloadProgress> = _downloadProgress.asStateFlow()
    
    /**
     * Download a remote file to the local files directory
     */
    suspend fun downloadRemoteFile(
        remoteFile: FileDiscoveryService.RemoteFile,
        progressCallback: ((DownloadProgress) -> Unit)? = null
    ): DownloadResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting download: ${remoteFile.name} from ${remoteFile.downloadUrl}")
                
                val destinationFile = File(context.filesDir, remoteFile.name)
                
                // Delete existing file if it exists
                if (destinationFile.exists()) {
                    Log.d(TAG, "Deleting existing file: ${destinationFile.name}")
                    destinationFile.delete()
                }
                
                // Update progress - starting download
                val initialProgress = DownloadProgress(
                    fileName = remoteFile.name,
                    downloadedBytes = 0L,
                    totalBytes = remoteFile.sizeBytes,
                    isCompleted = false
                )
                _downloadProgress.value = initialProgress
                progressCallback?.invoke(initialProgress)
                
                // Download the file
                val responseBody = gitHubDataService.downloadFile(remoteFile.downloadUrl)
                val totalBytes = remoteFile.sizeBytes
                
                Log.d(TAG, "Download started: ${remoteFile.name} (${totalBytes} bytes)")
                
                // Stream the file to disk with progress tracking
                val result = writeResponseBodyToDisk(responseBody, destinationFile, totalBytes) { downloaded ->
                    val progress = DownloadProgress(
                        fileName = remoteFile.name,
                        downloadedBytes = downloaded,
                        totalBytes = totalBytes,
                        isCompleted = false
                    )
                    _downloadProgress.value = progress
                    progressCallback?.invoke(progress)
                }
                
                if (result) {
                    val completedProgress = DownloadProgress(
                        fileName = remoteFile.name,
                        downloadedBytes = totalBytes,
                        totalBytes = totalBytes,
                        isCompleted = true
                    )
                    _downloadProgress.value = completedProgress
                    progressCallback?.invoke(completedProgress)
                    
                    Log.d(TAG, "✅ Download completed: ${remoteFile.name} saved to ${destinationFile.absolutePath}")
                    DownloadResult.Success(destinationFile)
                } else {
                    val errorProgress = DownloadProgress(
                        fileName = remoteFile.name,
                        downloadedBytes = 0L,
                        totalBytes = totalBytes,
                        isCompleted = false,
                        error = "Failed to write file"
                    )
                    _downloadProgress.value = errorProgress
                    progressCallback?.invoke(errorProgress)
                    
                    Log.e(TAG, "❌ Download failed: could not write ${remoteFile.name}")
                    DownloadResult.Error("Failed to write downloaded file")
                }
                
            } catch (e: Exception) {
                val errorProgress = DownloadProgress(
                    fileName = remoteFile.name,
                    downloadedBytes = 0L,
                    totalBytes = remoteFile.sizeBytes,
                    isCompleted = false,
                    error = e.message
                )
                _downloadProgress.value = errorProgress
                progressCallback?.invoke(errorProgress)
                
                Log.e(TAG, "❌ Download failed: ${remoteFile.name}", e)
                DownloadResult.Error(e.message ?: "Download failed")
            }
        }
    }
    
    /**
     * Download a file by type (DATA_ZIP or DATABASE_ZIP) from GitHub releases
     */
    suspend fun downloadFileByType(
        fileType: FileDiscoveryService.FileType,
        progressCallback: ((DownloadProgress) -> Unit)? = null
    ): DownloadResult {
        return try {
            Log.d(TAG, "Downloading file by type: $fileType")
            
            // Get latest release
            val release = gitHubDataService.getLatestRelease()
            if (release == null) {
                Log.e(TAG, "❌ No GitHub release found")
                return DownloadResult.Error("No GitHub release found")
            }
            
            // Find the matching asset
            val asset = release.assets.find { asset ->
                val fileName = asset.name.lowercase()
                when (fileType) {
                    FileDiscoveryService.FileType.DATA_ZIP -> fileName.startsWith("data") && fileName.endsWith(".zip")
                    FileDiscoveryService.FileType.DATABASE_ZIP -> fileName.contains("db") && fileName.endsWith(".zip")
                }
            }
            
            if (asset == null) {
                Log.e(TAG, "❌ No matching asset found for type: $fileType")
                return DownloadResult.Error("No matching file found for type: $fileType")
            }
            
            // Convert to RemoteFile and download
            val remoteFile = FileDiscoveryService.RemoteFile(
                name = asset.name,
                downloadUrl = asset.browserDownloadUrl,
                type = fileType,
                sizeBytes = asset.size
            )
            
            downloadRemoteFile(remoteFile, progressCallback)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to download file by type: $fileType", e)
            DownloadResult.Error(e.message ?: "Download failed")
        }
    }
    
    /**
     * Write response body to disk with progress tracking
     */
    private fun writeResponseBodyToDisk(
        body: ResponseBody,
        destinationFile: File,
        totalBytes: Long,
        progressCallback: (Long) -> Unit
    ): Boolean {
        return try {
            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null
            
            try {
                val buffer = ByteArray(BUFFER_SIZE)
                var downloadedBytes = 0L
                
                inputStream = body.byteStream()
                outputStream = FileOutputStream(destinationFile)
                
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    
                    // Report progress every buffer
                    progressCallback(downloadedBytes)
                    
                    // Log progress every 1MB
                    if (downloadedBytes % (1024 * 1024) == 0L || downloadedBytes == totalBytes) {
                        val progressPercent = if (totalBytes > 0) (downloadedBytes * 100) / totalBytes else 0
                        Log.d(TAG, "Download progress: ${downloadedBytes / (1024 * 1024)}MB / ${totalBytes / (1024 * 1024)}MB ($progressPercent%)")
                    }
                }
                
                outputStream.flush()
                true
                
            } finally {
                inputStream?.close()
                outputStream?.close()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error writing response body to disk", e)
            false
        }
    }
    
    /**
     * Clean up downloaded files
     */
    suspend fun cleanupDownloadedFile(fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, fileName)
                if (file.exists()) {
                    val deleted = file.delete()
                    Log.d(TAG, if (deleted) "✅ Cleaned up file: $fileName" else "❌ Failed to delete file: $fileName")
                    deleted
                } else {
                    Log.d(TAG, "File not found for cleanup: $fileName")
                    true // Consider it cleaned up if it doesn't exist
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up file: $fileName", e)
                false
            }
        }
    }
    
    /**
     * Get download directory
     */
    fun getDownloadDirectory(): File = context.filesDir
}

/**
 * Download result types
 */
sealed class DownloadResult {
    data class Success(val downloadedFile: File) : DownloadResult()
    data class Error(val error: String) : DownloadResult()
}