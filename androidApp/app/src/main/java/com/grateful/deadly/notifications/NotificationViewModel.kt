package com.grateful.deadly.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grateful.deadly.core.database.AnalyticsService
import com.grateful.deadly.core.notifications.CachedNotification
import com.grateful.deadly.core.notifications.NewArrival
import com.grateful.deadly.core.notifications.NotificationCoordinator
import com.grateful.deadly.core.notifications.NotificationStore
import com.grateful.deadly.core.notifications.active
import com.grateful.deadly.core.notifications.dismissed
import com.grateful.deadly.core.notifications.trackNotificationArchive
import com.grateful.deadly.core.notifications.trackNotificationArchiveAll
import com.grateful.deadly.core.notifications.trackNotificationCommunityTap
import com.grateful.deadly.core.notifications.trackNotificationImpression
import com.grateful.deadly.core.notifications.trackNotificationLinkTap
import com.grateful.deadly.core.notifications.trackNotificationMarkAllRead
import com.grateful.deadly.core.notifications.trackNotificationOpen
import com.grateful.deadly.core.notifications.trackNotificationReceived
import com.grateful.deadly.core.notifications.trackNotificationToastShown
import com.grateful.deadly.core.notifications.trackNotificationToastTap
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
    private val coordinator: NotificationCoordinator,
    private val analytics: AnalyticsService,
) : ViewModel() {

    private val version = store.appVersion

    /** Ids already counted as an impression this session, to dedupe re-scrolls. */
    private val impressed = mutableSetOf<Long>()

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

    /** Pull-to-refresh: full sync (feed + overlay + flush) with the spinner. */
    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            coordinator.sync("refresh")
            _refreshing.value = false
        }
    }

    /** Opening the inbox triggers a full sync, silently (no pull spinner) — so
     *  the list reflects cross-device read/dismiss state and retirements the
     *  moment it's shown. */
    fun syncOnOpen() {
        viewModelScope.launch { coordinator.sync("inbox_open") }
    }

    /** A message row became visible in the inbox — count one impression per id. */
    fun onImpression(message: CachedNotification) {
        if (impressed.add(message.id)) analytics.trackNotificationImpression(message)
    }

    /** "Tap to read" — opening a message's detail marks just that one read. */
    fun open(message: CachedNotification) {
        analytics.trackNotificationOpen(message)
        if (message.seenAt == null) store.markRead(message.id)
    }

    /** Bulk "Mark all read". */
    fun markAllSeen() {
        analytics.trackNotificationMarkAllRead(active.value.count { it.seenAt == null })
        store.markAllSeen()
    }

    /** Archive a single message (row swipe / detail button). */
    fun archive(message: CachedNotification) {
        analytics.trackNotificationArchive(message)
        store.dismiss(message.id)
    }

    /** Bulk "Archive all". */
    fun archiveAll() {
        analytics.trackNotificationArchiveAll(active.value.size)
        store.archiveAll()
    }

    fun onLinkTap(id: Long, url: String) = analytics.trackNotificationLinkTap(id, url)

    fun onCommunityTap() = analytics.trackNotificationCommunityTap()

    fun onToastShown(arrival: NewArrival) = analytics.trackNotificationToastShown(arrival)

    fun onToastTap(arrival: NewArrival) = analytics.trackNotificationToastTap(arrival)
}
