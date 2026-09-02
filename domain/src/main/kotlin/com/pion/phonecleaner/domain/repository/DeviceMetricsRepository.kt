package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.device.CpuInfo
import com.pion.phonecleaner.domain.model.device.DeviceIdentity
import com.pion.phonecleaner.domain.model.device.DisplayInfo
import com.pion.phonecleaner.domain.model.device.MemoryInfo
import com.pion.phonecleaner.domain.model.device.StorageInfo

/**
 * The five device-status readings (`docs/screens/18-device-battery-and-apps.md` §1.1). Replaces
 * `cd.a` plus `od.p0.q()`; declared once, in `deviceDataModule` (§8).
 *
 * Every method is `suspend` and every one returns [AppResult]: the competitor reads all five
 * **synchronously in `z()`**, on the main thread, before the first frame — `/proc/stat` plus `StatFs`
 * plus `getRealMetrics` is a guaranteed jank frame and an ANR on a slow volume. The `withContext`
 * lives inside the implementation, which takes `DispatcherProvider`, so no call site names a
 * dispatcher (`LLM.md` §6.5).
 */
interface DeviceMetricsRepository {

    suspend fun identity(): AppResult<DeviceIdentity>

    suspend fun memory(): AppResult<MemoryInfo>

    suspend fun storage(): AppResult<StorageInfo>

    /**
     * Samples `/proc/stat` **twice, inside the implementation**, and reports the delta.
     * `CpuInfo.busyPercent` is null if either read fails — never the competitor's `25`.
     */
    suspend fun cpu(): AppResult<CpuInfo>

    suspend fun display(): AppResult<DisplayInfo>
}
