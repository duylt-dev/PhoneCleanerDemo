package com.pion.phonecleaner.data.network

import com.pion.phonecleaner.core.common.time.AppClock
import com.pion.phonecleaner.domain.model.network.TrafficPeriod
import java.util.Calendar

/**
 * Turns a [TrafficPeriod] into the millisecond window `NetworkStatsManager` is asked for.
 *
 * It lives in `:data` and not in `:domain` because "this month" needs a **time zone**, and a time
 * zone is a platform reading. The competitor computes the same three windows inside its engine
 * (`docs/reverse-engineering/19-network-and-speed-test.md` :473-479); the arithmetic is reproduced
 * because it is the correct arithmetic, not because it is theirs.
 *
 * `now` comes from the injected [AppClock] and never from `System.currentTimeMillis()`, so a test can
 * pin the month boundary instead of being green only between the 2nd and the 28th.
 */
internal class TrafficWindow(private val clock: AppClock) {

    data class Range(val startMillis: Long, val endMillis: Long)

    fun of(period: TrafficPeriod): Range {
        val now = clock.now().toEpochMilliseconds()
        val start = when (period) {
            TrafficPeriod.Last24Hours -> now - DAY_MILLIS
            TrafficPeriod.Last30Days -> now - THIRTY_DAYS_MILLIS
            TrafficPeriod.ThisMonth -> startOfMonth(now)
        }
        return Range(startMillis = start.coerceAtMost(now), endMillis = now)
    }

    /** Midnight on the 1st, in the device's default time zone — `Calendar` is what knows that. */
    private fun startOfMonth(nowMillis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
        const val THIRTY_DAYS_MILLIS = 30L * DAY_MILLIS
    }
}
