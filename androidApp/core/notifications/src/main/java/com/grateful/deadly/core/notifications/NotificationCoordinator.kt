package com.grateful.deadly.core.notifications

import android.util.Log
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
) {
    companion object {
        private const val TAG = "NotificationCoord"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var started = false

    fun start() {
        if (started) return
        started = true

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
                onSuccess = {
                    store.merge(it)
                    Log.d(TAG, "pull[$reason] ok: +${it.messages.size} (cursor=${store.cursor})")
                },
                onFailure = { Log.w(TAG, "pull[$reason] failed: ${it.message}") },
            )
        }
    }
}
