package com.pion.phonecleaner.data.device

/**
 * The battery's design capacity in mAh — or `null`.
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
 * `BATTERY_PROPERTY_CHARGE_COUNTER` is deliberately **not** used as a substitute. It reports the
 * charge remaining right now, in µAh; the competitor divides it by 1000 and labels the result
 * "Capacity", which means its capacity cell falls as the battery drains.
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
}
