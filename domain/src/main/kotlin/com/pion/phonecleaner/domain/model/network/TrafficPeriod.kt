package com.pion.phonecleaner.domain.model.network

/**
 * The window a [TrafficReport] covers.
 *
 * The three constants are the competitor's `d.EnumC0102d` (`docs/reverse-engineering/19-network-and-speed-test.md`
 * :471) renamed to this project's convention. The **window arithmetic** for each one is a data-layer
 * concern and lives in `data/network/TrafficWindow.kt`; putting it here would need a time zone, and
 * `:domain` holds no platform.
 */
enum class TrafficPeriod {
    /** Midnight on the 1st of the current month, in the device's own time zone, until now. */
    ThisMonth,

    /** Exactly 30 × 24 h back from now. */
    Last30Days,

    /** Exactly 24 h back from now. */
    Last24Hours,
}
