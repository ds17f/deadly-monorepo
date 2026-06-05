package com.grateful.deadly.core.usersync

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.grateful.deadly.core.api.auth.AuthService
import com.grateful.deadly.core.api.auth.AuthState
import com.grateful.deadly.core.api.usersync.UserSyncApplyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives [UserSyncApplyService.pullAndApply] from app-level signals:
 *  - sign-in (auth state transitions to SignedIn)
 *  - cold start (if already signed in when the app launches)
 *  - foreground (each ON_RESUME of the process lifecycle, if signed in)
 *
 * The "after a successful push flush" trigger lives inside FavoritesPushServiceImpl,
 * because that's the only place that knows when a flush has produced results.
 *
 * [start] is idempotent — first call wires the observers, subsequent calls no-op.
 */
@Singleton
class UserSyncCoordinator @Inject constructor(
    private val authService: AuthService,
    private val applyService: UserSyncApplyService,
) {
    companion object {
        private const val TAG = "UserSyncCoordinator"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var started = false

    fun start() {
        if (started) return
        started = true

        // Sign-in trigger: re-pull whenever auth transitions into SignedIn.
        // Also covers cold-start, because restoreSession sets the StateFlow's
        // initial value before this collector subscribes.
        scope.launch {
            authService.authState
                .collect { state ->
                    if (state is AuthState.SignedIn) {
                        Log.d(TAG, "auth → SignedIn, triggering pull")
                        triggerPull("sign_in_or_cold_start")
                    }
                }
        }

        // Foreground trigger. ProcessLifecycleOwner emits ON_START when any
        // activity becomes visible after the app was fully backgrounded.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_START) {
                    if (authService.authState.value is AuthState.SignedIn) {
                        Log.d(TAG, "process ON_START while signed in, triggering pull")
                        triggerPull("foreground")
                    }
                }
            }
        })
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
