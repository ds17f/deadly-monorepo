package com.grateful.deadly.core.notifications

import android.util.Log
import com.grateful.deadly.core.api.auth.AuthService
import com.grateful.deadly.core.database.AnalyticsService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pulls the notifications feed on app-level signals:
 *  - cold start (when [start] is first called)
 *  - foreground (each ON_START of the process lifecycle)
 *
 * Unlike [com.grateful.deadly.core.usersync.UserSyncCoordinator], this fires
 * **unconditionally** — the feed is public/global, so it isn't gated on auth.
 * Each pull is a `?since=<cursor>` delta merged into [NotificationStore].
 *
 * [start] is idempotent — the first call wires the observer + does the cold
 * fetch; later calls no-op.
 */
@Singleton
class NotificationCoordinator @Inject constructor(
    private val apiService: NotificationApiService,
    private val store: NotificationStore,
    private val analytics: AnalyticsService,
    private val authService: AuthService,
) {
    companion object {
        private const val TAG = "NotificationCoord"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var started = false

    fun start() {
        if (started) return
        started = true

        // Eager-push local read/dismiss mutations to the server (ADR-0015).
        store.onStateChange = ::pushStateChange

        // Cold start: pull once now.
        pull("cold_start")

        // Foreground trigger. ProcessLifecycleOwner emits ON_START when any
        // activity becomes visible after the app was fully backgrounded.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_START) {
                    pull("foreground")
                }
            }
        })
    }

    private fun pull(reason: String) {
        scope.launch {
            apiService.fetch(store.cursor).fold(
                onSuccess = { result ->
                    // For a signed-in user, pull the read/dismiss overlay and merge
                    // it atomically with the feed so already-handled messages never
                    // flash unread (ADR-0015 state-before-surface).
                    val signedIn = authService.getAuthToken() != null
                    val serverState = if (signedIn) {
                        apiService.pullState().getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }

                    val fresh = store.merge(result, serverState)
                    fresh.forEach { m -> analytics.trackNotificationReceived(m, reason) }

                    // Offline catch-up: flush any local state the server is missing
                    // (an eager push that failed while offline). No-op once converged.
                    if (signedIn) {
                        for (row in store.unsyncedState(serverState)) {
                            apiService.pushState(row.notificationId, row.seenAt, row.dismissedAt)
                        }
                    }

                    Log.d(TAG, "pull[$reason] ok: +${result.messages.size} (cursor=${store.cursor})")
                },
                onFailure = { Log.w(TAG, "pull[$reason] failed: ${it.message}") },
            )
        }
    }

    /**
     * Eager push of a local read/dismiss mutation (ADR-0015). Fire-and-forget on
     * the IO scope; failures are caught by the focus-refresh catch-up flush.
     * No-op when signed out (state stays local, like the rest of user data).
     */
    private fun pushStateChange(change: NotificationStateChange) {
        if (authService.getAuthToken() == null) return
        val nowSec = System.currentTimeMillis() / 1000
        scope.launch {
            when (change) {
                is NotificationStateChange.Seen ->
                    apiService.pushState(change.id, seenAt = nowSec, dismissedAt = null)
                is NotificationStateChange.Dismissed ->
                    apiService.pushState(change.id, seenAt = null, dismissedAt = nowSec)
                NotificationStateChange.SeenAll ->
                    apiService.pushStateBulk(seenAt = nowSec, dismissedAt = null, ids = null)
                NotificationStateChange.DismissedAll ->
                    apiService.pushStateBulk(seenAt = null, dismissedAt = nowSec, ids = null)
            }
        }
    }
}
