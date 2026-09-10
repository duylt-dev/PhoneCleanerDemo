package com.pion.phonecleaner.data.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The sysfs layout is faked on disk rather than mocked: `read` takes the root as a parameter for
 * exactly this reason, and a fake tree is the only way to exercise the hot-plug and reset branches,
 * which never fire on a healthy device and are where a wrong number would come from.
 */
internal class CpuIdleSamplerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val second = 1_000_000L
    private val nanosPerMicro = 1_000L

    /** `online: "0-3"` plus four cores each holding two idle states. */
    private fun cpuRoot(online: String, idleMicros: Map<Int, List<Long>>): File {
        val root = temp.newFolder()
        File(root, "online").writeText("$online\n")
        idleMicros.forEach { (core, states) ->
            val dir = File(root, "cpu$core/cpuidle")
            dir.mkdirs()
            states.forEachIndexed { index, micros ->
                File(dir, "state$index").mkdirs()
                File(dir, "state$index/time").writeText("$micros\n")
            }
        }
        return root
    }

    @Test
    fun `sums every idle state of every online core`() {
        val root = cpuRoot("0-1", mapOf(0 to listOf(10L, 5L), 1 to listOf(7L, 3L)))

        val sample = CpuIdleSampler.read(root, nowNanos = 0L)

        assertEquals(mapOf(0 to 15L, 1 to 10L), sample?.idleMicrosPerCore)
    }

    /** A parked core is absent from `online`, so its frozen counters never enter the capacity. */
    @Test
    fun `reads only the cores online names`() {
        val root = cpuRoot("0,2", mapOf(0 to listOf(1L), 1 to listOf(2L), 2 to listOf(3L)))

        val sample = CpuIdleSampler.read(root, nowNanos = 0L)

        assertEquals(setOf(0, 2), sample?.idleMicrosPerCore?.keys)
    }

    @Test
    fun `is null when the cpu tree is not there`() {
        assertNull(CpuIdleSampler.read(temp.newFolder(), nowNanos = 0L))
    }

    /** Two cores idle for a quarter of a one-second window: three quarters of the capacity is work. */
    @Test
    fun `busy is the share of the two-core capacity that was not idle`() {
        val before = CpuIdleSampler.Sample(mapOf(0 to 0L, 1 to 0L), takenAtNanos = 0L)
        val after = CpuIdleSampler.Sample(
            mapOf(0 to second / 4, 1 to second / 4),
            takenAtNanos = second * nanosPerMicro,
        )

        assertEquals(75, CpuIdleSampler.busyPercent(before, after))
    }

    @Test
    fun `fully idle cores report zero`() {
        val before = CpuIdleSampler.Sample(mapOf(0 to 0L), takenAtNanos = 0L)
        val after = CpuIdleSampler.Sample(mapOf(0 to second), takenAtNanos = second * nanosPerMicro)

        assertEquals(0, CpuIdleSampler.busyPercent(before, after))
    }

    /**
     * Residency accrues in real time and the monotonic clock does not advance in suspend, so a
     * window the device slept through reports more idle than elapsed. That is 0 % busy, not negative.
     */
    @Test
    fun `idle beyond the elapsed window clamps to zero rather than going negative`() {
        val before = CpuIdleSampler.Sample(mapOf(0 to 0L), takenAtNanos = 0L)
        val after = CpuIdleSampler.Sample(mapOf(0 to second * 9), takenAtNanos = second * nanosPerMicro)

        assertEquals(0, CpuIdleSampler.busyPercent(before, after))
    }

    /** A core that left mid-window is dropped; the one that stayed still gives an answer. */
    @Test
    fun `counts capacity only for cores present in both samples`() {
        val before = CpuIdleSampler.Sample(mapOf(0 to 0L, 1 to 0L), takenAtNanos = 0L)
        val after = CpuIdleSampler.Sample(mapOf(0 to second / 2), takenAtNanos = second * nanosPerMicro)

        assertEquals(50, CpuIdleSampler.busyPercent(before, after))
    }

    /** A counter that went backwards means the core was reset — the window is unusable, not busy. */
    @Test
    fun `is null when a residency counter goes backwards`() {
        val before = CpuIdleSampler.Sample(mapOf(0 to second), takenAtNanos = 0L)
        val after = CpuIdleSampler.Sample(mapOf(0 to 1L), takenAtNanos = second * nanosPerMicro)

        assertNull(CpuIdleSampler.busyPercent(before, after))
    }

    @Test
    fun `is null when no core survived the window`() {
        val before = CpuIdleSampler.Sample(mapOf(0 to 0L), takenAtNanos = 0L)
        val after = CpuIdleSampler.Sample(mapOf(1 to 0L), takenAtNanos = second * nanosPerMicro)

        assertNull(CpuIdleSampler.busyPercent(before, after))
    }

    @Test
    fun `is null when the window carried no time`() {
        val sample = CpuIdleSampler.Sample(mapOf(0 to 0L), takenAtNanos = 0L)

        assertNull(CpuIdleSampler.busyPercent(sample, sample))
    }

    @Test
    fun `is null when either sample is missing`() {
        val sample = CpuIdleSampler.Sample(mapOf(0 to 0L), takenAtNanos = 0L)

        assertNull(CpuIdleSampler.busyPercent(null, sample))
        assertNull(CpuIdleSampler.busyPercent(sample, null))
    }
}
