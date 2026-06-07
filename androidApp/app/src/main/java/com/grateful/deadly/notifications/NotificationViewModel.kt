package com.grateful.deadly.notifications

import androidx.lifecycle.ViewModel
import com.grateful.deadly.core.notifications.CachedNotification
import com.grateful.deadly.core.notifications.NotificationStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Thin UI adapter over [NotificationStore]. The store owns all state and
 * persistence; this just exposes its reactive list and forwards the local
 * mark-seen / dismiss actions.
 */
@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val store: NotificationStore,
) : ViewModel() {

    val notifications: StateFlow<List<CachedNotification>> = store.notifications

    /** Called when the inbox opens — clears the unread badge on this device. */
    fun markAllSeen() = store.markAllSeen()

    fun dismiss(id: Long) = store.dismiss(id)
}
