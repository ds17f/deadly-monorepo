package com.grateful.deadly.feature.upnext

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grateful.deadly.core.api.playlist.PlaylistService
import com.grateful.deadly.core.database.ToastController
import com.grateful.deadly.core.domain.repository.BacklogRepository
import com.grateful.deadly.core.domain.repository.ShowRepository
import com.grateful.deadly.core.model.Show
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Up Next (the backlog). Single source of truth for the list UI, hosted by both
 * the standalone [UpNextScreen] (menu push) and the Favorites "Up Next" tab via
 * [UpNextList]. Observes the local-first backlog and resolves show ids to [Show].
 * No advance wiring (slice 3) or sync (slice 4).
 */
@HiltViewModel
class UpNextViewModel @Inject constructor(
    private val backlogRepository: BacklogRepository,
    private val showRepository: ShowRepository,
    private val playlistService: PlaylistService,
    private val toastController: ToastController,
) : ViewModel() {

    /** The backlog in play order, resolved to shows (head first). */
    val shows: StateFlow<List<Show>> = backlogRepository.observeShowIds()
        .map { ids -> ids.mapNotNull { showRepository.getShowById(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Play a show now (an ordinary play); it stays in the backlog until it ends. */
    fun play(show: Show) {
        viewModelScope.launch { playlistService.playShow(show) }
    }

    fun remove(showId: String) {
        viewModelScope.launch { backlogRepository.remove(showId) }
    }

    /** Persist a new order (the full list of show ids, head first). */
    fun reorder(orderedShowIds: List<String>) {
        viewModelScope.launch { backlogRepository.reorder(orderedShowIds) }
    }

    fun clear() {
        viewModelScope.launch {
            backlogRepository.clear()
            toastController.show("Up Next cleared")
        }
    }
}
