package com.pion.phonecleaner.core.common.policy

import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Holds a fast operation on screen for at least [floor], so a scan that finishes in 40 ms does not
 * flash. A class, not a top-level function, precisely so a test can inject [Duration.ZERO] —
 * a top-level function cannot be overridden (LLM.md §3.2).
 */
class MinimumDuration(private val floor: Duration) {

    suspend fun <T> around(block: suspend () -> T): T {
        val start = TimeSource.Monotonic.markNow()
        val result = block()
        val remaining = floor - start.elapsedNow()
        if (remaining.isPositive()) delay(remaining)
        return result
    }
}
