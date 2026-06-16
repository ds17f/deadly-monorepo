package com.grateful.deadly.core.database

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide transient toast bus.
 *
 * Any ViewModel can call [show]; the root scaffold collects [messages] and
 * surfaces them through the shared SnackbarHost. Use for lightweight
 * confirmations of actions that have no other on-screen feedback — e.g. the
 * Autoplay toggle, whose only visible change is an icon tint (ADR-0014). Kept
 * deliberately generic so other surfaces can reuse it.
 *
 * Convention: ALL transient, non-actionable confirmations should go through this
 * (not `android.widget.Toast` or ad-hoc snackbars). See
 * `docs/docs/todo/transient-toasts.md`.
 */
@Singleton
class ToastController @Inject constructor() {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun show(message: String) {
        _messages.tryEmit(message)
    }
}

/**
 * Shared copy for the Autoplay toggle confirmation, so the player and playlist
 * surfaces say the same thing (ADR-0014). On = teach what it does; off = terse.
 */
fun autoplayToastMessage(enabled: Boolean): String =
    if (enabled) "Autoplay on — the next show plays when this one ends" else "Autoplay off"
