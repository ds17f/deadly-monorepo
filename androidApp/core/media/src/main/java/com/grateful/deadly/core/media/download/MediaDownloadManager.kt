package com.grateful.deadly.core.media.download

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.grateful.deadly.core.domain.repository.ShowRepository
import com.grateful.deadly.core.model.LibraryDownloadStatus
import com.grateful.deadly.core.model.ShowDownloadProgress
import com.grateful.deadly.core.network.archive.service.ArchiveService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class MediaDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
    @DownloadCache private val cache: Cache,
    private val archiveService: ArchiveService,
    private val showRepository: ShowRepository
) {

    companion object {
        private const val TAG = "MediaDownloadManager"
        private val FORMAT_PRIORITY = listOf("VBR MP3", "MP3", "Ogg Vorbis")
    }

    /**
     * Download all tracks for a show. Uses the provided recordingId, or resolves
     * the best recording automatically.
     */
    suspend fun downloadShow(showId: String, recordingId: String? = null): Result<Unit> {
        return try {
            val resolvedRecordingId = recordingId
                ?: showRepository.getBestRecordingForShow(showId)?.identifier
                ?: return Result.failure(Exception("No recording found for show $showId"))

            Log.d(TAG, "Starting download for show=$showId, recording=$resolvedRecordingId")

            val allTracks = archiveService.getRecordingTracks(resolvedRecordingId)
                .getOrNull()
                ?: return Result.failure(Exception("Failed to fetch tracks for $resolvedRecordingId"))

            val format = FORMAT_PRIORITY.firstOrNull { fmt ->
                allTracks.any { it.format.equals(fmt, ignoreCase = true) }
            } ?: return Result.failure(Exception("No supported audio format found"))

            val audioTracks = allTracks.filter { it.format.equals(format, ignoreCase = true) }
            Log.d(TAG, "Downloading ${audioTracks.size} tracks in '$format' format")

            for (track in audioTracks) {
                val uri = "https://archive.org/download/$resolvedRecordingId/${track.name}"
                val downloadId = "$showId|$resolvedRecordingId|${track.name}"
                val groupData = "$showId|$resolvedRecordingId".toByteArray()

                val request = DownloadRequest.Builder(downloadId, android.net.Uri.parse(uri))
                    .setCustomCacheKey(uri)
                    .setData(groupData)
                    .build()

                DownloadService.sendAddDownload(
                    context,
                    DeadlyMediaDownloadService::class.java,
                    request,
                    false
                )
            }

            Log.d(TAG, "Queued ${audioTracks.size} track downloads for show $showId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading show $showId", e)
            Result.failure(e)
        }
    }

    /**
     * Cancel all in-progress downloads for a show.
     */
    fun cancelShowDownloads(showId: String) {
        val downloads = getDownloadsForShow(showId)
        for (download in downloads) {
            DownloadService.sendRemoveDownload(
                context,
                DeadlyMediaDownloadService::class.java,
                download.request.id,
                false
            )
        }
        Log.d(TAG, "Cancelled ${downloads.size} downloads for show $showId")
    }

    /**
     * Remove all downloads for a show (cancel + clear cache).
     */
    fun removeShowDownloads(showId: String) {
        cancelShowDownloads(showId)
    }

    /**
     * Observe aggregated download progress for a show.
     */
    fun observeShowDownloadProgress(showId: String): Flow<ShowDownloadProgress> = callbackFlow {
        val listener = object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                trySend(computeShowProgress(showId))
            }

            override fun onDownloadRemoved(
                downloadManager: DownloadManager,
                download: Download
            ) {
                trySend(computeShowProgress(showId))
            }
        }

        downloadManager.addListener(listener)

        // Emit current state immediately
        trySend(computeShowProgress(showId))

        awaitClose { downloadManager.removeListener(listener) }
    }.distinctUntilChanged()

    /**
     * Observe any download state change (used to trigger re-evaluation of download statuses).
     * Emits Unit on every onDownloadChanged/onDownloadRemoved callback.
     */
    fun observeDownloadChanges(): Flow<Unit> = callbackFlow {
        val listener = object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                trySend(Unit)
            }

            override fun onDownloadRemoved(
                downloadManager: DownloadManager,
                download: Download
            ) {
                trySend(Unit)
            }
        }

        downloadManager.addListener(listener)
        trySend(Unit) // Emit initial value
        awaitClose { downloadManager.removeListener(listener) }
    }

    /**
     * Get the current download status for a show.
     */
    fun getShowDownloadStatus(showId: String): LibraryDownloadStatus {
        val downloads = getDownloadsForShow(showId)
        if (downloads.isEmpty()) return LibraryDownloadStatus.NOT_DOWNLOADED

        val states = downloads.map { it.state }

        return when {
            states.all { it == Download.STATE_COMPLETED } -> LibraryDownloadStatus.COMPLETED
            states.any { it == Download.STATE_FAILED } -> LibraryDownloadStatus.FAILED
            states.any { it == Download.STATE_DOWNLOADING } -> LibraryDownloadStatus.DOWNLOADING
            states.any { it == Download.STATE_QUEUED } -> LibraryDownloadStatus.QUEUED
            states.any { it == Download.STATE_STOPPED } -> LibraryDownloadStatus.PAUSED
            states.any { it == Download.STATE_REMOVING } -> LibraryDownloadStatus.NOT_DOWNLOADED
            else -> LibraryDownloadStatus.NOT_DOWNLOADED
        }
    }

    /**
     * Total storage used by all downloaded media.
     */
    fun getTotalStorageUsed(): Long {
        return cache.cacheSpace
    }

    /**
     * Storage used by downloads for a specific show.
     */
    fun getShowStorageUsed(showId: String): Long {
        val downloads = getDownloadsForShow(showId)
        return downloads.sumOf { it.bytesDownloaded }
    }

    /**
     * Number of shows that are fully downloaded.
     */
    fun getDownloadedShowCount(): Int {
        val allDownloads = getAllDownloads()
        val showGroups = allDownloads.groupBy { extractShowId(it) }
        return showGroups.count { (_, downloads) ->
            downloads.all { it.state == Download.STATE_COMPLETED }
        }
    }

    /**
     * Check if a specific track URI is cached (downloaded).
     */
    fun isTrackCached(uri: String): Boolean {
        val keys = cache.keys
        return keys.any { it == uri }
    }

    /**
     * Get all show IDs that have any downloads (completed, in-progress, or queued).
     */
    fun getAllDownloadShowIds(): List<String> {
        return getAllDownloads()
            .mapNotNull { extractShowId(it) }
            .distinct()
    }

    /**
     * Extract the recording ID from a download's group data.
     */
    fun extractRecordingId(download: Download): String? {
        val data = download.request.data
        if (data != null && data.isNotEmpty()) {
            val groupString = String(data)
            val parts = groupString.split("|")
            return parts.getOrNull(1)
        }
        return download.request.id.split("|").getOrNull(1)
    }

    /**
     * Get the recording ID associated with downloads for a show.
     */
    fun getRecordingIdForShow(showId: String): String? {
        return getDownloadsForShow(showId).firstOrNull()?.let { extractRecordingId(it) }
    }

    /**
     * Observe all download activity across all shows for reactive UI updates.
     * Emits a map of showId to ShowDownloadProgress on every change.
     */
    fun observeAllDownloads(): Flow<Map<String, ShowDownloadProgress>> = callbackFlow {
        val listener = object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                trySend(computeAllShowProgress())
            }

            override fun onDownloadRemoved(
                downloadManager: DownloadManager,
                download: Download
            ) {
                trySend(computeAllShowProgress())
            }
        }

        downloadManager.addListener(listener)
        trySend(computeAllShowProgress())
        awaitClose { downloadManager.removeListener(listener) }
    }.distinctUntilChanged()

    /**
     * Remove ALL downloads across all shows.
     */
    fun removeAllDownloads() {
        val allDownloads = getAllDownloads()
        for (download in allDownloads) {
            DownloadService.sendRemoveDownload(
                context,
                DeadlyMediaDownloadService::class.java,
                download.request.id,
                false
            )
        }
        Log.d(TAG, "Removed all ${allDownloads.size} downloads")
    }

    private fun computeAllShowProgress(): Map<String, ShowDownloadProgress> {
        val allDownloads = getAllDownloads()
        val showGroups = allDownloads.groupBy { extractShowId(it) }
        return showGroups.mapNotNull { (showId, _) ->
            showId?.let { it to computeShowProgress(it) }
        }.toMap()
    }

    private fun computeShowProgress(showId: String): ShowDownloadProgress {
        val downloads = getDownloadsForShow(showId)
        if (downloads.isEmpty()) {
            return ShowDownloadProgress(
                showId = showId,
                status = LibraryDownloadStatus.NOT_DOWNLOADED,
                overallProgress = 0f,
                downloadedBytes = 0L,
                totalBytes = 0L,
                tracksCompleted = 0,
                tracksTotal = 0
            )
        }

        val downloadedBytes = downloads.sumOf { it.bytesDownloaded }
        val totalBytes = downloads.sumOf { it.contentLength.coerceAtLeast(0) }
        val tracksCompleted = downloads.count { it.state == Download.STATE_COMPLETED }

        // Always use per-track percentDownloaded average — avoids inflated progress
        // when some tracks haven't reported contentLength yet
        val overallProgress = downloads
            .map { it.percentDownloaded / 100f }
            .average()
            .toFloat()

        // Inline status from already-fetched downloads to avoid a second cursor scan
        val states = downloads.map { it.state }
        val status = when {
            states.all { it == Download.STATE_COMPLETED } -> LibraryDownloadStatus.COMPLETED
            states.any { it == Download.STATE_FAILED } -> LibraryDownloadStatus.FAILED
            states.any { it == Download.STATE_DOWNLOADING } -> LibraryDownloadStatus.DOWNLOADING
            states.any { it == Download.STATE_QUEUED } -> LibraryDownloadStatus.QUEUED
            states.any { it == Download.STATE_STOPPED } -> LibraryDownloadStatus.PAUSED
            states.any { it == Download.STATE_REMOVING } -> LibraryDownloadStatus.NOT_DOWNLOADED
            else -> LibraryDownloadStatus.NOT_DOWNLOADED
        }

        return ShowDownloadProgress(
            showId = showId,
            status = status,
            overallProgress = overallProgress.coerceIn(0f, 1f),
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            tracksCompleted = tracksCompleted,
            tracksTotal = downloads.size
        )
    }

    private fun getDownloadsForShow(showId: String): List<Download> {
        return getAllDownloads().filter { extractShowId(it) == showId }
    }

    private fun getAllDownloads(): List<Download> {
        val downloads = mutableListOf<Download>()
        val cursor = downloadManager.downloadIndex.getDownloads()
        while (cursor.moveToNext()) {
            downloads.add(cursor.download)
        }
        cursor.close()
        return downloads
    }

    private fun extractShowId(download: Download): String? {
        val data = download.request.data
        if (data != null && data.isNotEmpty()) {
            val groupString = String(data)
            return groupString.split("|").firstOrNull()
        }
        // Fallback: parse from download ID "showId|recordingId|trackName"
        return download.request.id.split("|").firstOrNull()
    }
}
