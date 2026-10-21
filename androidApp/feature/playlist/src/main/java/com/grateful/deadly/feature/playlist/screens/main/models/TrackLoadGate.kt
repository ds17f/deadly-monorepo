package com.grateful.deadly.feature.playlist.screens.main.models

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Status of the track-list fetch for the current recording.
 */
data class TrackLoadStatus(
    val isLoading: Boolean = false,
    val failed: Boolean = false,
    val isRetryCoolingDown: Boolean = false
) {
    /** Retry is allowed only after a failure, with no fetch in flight and the cooldown ended. */
    val canRetry: Boolean get() = failed && !isLoading && !isRetryCoolingDown
}

/**
 * Runs track-list fetches and debounces user retries.
 *
 * - [load] always starts a new fetch (navigation, recording change). It cancels
 *   any fetch that is still in flight.
 * - [retry] is ignored while a fetch is in flight, and for [retryCooldownMs]
 *   after each failure. Rapid taps can never start more than one request.
 *
 * All calls are expected on one thread (the main thread in the ViewModel), so the
 * status check in [retry] and the status change in [load] are atomic for the caller.
 */
class TrackLoadGate(
    private val scope: CoroutineScope,
    private val retryCooldownMs: Long = DEFAULT_RETRY_COOLDOWN_MS,
    private val onStatusChange: (TrackLoadStatus) -> Unit = {}
) {
    companion object {
        const val DEFAULT_RETRY_COOLDOWN_MS = 3_000L
    }

    var status: TrackLoadStatus = TrackLoadStatus()
        private set(value) {
            field = value
            onStatusChange(value)
        }

    private var loadJob: Job? = null
    private var cooldownJob: Job? = null

    /**
     * Start a fetch. [fetch] returns true on success and false on failure.
     */
    fun load(fetch: suspend () -> Boolean) = start(fetch, keepFailed = false)

    /**
     * Retry after a failure. Returns true if a fetch started, false if the tap was ignored.
     * [TrackLoadStatus.failed] stays true while the retry runs, so the error block stays
     * in place (and the list keeps its scroll position).
     */
    fun retry(fetch: suspend () -> Boolean): Boolean {
        if (!status.canRetry) return false
        start(fetch, keepFailed = true)
        return true
    }

    private fun start(fetch: suspend () -> Boolean, keepFailed: Boolean) {
        loadJob?.cancel()
        cooldownJob?.cancel()
        status = TrackLoadStatus(isLoading = true, failed = keepFailed && status.failed)
        loadJob = scope.launch {
            val succeeded = try {
                fetch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            // A newer load() may have cancelled this one; do not overwrite its status.
            ensureActive()
            if (succeeded) {
                status = TrackLoadStatus()
            } else {
                status = TrackLoadStatus(failed = true, isRetryCoolingDown = true)
                cooldownJob = scope.launch {
                    delay(retryCooldownMs)
                    status = status.copy(isRetryCoolingDown = false)
                }
            }
        }
    }
}
