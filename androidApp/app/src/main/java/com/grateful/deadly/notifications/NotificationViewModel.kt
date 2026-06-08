package com.grateful.deadly.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grateful.deadly.core.notifications.CachedNotification
import com.grateful.deadly.core.notifications.NewArrival
import com.grateful.deadly.core.notifications.NotificationApiService
import com.grateful.deadly.core.notifications.NotificationStore
import com.grateful.deadly.core.notifications.active
import com.grateful.deadly.core.notifications.dismissed
import com.grateful.deadly.core.notifications.unreadCount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Thin UI adapter over [NotificationStore]. The store owns all state and
 * persistence; this exposes eligibility-filtered (decision E) views and
 * forwards the local read/archive actions.
 */
@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val store: NotificationStore,
    private val apiService: NotificationApiService,
) : ViewModel() {

    private val version = store.appVersion

    private val _refreshing = MutableStateFlow(false)
    /** Drives the pull-to-refresh spinner on the inbox screen. */
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    val active: StateFlow<List<CachedNotification>> =
        store.notifications
            .map { it.active(version) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val archived: StateFlow<List<CachedNotification>> =
        store.notifications
            .map { it.dismissed(version) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unread: StateFlow<Int> =
        store.notifications
            .map { it.unreadCount(version) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Emits when a delta brings new eligible unread messages — for the toast. */
    val newArrivals = store.newArrivals

    /** Pull-to-refresh: fetch a `?since` delta and merge it. */
    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            apiService.fetch(store.cursor).onSuccess { store.merge(it) }
            _refreshing.value = false
        }
    }

    /** "Tap to read" — opening a message's detail marks just that one read. */
    fun markRead(id: Long) = store.markRead(id)

    /** Bulk "Mark all read". */
    fun markAllSeen() = store.markAllSeen()

    fun dismiss(id: Long) = store.dismiss(id)

    /** Bulk "Archive all". */
    fun archiveAll() = store.archiveAll()
}
