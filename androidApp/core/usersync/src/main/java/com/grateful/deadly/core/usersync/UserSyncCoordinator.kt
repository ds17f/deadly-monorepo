package com.grateful.deadly.core.usersync

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.grateful.deadly.core.api.auth.AuthService
import com.grateful.deadly.core.api.auth.AuthState
import com.grateful.deadly.core.api.usersync.FavoritesPushService
import com.grateful.deadly.core.api.usersync.UserSyncApplyService
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives sync from app-level signals:
 *  - sign-in (auth state transitions to SignedIn)
 *  - cold start (if already signed in when the app launches)
 *  - foreground (each ON_START of the process lifecycle, if signed in)
 *
 * Each signal both **flushes the outbox** (push local → server) and then
 * **pulls** (server → local). Flushing here is essential: a user who browsed
 * signed-out accumulates favorites in the outbox, and the one-time startup
 * backfill consumes its flag while still signed out, so sign-in is the only
 * remaining moment those rows can be pushed. Pull-only would strand them.
 *
 * FavoritesPushService is injected lazily to break the DI cycle —
 * FavoritesPushServiceImpl already depends on this coordinator (it pulls after
 * a successful flush).
 *
 * [start] is idempotent — first call wires the observers, subsequent calls no-op.
 */
@Singleton
class UserSyncCoordinator @Inject constructor(
    private val authService: AuthService,
    private val applyService: UserSyncApplyService,
    private val favoritesPushService: Lazy<FavoritesPushService>,
) {
    companion object {
        private const val TAG = "UserSyncCoordinator"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var started = false

    fun start() {
        if (started) return
        started = true

        // Sign-in trigger: flush + pull whenever auth transitions into SignedIn.
        // Also covers cold-start, because restoreSession sets the StateFlow's
        // initial value before this collector subscribes.
        scope.launch {
            authService.authState
                .collect { state ->
                    if (state is AuthState.SignedIn) {
                        Log.d(TAG, "auth → SignedIn, flushing then pulling")
                        flushThenPull("sign_in_or_cold_start")
                    }
                }
        }

        // Foreground trigger. ProcessLifecycleOwner emits ON_START when any
        // activity becomes visible after the app was fully backgrounded.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_START) {
                    if (authService.authState.value is AuthState.SignedIn) {
                        Log.d(TAG, "process ON_START while signed in, flushing then pulling")
                        flushThenPull("foreground")
                    }
                }
            }
        })
    }

    /**
     * Push any locally-queued changes, then pull. Flushing first ensures a
     * signed-out browsing session's backlog reaches the server the moment the
     * user signs in (or next foregrounds), instead of waiting for them to
     * happen to toggle a new favorite. flushPending() is a cheap no-op when the
     * outbox is empty or the user isn't signed in.
     */
    private fun flushThenPull(reason: String) {
        scope.launch {
            try {
                favoritesPushService.get().flushPending()
            } catch (e: Exception) {
                Log.w(TAG, "flush[$reason] failed: ${e.message}")
            }
            triggerPull(reason)
        }
    }

    /** Public hook so FavoritesPushServiceImpl can fire a pull after a flush. */
    fun triggerPull(reason: String) {
        scope.launch {
            val result = applyService.pullAndApply()
            result.fold(
                onSuccess = { Log.d(TAG, "pull[$reason] ok: $it") },
                onFailure = { Log.w(TAG, "pull[$reason] failed: ${it.message}") },
            )
        }
    }
}
