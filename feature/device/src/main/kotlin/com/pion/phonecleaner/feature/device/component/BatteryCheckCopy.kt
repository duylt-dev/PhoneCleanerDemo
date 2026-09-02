package com.pion.phonecleaner.feature.device.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.domain.model.device.BatteryCheck
import com.pion.phonecleaner.domain.model.device.BatteryHealth
import com.pion.phonecleaner.domain.model.device.BatterySnapshot
import com.pion.phonecleaner.domain.model.device.BatteryTechnology
import com.pion.phonecleaner.feature.device.R

/**
 * `BatteryCheck` → a label, a glyph and a rendered value
 * (`docs/screens/18-device-battery-and-apps.md` §4.1, §5.3).
 *
 * **The mapping lives here, not on the model.** The competitor stores `nameResId` and `iconResId` on
 * its step objects, which puts `R` references into data and makes the same six facts describable two
 * different ways on the scan screen and the detail screen. One enum, one mapping, both screens.
 *
 * [batteryValue] is the whole point of the cluster's largest delta: a null field renders
 * `value_unavailable`, never a placeholder number. The competitor substitutes `4660` mAh, `50 %`
 * brightness and the literal `"Li-ion"`, so its screen cannot distinguish a reading from a gap.
 */
@Composable
internal fun batteryCheckLabel(check: BatteryCheck): String = stringResource(
    when (check) {
        BatteryCheck.Brightness -> R.string.battery_check_brightness
        BatteryCheck.Temperature -> R.string.battery_check_temperature
        BatteryCheck.Voltage -> R.string.battery_check_voltage
        BatteryCheck.Technology -> R.string.battery_check_technology
        BatteryCheck.Capacity -> R.string.battery_check_capacity
        BatteryCheck.Health -> R.string.battery_check_health
    },
)

internal fun batteryCheckIcon(check: BatteryCheck): ImageVector = when (check) {
    BatteryCheck.Brightness -> Icons.Filled.Brightness6
    BatteryCheck.Temperature -> Icons.Filled.Thermostat
    BatteryCheck.Voltage -> Icons.Filled.Bolt
    BatteryCheck.Technology -> Icons.Filled.BatteryChargingFull
    BatteryCheck.Capacity -> Icons.Filled.Battery4Bar
    BatteryCheck.Health -> Icons.Filled.HealthAndSafety
}

/**
 * A null snapshot **or** a null field renders "Not available".
 *
 * Brightness has a third arm: [BatterySnapshot.brightnessPercent] is null both when the value is
 * unreadable and when the panel is in automatic mode, and the two are not the same thing to a reader.
 * The mode is what the *snapshot's* nullability cannot express, so "Auto" is shown whenever a
 * snapshot exists and the field does not — which is the honest reading of §5.5's brightness row.
 */
@Composable
internal fun batteryValue(check: BatteryCheck, snapshot: BatterySnapshot?): String {
    val unavailable = stringResource(R.string.value_unavailable)
    if (snapshot == null) return unavailable
    return when (check) {
        BatteryCheck.Brightness -> snapshot.brightnessPercent
            ?.let { stringResource(R.string.device_status_value_percent, it) }
            ?: stringResource(R.string.value_automatic)

        BatteryCheck.Temperature ->
            stringResource(R.string.battery_value_celsius, snapshot.temperatureCelsius)

        BatteryCheck.Voltage -> snapshot.voltageMillivolts
            .takeIf { it > 0 }
            ?.let { stringResource(R.string.battery_value_millivolts, it) }
            ?: unavailable

        // OTHER renders the OEM's own word verbatim; a blank one is a gap, not "Li-ion".
        BatteryCheck.Technology -> technologyLabel(snapshot, unavailable)

        BatteryCheck.Capacity -> snapshot.capacityMah
            ?.let { stringResource(R.string.battery_value_milliamp_hours, it) }
            ?: unavailable

        BatteryCheck.Health -> stringResource(healthLabelRes(snapshot.health))
    }
}

@Composable
private fun technologyLabel(snapshot: BatterySnapshot, unavailable: String): String =
    when (snapshot.technology) {
        BatteryTechnology.LI_ION -> stringResource(R.string.battery_technology_li_ion)
        BatteryTechnology.LI_POLY -> stringResource(R.string.battery_technology_li_poly)
        BatteryTechnology.NIMH -> stringResource(R.string.battery_technology_nimh)
        BatteryTechnology.NICD -> stringResource(R.string.battery_technology_nicd)
        BatteryTechnology.OTHER -> snapshot.rawTechnology.ifBlank { unavailable }
    }

/** All seven conditions keep their own string. The competitor renders three of them as "Unknown". */
private fun healthLabelRes(health: BatteryHealth): Int = when (health) {
    BatteryHealth.GOOD -> R.string.battery_health_good
    BatteryHealth.OVERHEAT -> R.string.battery_health_overheat
    BatteryHealth.DEAD -> R.string.battery_health_dead
    BatteryHealth.OVER_VOLTAGE -> R.string.battery_health_over_voltage
    BatteryHealth.FAILURE -> R.string.battery_health_failure
    BatteryHealth.COLD -> R.string.battery_health_cold
    BatteryHealth.UNKNOWN -> R.string.battery_health_unknown
}
