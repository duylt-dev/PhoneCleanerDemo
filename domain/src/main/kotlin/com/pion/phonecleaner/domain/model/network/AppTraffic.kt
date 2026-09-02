package com.pion.phonecleaner.domain.model.network

import kotlinx.collections.immutable.ImmutableList

/**
 * One **UID's** data use over a [TrafficPeriod] — not one package's.
 *
 * `NetworkStatsManager` attributes bytes to a UID, and a UID may be shared by several packages. The
 * competitor credits 100 % of a shared UID to `getPackagesForUid(uid)[0]` and never says so
 * (`docs/reverse-engineering/19-network-and-speed-test.md` :502-504), which silently blames one app
 * for another's traffic. Carrying [packageNames] whole keeps the ambiguity visible: the screen
 * renders one grouped row per UID (`docs/screens/19-network-and-speed-test.md` §1.5 D4).
 *
 * [packageNames] is in the order `PackageManager` returned and is never empty — a UID with no
 * resolvable package is dropped by the repository rather than rendered as a raw id.
 */
data class AppTraffic(
    val uid: Int,
    val packageNames: ImmutableList<String>,
    val mobileBytes: Long,
    val wifiBytes: Long,
) {
    val totalBytes: Long get() = mobileBytes + wifiBytes

    /** The one place the filter is applied to a number, so no screen re-derives it. */
    fun bytesFor(filter: TrafficFilter): Long = when (filter) {
        TrafficFilter.Mobile -> mobileBytes
        TrafficFilter.Wifi -> wifiBytes
        TrafficFilter.All -> totalBytes
    }
}
