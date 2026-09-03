package com.pion.phonecleaner.data.device

/**
 * The battery's two capacity readings in mAh — the design capacity, and the charge left right now.
 * Either can be `null`; they come from different sources and fail independently.
 *
 * `docs/screens/18-device-battery-and-apps.md` §5.5 makes this the headline example of the cluster's
 * largest delta: the competitor substitutes the literal **`4660`** whenever it cannot read a
 * capacity, so a user who checks the spec sheet finds a number the OS never reported. There is no
 * fallback here.
 *
 * `com.android.internal.os.PowerProfile.getBatteryCapacity()` is the only source that reports a
 * *design* capacity, and it is the one the competitor reaches for too. It is a non-SDK interface, so
 * on API 28 and above the platform is entitled to refuse the reflective call — and increasingly does.
 * That refusal is caught and answered with `null`, which the cell renders as "Not available".
 *
 * `BATTERY_PROPERTY_CHARGE_COUNTER` is deliberately **not** a substitute for it. It reports the
 * charge remaining right now, in µAh; the competitor divides it by 1000 and labels the result
 * "Capacity", which means its capacity cell falls as the battery drains. That reading is real and
 * worth showing — under its own name, [currentChargeMah], on its own row.
 *
 * > UNKNOWN — no public API for design capacity exists. Looked in
 * > `docs/screens/18-device-battery-and-apps.md` §5.5 and
 * > `docs/reverse-engineering/18-device-battery-and-apps.md` §4.2, which record the competitor's two
 * > sources and its constant, and neither names a supported one.
 */
internal object BatteryCapacityReader {

    private const val POWER_PROFILE_CLASS = "com.android.internal.os.PowerProfile"

    fun designCapacityMah(context: android.content.Context): Int? = runCatching {
        val type = Class.forName(POWER_PROFILE_CLASS)
        val instance = type.getConstructor(android.content.Context::class.java).newInstance(context)
        val capacity = type.getMethod("getBatteryCapacity").invoke(instance) as? Double
        capacity?.takeIf { it > 0.0 }?.toInt()
    }.getOrNull()

    /**
     * The charge left right now, µAh → mAh.
     *
     * `getIntProperty` answers `Integer.MIN_VALUE` for a property the device does not support, and a
     * fuel gauge that has never been calibrated can answer `0`; both are "not readable", so anything
     * that is not positive becomes `null` rather than a `0 mAh` row on a phone that is plainly on.
     */
    fun currentChargeMah(context: android.content.Context): Int? =
        context.getSystemService(android.os.BatteryManager::class.java)
            ?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ?.takeIf { it > 0 }
            ?.let { it / MICRO_PER_MILLI }

    /** `CHARGE_COUNTER` is documented in µAh; every row this cluster renders is in mAh. */
    private const val MICRO_PER_MILLI = 1000
}
