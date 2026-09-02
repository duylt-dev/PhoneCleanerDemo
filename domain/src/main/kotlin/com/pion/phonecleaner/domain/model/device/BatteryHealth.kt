package com.pion.phonecleaner.domain.model.device

/**
 * All seven `BatteryManager.BATTERY_HEALTH_*` conditions
 * (`docs/screens/18-device-battery-and-apps.md` §5.5).
 *
 * The competitor collapses `COLD`, `OVER_VOLTAGE` and `UNSPECIFIED_FAILURE` into one word, *Unknown*
 * — three distinct conditions reported as one. Each constant survives here and `:core:ui` maps each
 * to its own string.
 */
enum class BatteryHealth {
    GOOD,
    OVERHEAT,
    DEAD,
    OVER_VOLTAGE,
    FAILURE,
    COLD,
    UNKNOWN,
}
