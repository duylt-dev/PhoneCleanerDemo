package com.pion.phonecleaner.data.device

import java.io.File
import java.io.IOException

/**
 * cpu0's clock, in MHz, from the two `cpufreq` sysfs nodes — or `null`.
 *
 * `scaling_cur_freq` is the live value and `cpuinfo_max_freq` the ceiling; both are in kHz. The
 * competitor falls back to **`0`** when neither is readable, which the detail screen then renders as
 * a real reading of zero.
 *
 * > **UNKNOWN — whether `scaling_cur_freq` is readable on current OEM builds is untested.**
 * > This is open question 3 of `docs/screens/18-device-battery-and-apps.md` §9. Looked for a
 * > measurement in that appendix §9 row 3, in `docs/reverse-engineering/18-device-battery-and-apps.md`
 * > §4.1 (which records the read but not its success rate) and §6; none states one. If it never is,
 * > `CpuInfo.currentFrequencyMhz` is permanently null and the row is permanently a dash — which is
 * > the honest outcome, and the reason this returns a nullable rather than a number.
 */
internal object CpuFrequencyReader {

    private const val SCALING_CURRENT = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"
    private const val CPUINFO_MAX = "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq"
    private const val KHZ_PER_MHZ = 1000

    fun currentMhz(
        current: File = File(SCALING_CURRENT),
        max: File = File(CPUINFO_MAX),
    ): Int? = readKilohertz(current) ?: readKilohertz(max)

    private fun readKilohertz(file: File): Int? = try {
        file.takeIf { it.canRead() }
            ?.readText()
            ?.trim()
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
            ?.let { (it / KHZ_PER_MHZ).toInt() }
            ?.takeIf { it > 0 }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
}
