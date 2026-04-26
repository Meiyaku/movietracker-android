package com.ycs.movietracker.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetryUtilsTest {

    @Test
    fun `returns result on first success`() = runTest {
        var callCount = 0
        val result = retryWithBackoff(isRetryable = { true }) {
            callCount++
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(1, callCount)
    }

    @Test
    fun `retries on retryable failure and succeeds on second attempt`() = runTest {
        var callCount = 0
        val result = retryWithBackoff(isRetryable = { true }) {
            callCount++
            if (callCount < 2) throw RuntimeException("transient")
            "recovered"
        }
        assertEquals("recovered", result)
        assertEquals(2, callCount)
    }

    @Test
    fun `succeeds on final attempt after two retryable failures`() = runTest {
        var callCount = 0
        val result = retryWithBackoff(maxAttempts = 3, isRetryable = { true }) {
            callCount++
            if (callCount < 3) throw RuntimeException("transient")
            "recovered on third"
        }
        assertEquals("recovered on third", result)
        assertEquals(3, callCount)
    }

    @Test
    fun `non-retryable exception aborts immediately`() = runTest {
        var callCount = 0
        try {
            retryWithBackoff(isRetryable = { false }) {
                callCount++
                throw IllegalStateException("permanent")
            }
            fail("Expected IllegalStateException to be thrown")
        } catch (e: IllegalStateException) {
            assertEquals("permanent", e.message)
        }
        assertEquals(1, callCount)
    }

    @Test
    fun `exhausts all attempts and rethrows last exception`() = runTest {
        var callCount = 0
        try {
            retryWithBackoff(maxAttempts = 3, isRetryable = { true }) {
                callCount++
                throw RuntimeException("always fails attempt $callCount")
            }
            fail("Expected RuntimeException to be thrown")
        } catch (e: RuntimeException) {
            assertEquals("always fails attempt 3", e.message)
        }
        assertEquals(3, callCount)
    }

    @Test
    fun `backoff delay grows exponentially`() = runTest(StandardTestDispatcher()) {
        var callCount = 0
        try {
            retryWithBackoff(
                maxAttempts = 3,
                initialDelay = 500L,
                factor = 2.0,
                maxDelay = 2000L,
                isRetryable = { true }
            ) {
                callCount++
                throw RuntimeException("fail")
            }
        } catch (_: RuntimeException) {}
        advanceUntilIdle()
        // Attempt 1 fails → delay 500ms, attempt 2 fails → delay 1000ms, attempt 3 fails
        assertEquals(1500L, currentTime)
    }
}
