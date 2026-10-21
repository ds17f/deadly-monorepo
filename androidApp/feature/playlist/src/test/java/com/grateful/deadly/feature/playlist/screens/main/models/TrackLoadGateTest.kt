package com.grateful.deadly.feature.playlist.screens.main.models

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackLoadGateTest {

    private fun TestScope.gate() = TrackLoadGate(this, retryCooldownMs = 3_000L)

    @Test
    fun `failure sets failed state and starts cooldown`() = runTest {
        val gate = gate()
        gate.load { false }
        assertTrue(gate.status.isLoading)
        runCurrent()

        assertTrue(gate.status.failed)
        assertFalse(gate.status.isLoading)
        assertTrue(gate.status.isRetryCoolingDown)
        assertFalse(gate.status.canRetry)
    }

    @Test
    fun `retry keeps failed state while in flight, normal load clears it`() = runTest {
        val gate = gate()
        gate.load { false }
        runCurrent()
        advanceTimeBy(3_000L)
        runCurrent()

        val pending = CompletableDeferred<Boolean>()
        assertTrue(gate.retry { pending.await() })
        runCurrent()
        assertTrue(gate.status.isLoading)
        assertTrue(gate.status.failed)

        pending.complete(true)
        runCurrent()
        assertFalse(gate.status.failed)

        gate.load { false }
        runCurrent()
        advanceTimeBy(3_000L)
        runCurrent()
        val pendingLoad = CompletableDeferred<Boolean>()
        gate.load { pendingLoad.await() }
        runCurrent()
        assertTrue(gate.status.isLoading)
        assertFalse(gate.status.failed)
        pendingLoad.complete(true)
    }

    @Test
    fun `exception in fetch is treated as failure`() = runTest {
        val gate = gate()
        gate.load { throw RuntimeException("SocketTimeoutException") }
        runCurrent()

        assertTrue(gate.status.failed)
    }

    @Test
    fun `retry is ignored during cooldown and allowed after it`() = runTest {
        val gate = gate()
        var calls = 0
        val failing: suspend () -> Boolean = { calls++; false }

        gate.load(failing)
        runCurrent()
        assertEquals(1, calls)

        advanceTimeBy(2_999L)
        runCurrent()
        assertFalse(gate.retry(failing))
        assertEquals(1, calls)

        advanceTimeBy(1L)
        runCurrent()
        assertTrue(gate.status.canRetry)
        assertTrue(gate.retry(failing))
        runCurrent()
        assertEquals(2, calls)
        // Cooldown restarts after each failure
        assertTrue(gate.status.isRetryCoolingDown)
    }

    @Test
    fun `rapid retry taps start only one request while in flight`() = runTest {
        val gate = gate()
        gate.load { false }
        runCurrent()
        advanceTimeBy(3_000L)
        runCurrent()

        var calls = 0
        val pending = CompletableDeferred<Boolean>()
        val fetch: suspend () -> Boolean = { calls++; pending.await() }

        val started = (1..10).map { gate.retry(fetch) }
        runCurrent()

        assertEquals(listOf(true) + List(9) { false }, started)
        assertEquals(1, calls)
        assertTrue(gate.status.isLoading)

        pending.complete(true)
        runCurrent()
        assertEquals(TrackLoadStatus(), gate.status)
    }

    @Test
    fun `retry is not allowed when there was no failure`() = runTest {
        val gate = gate()
        gate.load { true }
        runCurrent()

        assertFalse(gate.status.failed)
        assertFalse(gate.retry { true })
    }

    @Test
    fun `new load cancels in-flight fetch and its result is ignored`() = runTest {
        val gate = gate()
        val first = CompletableDeferred<Boolean>()
        gate.load { first.await() }
        runCurrent()

        gate.load { true }
        runCurrent()
        first.complete(false)
        runCurrent()

        assertEquals(TrackLoadStatus(), gate.status)
    }

    @Test
    fun `status changes are reported to the callback`() = runTest {
        val seen = mutableListOf<TrackLoadStatus>()
        val gate = TrackLoadGate(this, retryCooldownMs = 3_000L) { seen += it }
        gate.load { false }
        runCurrent()
        advanceTimeBy(3_000L)
        runCurrent()

        assertEquals(
            listOf(
                TrackLoadStatus(isLoading = true),
                TrackLoadStatus(failed = true, isRetryCoolingDown = true),
                TrackLoadStatus(failed = true, isRetryCoolingDown = false)
            ),
            seen
        )
    }
}
