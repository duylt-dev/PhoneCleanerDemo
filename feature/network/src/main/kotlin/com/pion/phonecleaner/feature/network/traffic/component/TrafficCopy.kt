package com.pion.phonecleaner.feature.network.traffic.component

import androidx.annotation.StringRes
import com.pion.phonecleaner.domain.model.network.TrafficFilter
import com.pion.phonecleaner.domain.model.network.TrafficPeriod
import com.pion.phonecleaner.feature.network.R

/**
 * The two enum → `@StringRes` maps, in one place and resolved with `stringResource` at render time.
 *
 * Resolving them at object initialisation is the competitor's worst localisation defect: `ae.i2`
 * turns feature names into `String`s from `Resources` when its object is built, and the app ships a
 * 17-locale in-app language picker, so switching language leaves every name stale until the process
 * restarts (`LLM.md` §2).
 */
@StringRes
internal fun TrafficPeriod.labelRes(): Int = when (this) {
    TrafficPeriod.ThisMonth -> R.string.traffic_period_this_month
    TrafficPeriod.Last30Days -> R.string.traffic_period_30_days
    TrafficPeriod.Last24Hours -> R.string.traffic_period_24_hours
}

@StringRes
internal fun TrafficFilter.labelRes(): Int = when (this) {
    TrafficFilter.All -> R.string.traffic_connection_all
    TrafficFilter.Mobile -> R.string.traffic_connection_mobile
    TrafficFilter.Wifi -> R.string.traffic_connection_wifi
}
