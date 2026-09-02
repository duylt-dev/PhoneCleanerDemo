package com.pion.phonecleaner.domain.model.device

/**
 * The five device-status readings, gathered once
 * (`docs/screens/18-device-battery-and-apps.md` §2.5, §3.1).
 *
 * **Five independent nullables, not one all-or-nothing snapshot.** The reads have wildly different
 * costs — `Build.*` is free, `/proc/stat` is a file read sampled twice, `StatFs` can block on a slow
 * volume — so folding them into one non-null object makes the page wait for the slowest. A `null`
 * field is a real state the card renders as a skeleton or an "Unavailable" line.
 *
 * It exists as a type because §2.5 makes `devicestatusscan` do the reading *during* its animation and
 * hand the result on through `DeviceScanSessionStore` (§0.2). Without an aggregate there is nothing
 * for the store to hold, and `devicestatusdetail` would re-read everything the scan just read — the
 * same discard-and-redo defect §6.4 records for the running-apps pair.
 */
data class DeviceMetrics(
    val identity: DeviceIdentity? = null,
    val memory: MemoryInfo? = null,
    val storage: StorageInfo? = null,
    val cpu: CpuInfo? = null,
    val display: DisplayInfo? = null,
) {
    /** The page is usable the moment any one reading lands. */
    val hasAny: Boolean get() =
        identity != null || memory != null || storage != null || cpu != null || display != null
}
