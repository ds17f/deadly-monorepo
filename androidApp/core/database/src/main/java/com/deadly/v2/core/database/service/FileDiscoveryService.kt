package com.deadly.v2.core.database.service

import android.content.Context
import android.util.Log
import com.deadly.v2.core.network.github.service.GitHubDataService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileDiscoveryService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gitHubDataService: GitHubDataService
) {
    
    companion object {
        private const val TAG = "FileDiscoveryService"
    }
    
    data class LocalFile(
        val file: File,
        val type: FileType,
        val sizeBytes: Long
    )
    
    data class RemoteFile(
        val name: String,
        val downloadUrl: String,
        val type: FileType,
        val sizeBytes: Long
    )
    
    enum class FileType {
        DATA_ZIP,    // data*.zip files
        DATABASE_ZIP // *db*.zip files  
    }
    
    data class DiscoveryResult(
        val localFiles: List<LocalFile>,
        val remoteFiles: List<RemoteFile>
    )
    
    /**
     * Discover all available local and remote files
     */
    suspend fun discoverAvailableFiles(): DiscoveryResult {
        Log.d(TAG, "Starting file discovery...")
        
        val localFiles = findLocalFiles()
        val remoteFiles = findRemoteFiles()
        
        Log.d(TAG, "Discovery complete: ${localFiles.size} local files, ${remoteFiles.size} remote files")
        
        return DiscoveryResult(localFiles, remoteFiles)
    }
    
    /**
     * Find local files in the app's files directory
     */
    private fun findLocalFiles(): List<LocalFile> {
        Log.d(TAG, "Searching for local files in ${context.filesDir}")
        
        val localFiles = mutableListOf<LocalFile>()
        
        try {
            val filesDir = context.filesDir
            if (!filesDir.exists()) {
                Log.d(TAG, "Files directory does not exist")
                return emptyList()
            }
            
            val files = filesDir.listFiles() ?: emptyArray()
            Log.d(TAG, "Found ${files.size} files in directory")
            
            for (file in files) {
                if (!file.isFile) continue
                
                val fileName = file.name.lowercase()
                val fileType = when {
                    fileName.startsWith("data") && fileName.endsWith(".zip") -> {
                        Log.d(TAG, "Found local data file: ${file.name} (${file.length()} bytes)")
                        FileType.DATA_ZIP
                    }
                    fileName.contains("db") && fileName.endsWith(".zip") -> {
                        Log.d(TAG, "Found local database file: ${file.name} (${file.length()} bytes)")
                        FileType.DATABASE_ZIP
                    }
                    else -> continue
                }
                
                localFiles.add(LocalFile(file, fileType, file.length()))
            }
            
            // Ensure we only have one of each type
            val dataFiles = localFiles.filter { it.type == FileType.DATA_ZIP }
            val dbFiles = localFiles.filter { it.type == FileType.DATABASE_ZIP }
            
            if (dataFiles.size > 1) {
                Log.w(TAG, "Multiple data files found (${dataFiles.size}), this may cause confusion")
            }
            if (dbFiles.size > 1) {
                Log.w(TAG, "Multiple database files found (${dbFiles.size}), this may cause confusion")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error searching for local files", e)
        }
        
        return localFiles
    }
    
    /**
     * Find remote files from GitHub releases
     */
    private suspend fun findRemoteFiles(): List<RemoteFile> {
        Log.d(TAG, "Searching for remote files from GitHub releases")
        
        return try {
            val release = gitHubDataService.getLatestRelease()
            if (release == null) {
                Log.w(TAG, "No GitHub release found")
                return emptyList()
            }
            
            Log.d(TAG, "Found release: ${release.tagName} with ${release.assets.size} assets")
            
            val remoteFiles = mutableListOf<RemoteFile>()
            
            for (asset in release.assets) {
                val fileName = asset.name.lowercase()
                val fileType = when {
                    fileName.startsWith("data") && fileName.endsWith(".zip") -> {
                        Log.d(TAG, "Found remote data file: ${asset.name} (${asset.size} bytes)")
                        FileType.DATA_ZIP
                    }
                    fileName.contains("db") && fileName.endsWith(".zip") -> {
                        Log.d(TAG, "Found remote database file: ${asset.name} (${asset.size} bytes)")
                        FileType.DATABASE_ZIP
                    }
                    else -> {
                        Log.d(TAG, "Skipping non-matching asset: ${asset.name}")
                        continue
                    }
                }
                
                remoteFiles.add(RemoteFile(asset.name, asset.browserDownloadUrl, fileType, asset.size))
            }
            
            remoteFiles
        } catch (e: Exception) {
            Log.e(TAG, "Error searching for remote files", e)
            emptyList()
        }
    }
    
    /**
     * Get the download directory for remote files (same as local files directory)
     */
    fun getDownloadDirectory(): File {
        return context.filesDir
    }
    
    /**
     * Check if a specific file type is available locally
     */
    suspend fun hasLocalFile(type: FileType): Boolean {
        val localFiles = findLocalFiles()
        return localFiles.any { it.type == type }
    }
    
    /**
     * Check if a specific file type is available remotely
     */
    suspend fun hasRemoteFile(type: FileType): Boolean {
        val remoteFiles = findRemoteFiles()
        return remoteFiles.any { it.type == type }
    }
}