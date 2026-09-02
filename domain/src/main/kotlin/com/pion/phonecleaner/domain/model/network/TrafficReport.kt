package com.pion.phonecleaner.domain.model.network

import kotlinx.collections.immutable.ImmutableList

/**
 * Everything one query of `NetworkStatsManager` returned, for one [period].
 *
 * [mobileBytes] and [wifiBytes] are the sums over **every** row in [apps], so the totals at the top
 * of the screen and the rows below it can never disagree. The competitor's totals are the sums of a
 * list it has already filtered, so its header answers a different question from its body
 * (`docs/reverse-engineering/19-network-and-speed-test.md` :506-509).
 *
 * [period] rides along so a late result can be recognised as stale without the caller keeping a
 * parallel record of what it asked for.
 */
data class TrafficReport(
    val period: TrafficPeriod,
    val mobileBytes: Long,
    val wifiBytes: Long,
    val apps: ImmutableList<AppTraffic>,
) {
    fun totalFor(filter: TrafficFilter): Long = when (filter) {
        TrafficFilter.Mobile -> mobileBytes
        TrafficFilter.Wifi -> wifiBytes
        TrafficFilter.All -> mobileBytes + wifiBytes
    }
}
