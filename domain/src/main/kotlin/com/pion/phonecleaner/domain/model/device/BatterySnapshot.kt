package com.pion.phonecleaner.domain.model.device

import kotlin.time.Duration

/**
 * One reading of the battery (`docs/screens/18-device-battery-and-apps.md` §1, §5).
 *
 * **Numbers and enums only — no formatted string.** The competitor writes `"%.1f°C"`, `"2400 MHz"`
 * and a `SpannableStringBuilder` into its bindings; here every cell is formatted at render time,
 * where the locale is (`docs/system-architecture.md` §4.2). A ViewModel never builds user-facing copy.
 *
 * **Every nullable below is a fallback the competitor invents.** `4660` mAh, `50 %` brightness and a
 * remaining time computed as `percent × 480 / 100` all exist so the screen never shows a gap; the
 * result is that a user cannot tell a reading from a placeholder. Rendering "Not available" costs one
 * `when` branch and is the single largest behavioural delta in this cluster.
 */
data class BatterySnapshot(
    val percent: Int,
    val voltageMillivolts: Int,
    val temperatureCelsius: Float,
    val health: BatteryHealth,
    val technology: BatteryTechnology,
    /** The OEM's own word, so [BatteryTechnology.OTHER] can render what the platform actually said. */
    val rawTechnology: String,
    val chargeState: ChargeState,
    /**
     * The battery's **design** capacity — what the pack holds when full. null when the OS could not
     * report it. The competitor substitutes `4660`.
     */
    val capacityMah: Int?,
    /**
     * The charge **left right now**, in mAh: `BATTERY_PROPERTY_CHARGE_COUNTER` (µAh) ÷ 1000, null
     * when the platform answers `Integer.MIN_VALUE` — which it is entitled to do, and many OEM
     * kernels do.
     *
     * **Not `capacityMah × percent / 100`.** That is the competitor's *Current Capacity :* cell, and
     * it is the percentage in another unit: it has no input the percentage row does not already
     * show, and it inherits `4660` whenever the design capacity was not readable either. This field
     * is a separate reading from a separate property, so the two rows can disagree — and when they
     * do, the disagreement is the hardware's, not a formula's.
     */
    val currentChargeMah: Int?,
    /**
     * null when the brightness mode is automatic or the panel's range is not knowable. The
     * competitor computes `stored × 100 / 255`, which assumes a 0–255 panel; OEMs ship 0–1023 and
     * 0–4095 ones, and in automatic mode the stored value means nothing at all.
     */
    val brightnessPercent: Int?,
    /**
     * null unless `BatteryManager` gave a real answer. **There is no linear guess.**
     * `percent × 480 / 100` (discharging) and `(100 − percent) × 1.2` (charging) are the competitor's
     * two formulas; neither has any input but the percentage, so each *is* the percentage in another
     * unit. A row that cannot be measured renders "Not available".
     */
    val chargeTimeRemaining: Duration?,
) {
    val isCharging: Boolean get() = chargeState != ChargeState.DISCHARGING
}
