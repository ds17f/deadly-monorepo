package com.grateful.deadly.feature.downloads.screens.main.models

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.grateful.deadly.core.database.dao.LibraryDao
import com.grateful.deadly.core.domain.repository.ShowRepository
import com.grateful.deadly.core.media.download.MediaDownloadManager
import com.grateful.deadly.core.model.AppDatabase
import com.grateful.deadly.core.model.DownloadedShowViewModel
import com.grateful.deadly.core.model.DownloadsUiState
import com.grateful.deadly.core.model.LibraryDownloadStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@UnstableApi
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val mediaDownloadManager: MediaDownloadManager,
    private val showRepository: ShowRepository,
    @AppDatabase private val libraryDao: LibraryDao
) : ViewModel() {

    companion object {
        private const val TAG = "DownloadsViewModel"
    }

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    // Caches to avoid DB queries on every download tick
    private val showCache = ConcurrentHashMap<String, ShowMetadata>()

    private data class ShowMetadata(
        val displayDate: String,
        val venue: String,
        val location: String,
        val recordingId: String?,
        val coverImageUrl: String?
    )

    init {
        observeDownloads()
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            mediaDownloadManager.observeAllDownloads()
                .debounce(300)
                .collect { progressMap ->
                    Log.d(TAG, "Download state changed: ${progressMap.size} shows")

                    val activeDownloads = mutableListOf<DownloadedShowViewModel>()
                    val pausedDownloads = mutableListOf<DownloadedShowViewModel>()
                    val completedDownloads = mutableListOf<DownloadedShowViewModel>()

                    for ((showId, progress) in progressMap) {
                        val meta = showCache.getOrPut(showId) { loadShowMetadata(showId) }

                        val viewModel = DownloadedShowViewModel(
                            showId = showId,
                            displayDate = meta.displayDate,
                            venue = meta.venue,
                            location = meta.location,
                            storageBytes = progress.downloadedBytes,
                            status = progress.status,
                            progress = progress,
                            recordingId = meta.recordingId,
                            coverImageUrl = meta.coverImageUrl
                        )

                        when (progress.status) {
                            LibraryDownloadStatus.COMPLETED -> completedDownloads.add(viewModel)
                            LibraryDownloadStatus.DOWNLOADING,
                            LibraryDownloadStatus.QUEUED -> activeDownloads.add(viewModel)
                            LibraryDownloadStatus.PAUSED -> pausedDownloads.add(viewModel)
                            else -> {} // FAILED/CANCELLED/NOT_DOWNLOADED — don't show
                        }
                    }

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        totalStorageUsed = mediaDownloadManager.getTotalStorageUsed(),
                        activeDownloads = activeDownloads,
                        pausedDownloads = pausedDownloads,
                        completedDownloads = completedDownloads
                    )
                }
        }
    }

    private suspend fun loadShowMetadata(showId: String): ShowMetadata {
        val show = showRepository.getShowById(showId)
        val libraryEntity = libraryDao.getLibraryShowById(showId)
        val recordingId = libraryEntity?.downloadedRecordingId
            ?: mediaDownloadManager.getRecordingIdForShow(showId)

        return ShowMetadata(
            displayDate = show?.let { formatDisplayDate(it.date) } ?: showId,
            venue = show?.venue?.name ?: "",
            location = show?.location?.displayText ?: "",
            recordingId = recordingId,
            coverImageUrl = show?.coverImageUrl
        )
    }

    fun cancelDownload(showId: String) {
        mediaDownloadManager.cancelShowDownloads(showId)
    }

    fun pauseDownload(showId: String) {
        mediaDownloadManager.pauseShowDownloads(showId)
    }

    fun resumeDownload(showId: String) {
        mediaDownloadManager.resumeShowDownloads(showId)
    }

    fun removeDownload(showId: String) {
        mediaDownloadManager.removeShowDownloads(showId)
        showCache.remove(showId)
    }

    fun showRemoveAllDialog() {
        _uiState.value = _uiState.value.copy(showRemoveAllDialog = true)
    }

    fun dismissRemoveAllDialog() {
        _uiState.value = _uiState.value.copy(showRemoveAllDialog = false)
    }

    fun removeAllDownloads() {
        mediaDownloadManager.removeAllDownloads()
        showCache.clear()
        _uiState.value = _uiState.value.copy(showRemoveAllDialog = false)
    }

    private fun formatDisplayDate(date: String): String {
        return try {
            val parts = date.split("-")
            val year = parts[0]
            val month = parts[1].toInt()
            val day = parts[2].toInt()
            val monthNames = arrayOf(
                "", "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"
            )
            "${monthNames[month]} $day, $year"
        } catch (e: Exception) {
            date
        }
    }
}
