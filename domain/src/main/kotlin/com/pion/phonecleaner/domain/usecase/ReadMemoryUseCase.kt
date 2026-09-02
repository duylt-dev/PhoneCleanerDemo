package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.device.MemoryInfo
import com.pion.phonecleaner.domain.repository.DeviceMetricsRepository

/**
 * The memory ring on both running-apps screens
 * (`docs/screens/18-device-battery-and-apps.md` §1.1, §6.4).
 *
 * It exists separately from [ReadDeviceMetricsUseCase] because those two screens need exactly this
 * one reading and none of the other four. The competitor reads it **on the main thread in `z()`**, on
 * both screens — `ActivityManager.getMemoryInfo()` is a binder round trip before the first frame.
 */
class ReadMemoryUseCase(
    private val metrics: DeviceMetricsRepository,
) {
    suspend operator fun invoke(): AppResult<MemoryInfo> = metrics.memory()
}
