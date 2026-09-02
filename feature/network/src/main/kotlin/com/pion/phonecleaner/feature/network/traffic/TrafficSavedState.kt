package com.pion.phonecleaner.feature.network.traffic

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.domain.model.network.TrafficFilter
import com.pion.phonecleaner.domain.model.network.TrafficPeriod

/**
 * The two filters survive process death (`docs/screens/19-network-and-speed-test.md` §1.5 D6): every
 * competitor field on this screen is an Activity field, so a rotation resets the period to
 * `THIS_MONTH`, the type filter to `ALL` and the scroll to the top.
 *
 * They are stored as `name` strings, not as enum instances: a `SavedStateHandle` round trip goes
 * through a `Bundle`, and a constant that is later renamed or removed then comes back as a
 * deserialisation failure rather than as an unknown name this file can fall back on.
 */
private const val KeyPeriod = "network.traffic.period"
private const val KeyFilter = "network.traffic.filter"

internal fun SavedStateHandle.restorePeriod(): TrafficPeriod =
    get<String>(KeyPeriod)
        ?.let { name -> TrafficPeriod.entries.firstOrNull { it.name == name } }
        ?: TrafficPeriod.ThisMonth

internal fun SavedStateHandle.restoreFilter(): TrafficFilter =
    get<String>(KeyFilter)
        ?.let { name -> TrafficFilter.entries.firstOrNull { it.name == name } }
        ?: TrafficFilter.All

internal fun SavedStateHandle.storePeriod(period: TrafficPeriod) {
    set(KeyPeriod, period.name)
}

internal fun SavedStateHandle.storeFilter(filter: TrafficFilter) {
    set(KeyFilter, filter.name)
}
