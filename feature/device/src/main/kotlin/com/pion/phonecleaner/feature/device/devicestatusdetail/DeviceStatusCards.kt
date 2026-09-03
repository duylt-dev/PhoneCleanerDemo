package com.pion.phonecleaner.feature.device.devicestatusdetail

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.tile.LabelValueRow
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.domain.model.device.BatteryCheck
import com.pion.phonecleaner.domain.model.device.BatterySnapshot
import com.pion.phonecleaner.domain.model.device.CpuInfo
import com.pion.phonecleaner.domain.model.device.DisplayInfo
import com.pion.phonecleaner.feature.device.R
import com.pion.phonecleaner.feature.device.component.batteryValue

/**
 * The body of each device-status card (`docs/screens/18-device-battery-and-apps.md` §3.3, §3.5).
 *
 * Two competitor rendering defects are fixed here:
 *
 * 1. *"Used :"* shows a **percentage** while *"All :"* and *"Available :"* show sizes, on two
 *    different cards. Here *Used* renders bytes; the occupancy fraction stays on the progress track,
 *    where it belongs.
 * 2. *"CPU Model :"* shows `Build.SUPPORTED_ABIS`. An ABI list is not a model name, so the row is
 *    labelled **Architecture**.
 *
 * Every value is formatted here, at render time, through `rememberByteFormat()` — which supplies the
 * locale. A ViewModel never builds user-facing copy (`docs/system-architecture.md` §4.2).
 */
@Composable
internal fun ColumnVolumeRows(totalBytes: Long?, availableBytes: Long?) {
    val bytes = rememberByteFormat()
    val unread = stringResource(R.string.device_status_unread)
    val used = if (totalBytes != null && availableBytes != null) {
        bytes.size((totalBytes - availableBytes).coerceAtLeast(0L)).toString()
    } else {
        unread
    }
    Column {
        LabelValueRow(
            icon = null,
            label = stringResource(R.string.device_status_row_used),
            value = used,
        )
        LabelValueRow(
            icon = null,
            label = stringResource(R.string.device_status_row_total),
            value = totalBytes?.let { bytes.size(it).toString() } ?: unread,
        )
        LabelValueRow(
            icon = null,
            label = stringResource(R.string.device_status_row_available),
            value = availableBytes?.let { bytes.size(it).toString() } ?: unread,
        )
    }
}

/**
 * The battery card reuses the same six `BatteryCheck` cells the battery screens use, so a reading is
 * described identically wherever it appears. Only the three that fit a two-line card are shown.
 */
@Composable
internal fun ColumnBatteryRows(snapshot: BatterySnapshot?) {
    Column {
        BATTERY_CARD_CHECKS.forEach { check ->
            LabelValueRow(
                icon = null,
                label = stringResource(batteryCardLabelRes(check)),
                value = batteryValue(check, snapshot),
            )
        }
    }
}

@Composable
internal fun ColumnCpuRows(cpu: CpuInfo?) {
    val unread = stringResource(R.string.device_status_unread)
    val unavailable = stringResource(R.string.value_unavailable)
    Column {
        LabelValueRow(
            icon = null,
            label = stringResource(R.string.device_status_row_architecture),
            value = cpu?.abis?.joinToString(", ")?.ifBlank { unavailable } ?: unread,
        )
        LabelValueRow(
            icon = null,
            label = stringResource(R.string.device_status_row_cores),
            value = cpu?.cores?.toString() ?: unread,
        )
        // A dash, never the competitor's `0`, when neither cpufreq node is readable.
        LabelValueRow(
            icon = null,
            label = stringResource(R.string.device_status_row_frequency),
            value = cpu?.currentFrequencyMhz
                ?.let { stringResource(R.string.device_status_value_megahertz, it) }
                ?: if (cpu == null) unread else unavailable,
        )
        // A DELTA between two /proc/stat samples. The competitor's number is a lifetime ratio that
        // stops moving hours after boot, and `25` whenever the parse fails.
        //
        // Idle is `100 - used`, derived here rather than carried on CpuInfo: /proc/stat's idle column
        // is the same subtraction one field earlier, so a second nullable would be a second chance
        // for the two rows to contradict each other. They are one reading shown two ways, and when
        // the reading is missing BOTH rows say so.
        LabelValueRow(
            icon = null,
            label = stringResource(R.string.device_status_row_cpu_used),
            value = cpu?.busyPercent
                ?.let { stringResource(R.string.device_status_value_percent, it) }
                ?: if (cpu == null) unread else unavailable,
        )
        LabelValueRow(
            icon = null,
            label = stringResource(R.string.device_status_row_cpu_idle),
            value = cpu?.busyPercent
                ?.let { stringResource(R.string.device_status_value_percent, 100 - it) }
                ?: if (cpu == null) unread else unavailable,
        )
    }
}

/**
 * Resolution and density, plain. The competitor draws a "screen quality" track at
 * `densityDpi / 640`, which measures nothing; the bar is dropped rather than relabelled.
 */
@Composable
internal fun ColumnDisplayRows(display: DisplayInfo?) {
    val unread = stringResource(R.string.device_status_unread)
    Column {
        LabelValueRow(
            icon = null,
            label = stringResource(R.string.device_status_row_resolution),
            value = display
                ?.let { stringResource(R.string.device_status_value_pixels, it.widthPx, it.heightPx) }
                ?: unread,
        )
        LabelValueRow(
            icon = null,
            label = stringResource(R.string.device_status_row_density),
            value = display
                ?.let { stringResource(R.string.device_status_value_dpi, it.densityDpi) }
                ?: unread,
        )
    }
}

private val BATTERY_CARD_CHECKS = listOf(
    BatteryCheck.Health,
    BatteryCheck.Temperature,
    BatteryCheck.Voltage,
)

private fun batteryCardLabelRes(check: BatteryCheck): Int = when (check) {
    BatteryCheck.Brightness -> R.string.battery_check_brightness
    BatteryCheck.Temperature -> R.string.battery_check_temperature
    BatteryCheck.Voltage -> R.string.battery_check_voltage
    BatteryCheck.Technology -> R.string.battery_check_technology
    BatteryCheck.Capacity -> R.string.battery_check_capacity
    BatteryCheck.Health -> R.string.battery_check_health
}
