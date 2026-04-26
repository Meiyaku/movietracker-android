package com.ycs.movietracker.util

import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Executes [block] up to [maxAttempts] times, waiting between failures with exponential backoff.
 *
 * The first [maxAttempts] - 1 attempts are inside a `repeat` loop; any retryable exception delays
 * and retries. The final attempt runs outside the loop so a non-retryable (or exhausted) exception
 * propagates naturally to the caller rather than being swallowed.
 *
 * Non-retryable exceptions (per [isRetryable]) are always rethrown immediately without delay.
 */
suspend fun <T> retryWithBackoff(
    maxAttempts: Int = 3,
    initialDelay: Long = AppConfig.RETRY_INITIAL_DELAY_MS,
    factor: Double = AppConfig.RETRY_BACKOFF_FACTOR,
    maxDelay: Long = AppConfig.RETRY_MAX_DELAY_MS,
    isRetryable: (Throwable) -> Boolean = Throwable::isRetryable,
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    repeat(maxAttempts - 1) {
        try {
            return block()
        } catch (e: Throwable) {
            if (!isRetryable(e)) throw e
            Timber.w(e, "Retryable failure (attempt ${it + 1}/$maxAttempts), retrying in ${currentDelay}ms")
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
    }
    return block() // final attempt — exception propagates naturally
}
