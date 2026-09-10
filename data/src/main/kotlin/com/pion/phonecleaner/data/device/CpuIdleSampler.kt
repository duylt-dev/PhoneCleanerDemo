package com.pion.phonecleaner.data.device

import java.io.File
import java.io.IOException

/**
 * System-wide CPU busy %, derived from **cpuidle residency** — the fallback for every build where
 * [ProcStatSampler] cannot open `/proc/stat`.
 *
 * > **MEASURED, 2026-09-03, device `RF8Y60B9NCZ` (SM-A165F, Android 16, API 36).** From the app's own
 * > process, `/proc/stat`, `/proc/uptime`, `/proc/loadavg` and `/proc/schedstat` all fail with
 * > `EACCES`; `/sys/devices/system/cpu/cpuN/cpuidle/stateM/time` and `cpu/online` are readable.
 * > `run-as` is **not** a valid probe for this — it runs in the `runas_app` SELinux domain with the
 * > `readproc` group, and reads `/proc/stat` fine. Only a read from the real process proves anything.
 *
 * Each `stateM/time` is that core's cumulative microseconds in one idle state. Summed per core, the
 * delta between two instants is the time that core spent **not working**; the busy share of the
 * interval is everything else. Validated over 1 s windows against `/proc/stat` read from an `adb
 * shell` (which is in the `shell` domain and may read it): 8–23 % here against 5–20 % there, over the
 * same minute — the same measurement, not a proxy.
 *
 * Two things this deliberately does not do:
 * - **It does not divide by a core count taken from `Runtime.availableProcessors()`.** Capacity is
 *   counted only over cores present in *both* samples, so a core hot-plugged out mid-interval leaves
 *   the window rather than contributing frozen counters that would read as 100 % busy.
 * - **It does not substitute a constant.** `null` is a real answer; `cd.a.a()`'s `25` — which is what
 *   the competitor puts on screen on this device, verified 2026-09-03 — is not.
 */
internal object CpuIdleSampler {

    private const val CPU_ROOT = "/sys/devices/system/cpu"
    private const val STATE_PREFIX = "state"
    private const val NANOS_PER_MICRO = 1_000L

    /** Cumulative idle microseconds per online core, and the monotonic instant the reading was taken. */
    data class Sample(val idleMicrosPerCore: Map<Int, Long>, val takenAtNanos: Long)

    fun read(root: File = File(CPU_ROOT), nowNanos: Long = System.nanoTime()): Sample? {
        val cores = onlineCores(root) ?: return null
        val perCore = cores.mapNotNull { core -> idleMicros(root, core)?.let { core to it } }.toMap()
        return if (perCore.isEmpty()) null else Sample(perCore, nowNanos)
    }

    /**
     * 0..100, or null when the interval carried no measurable time or no core survived it.
     *
     * A counter that went **backwards** means the core was off and its residency was reset, so the
     * whole window is discarded rather than reported as a busy spike.
     */
    fun busyPercent(first: Sample?, second: Sample?): Int? {
        if (first == null || second == null) return null
        val elapsedMicros = (second.takenAtNanos - first.takenAtNanos) / NANOS_PER_MICRO
        if (elapsedMicros <= 0L) return null

        var idleDelta = 0L
        var cores = 0
        for ((core, before) in first.idleMicrosPerCore) {
            val after = second.idleMicrosPerCore[core] ?: continue
            if (after < before) return null
            idleDelta += after - before
            cores++
        }
        if (cores == 0) return null

        val capacity = elapsedMicros * cores
        val busy = (capacity - idleDelta).coerceIn(0L, capacity)
        return ((busy * 100) / capacity).toInt()
    }

    /** `/sys/devices/system/cpu/online` is a cpumask list: `0-7`, or `0-3,6-7` with cores parked. */
    private fun onlineCores(root: File): List<Int>? = readTrimmed(File(root, "online"))
        ?.split(',')
        ?.flatMap { span ->
            val ends = span.split('-').mapNotNull { it.trim().toIntOrNull() }
            when {
                ends.size == 1 -> listOf(ends.first())
                ends.size == 2 && ends[0] <= ends[1] -> (ends[0]..ends[1]).toList()
                else -> emptyList()
            }
        }
        ?.takeIf { it.isNotEmpty() }

    /** The sum over every idle state this core exposes; null when the core exposes none we can read. */
    private fun idleMicros(root: File, core: Int): Long? {
        val states = try {
            File(root, "cpu$core/cpuidle").listFiles { file -> file.name.startsWith(STATE_PREFIX) }
        } catch (_: SecurityException) {
            null
        } ?: return null

        var total = 0L
        var read = false
        for (state in states) {
            val micros = readTrimmed(File(state, "time"))?.toLongOrNull() ?: continue
            total += micros
            read = true
        }
        return if (read) total else null
    }

    private fun readTrimmed(file: File): String? = try {
        file.readText().trim().takeIf { it.isNotEmpty() }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
}
