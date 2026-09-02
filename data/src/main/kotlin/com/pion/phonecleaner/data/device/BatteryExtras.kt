package com.pion.phonecleaner.data.device

import android.content.Intent
import android.os.BatteryManager
import com.pion.phonecleaner.domain.model.device.BatteryHealth
import com.pion.phonecleaner.domain.model.device.BatteryTechnology
import com.pion.phonecleaner.domain.model.device.ChargeState
import java.util.Locale

/**
 * `ACTION_BATTERY_CHANGED` extras → domain enums. Pure functions, so they are unit-testable without a
 * device (`docs/screens/18-device-battery-and-apps.md` §5).
 *
 * Every mapping below is total, and none of them invents a value:
 *
 * * [batteryHealth] keeps all seven `BATTERY_HEALTH_*` conditions apart. The competitor folds
 *   `COLD`, `OVER_VOLTAGE` and `UNSPECIFIED_FAILURE` into *Unknown* — three conditions, one word.
 * * [batteryTechnology] returns `OTHER` for a word it does not recognise, and the caller carries the
 *   OEM's own string through as `rawTechnology`. The competitor substitutes the literal `"Li-ion"`
 *   for a missing **or blank** value, so a guess about the hardware reads as a measurement.
 * * [chargeState] has no `CHARGING_FAST`: `isFastCharging = plugged == AC || plugged == WIRELESS`
 *   calls wireless fast and USB-PD not fast, which is backwards on most modern devices (§5.5).
 */
internal fun batteryHealth(raw: Int): BatteryHealth = when (raw) {
    BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.GOOD
    BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEAT
    BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.DEAD
    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OVER_VOLTAGE
    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealth.FAILURE
    BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
    else -> BatteryHealth.UNKNOWN
}

/**
 * The strings AOSP itself writes into the extra are `"Li-ion"`, `"Li-poly"`, `"NiMH"` and `"NiCd"`;
 * OEMs ship variants that differ only in punctuation, so the comparison strips the separators rather
 * than listing spellings.
 */
internal fun batteryTechnology(raw: String?): BatteryTechnology {
    val normalised = raw.orEmpty().lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
    return when {
        normalised.isEmpty() -> BatteryTechnology.OTHER
        normalised.startsWith("lipoly") || normalised.startsWith("lipo") -> BatteryTechnology.LI_POLY
        normalised.startsWith("liion") || normalised.startsWith("li") -> BatteryTechnology.LI_ION
        normalised.startsWith("nimh") -> BatteryTechnology.NIMH
        normalised.startsWith("nicd") -> BatteryTechnology.NICD
        else -> BatteryTechnology.OTHER
    }
}

internal fun chargeState(status: Int): ChargeState = when (status) {
    BatteryManager.BATTERY_STATUS_FULL -> ChargeState.FULL
    BatteryManager.BATTERY_STATUS_CHARGING -> ChargeState.CHARGING
    else -> ChargeState.DISCHARGING
}

/**
 * `level × 100 / scale`, clamped. `scale` is nominally 100 but the platform is allowed to report any
 * positive denominator, and a non-positive one means the reading is absent — 0 %, not a crash.
 */
internal fun batteryPercent(level: Int, scale: Int): Int =
    if (scale > 0 && level >= 0) ((level * 100) / scale).coerceIn(0, 100) else 0

/** The raw temperature extra is in tenths of a degree Celsius. */
internal fun temperatureCelsius(raw: Int): Float = raw / 10f

internal fun Intent.batteryInt(name: String, absent: Int = ABSENT): Int = getIntExtra(name, absent)

/** Distinguishable from every real reading, so a caller can tell "not reported" from "zero". */
internal const val ABSENT = -1
