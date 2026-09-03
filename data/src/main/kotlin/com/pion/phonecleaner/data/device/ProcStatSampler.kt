package com.pion.phonecleaner.data.device

import java.io.File
import java.io.IOException

/**
 * The `/proc/stat` `cpu ` aggregate line, and the **delta** between two readings of it.
 *
 * `docs/screens/18-device-battery-and-apps.md` §3.5 records the defect this replaces: the competitor
 * computes `(sum − idle) × 100 / sum` over a **single** sample. Those are lifetime counters, so the
 * ratio converges within hours of boot and then never moves again — the number on screen is a
 * constant dressed as a measurement. It also substitutes **`25`** whenever the parse fails.
 *
 * Here two samples are taken `SAMPLE_GAP_MILLIS` apart — the constant belongs to
 * `AndroidDeviceMetricsRepository`, which owns the pair — and the busy share of the interval is
 * reported. Either read failing yields `null`.
 *
 * > **MEASURED, 2026-09-03, `RF8Y60B9NCZ` (SM-A165F, Android 16): `/proc/stat` is `EACCES` from the
 * > app's own process.** So on this device this class returns `null` every time and
 * > [CpuIdleSampler] is what actually produces the number. It is kept, and still tried first,
 * > because it is the exact figure on any build whose policy allows the read.
 */
internal object ProcStatSampler {

    /** Total and idle jiffies from the aggregate `cpu ` line. */
    data class Sample(val total: Long, val idle: Long)

    /**
     * Field order in the `cpu ` line is fixed by the kernel: user nice system idle iowait irq
     * softirq steal … . `idle + iowait` is time the CPU was not doing work, so the busy share is
     * everything else.
     */
    fun read(file: File = File("/proc/stat")): Sample? = try {
        file.useLines { lines ->
            lines.firstOrNull { it.startsWith("cpu ") }
                ?.split(WHITESPACE)
                ?.drop(1)
                ?.mapNotNull(String::toLongOrNull)
                ?.takeIf { it.size >= MINIMUM_FIELDS }
                ?.let { fields ->
                    Sample(total = fields.sum(), idle = fields[IDLE_INDEX] + fields[IOWAIT_INDEX])
                }
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    /** 0..100, or null when the interval carried no measurable time. */
    fun busyPercent(first: Sample?, second: Sample?): Int? {
        if (first == null || second == null) return null
        val totalDelta = second.total - first.total
        val idleDelta = second.idle - first.idle
        if (totalDelta <= 0L) return null
        val busy = (totalDelta - idleDelta).coerceAtLeast(0L)
        return ((busy * 100) / totalDelta).toInt().coerceIn(0, 100)
    }

    /** The competitor accepts a line with four fields; `iowait` is the fifth, so five is the floor. */
    private const val MINIMUM_FIELDS = 5
    private const val IDLE_INDEX = 3
    private const val IOWAIT_INDEX = 4
    private val WHITESPACE = Regex("\\s+")
}
