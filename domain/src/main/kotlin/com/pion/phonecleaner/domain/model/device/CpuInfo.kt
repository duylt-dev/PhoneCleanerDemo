package com.pion.phonecleaner.domain.model.device

import kotlinx.collections.immutable.ImmutableList

/**
 * What can be read about the processor (`docs/screens/18-device-battery-and-apps.md` §1, §3.5).
 *
 * **Every hard fallback in the competitor becomes a nullable here.** `cd.a` substitutes `25` for CPU
 * busy on a parse failure and `0` for a frequency it could not read, so the screen cannot distinguish
 * "your CPU is 25 % busy" from "we could not read it". A `null` plus an explicit "Not available"
 * string is the honest equivalent and costs one `when` branch per cell.
 */
data class CpuInfo(
    /** `Build.SUPPORTED_ABIS`. Labelled **Architecture**, never "CPU Model" — it is not a model name. */
    val abis: ImmutableList<String>,
    val cores: Int,
    /** null when neither cpufreq node is readable — the UI renders a dash, never a zero. */
    val currentFrequencyMhz: Int?,
    /**
     * A DELTA across a ~500 ms window, not a lifetime figure. The competitor's `(sum − idle) / sum`
     * runs over counters that have been accumulating since boot, so it converges to a constant within
     * hours and never moves again — and on a device where the read is refused it prints a flat `25`.
     *
     * The data layer reads it from `/proc/stat` where policy allows and from per-core cpuidle
     * residency where it does not; **on Android 8+ it is normally the latter**, `/proc/stat` being
     * `EACCES` to an untrusted app. null when neither source could be sampled twice.
     */
    val busyPercent: Int?,
)
