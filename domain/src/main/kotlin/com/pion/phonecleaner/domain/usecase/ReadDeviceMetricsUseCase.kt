package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.domain.model.device.DeviceMetrics
import com.pion.phonecleaner.domain.repository.DeviceMetricsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * The five device-status readings, taken concurrently
 * (`docs/screens/18-device-battery-and-apps.md` §2.2, §3.2).
 *
 * The concurrency lives here rather than in each of the two ViewModels that need it: `devicestatusscan`
 * takes the readings during its animation and `devicestatusdetail` re-takes them on every resume, and
 * a `coroutineScope { async … }` block written twice is a bound that can drift in one copy.
 *
 * **Every `async` is a structural child of the caller's job**, so a cancelled ViewModel cancels all
 * five with nothing to null out. **Every wait is bounded**: the `StatFs` read sits inside
 * `withTimeoutOrNull`, so a hung volume leaves the storage card "Unavailable" instead of an infinite
 * skeleton. A failed reading becomes `null` — a real state the card renders — never a substituted
 * constant.
 */
class ReadDeviceMetricsUseCase(
    private val metrics: DeviceMetricsRepository,
) {
    suspend operator fun invoke(): DeviceMetrics = coroutineScope {
        val identity = async { metrics.identity().getOrNull() }
        val memory = async { metrics.memory().getOrNull() }
        val storage = async { withTimeoutOrNull(STORAGE_TIMEOUT) { metrics.storage().getOrNull() } }
        val cpu = async { metrics.cpu().getOrNull() }
        val display = async { metrics.display().getOrNull() }
        DeviceMetrics(
            identity = identity.await(),
            memory = memory.await(),
            storage = storage.await(),
            cpu = cpu.await(),
            display = display.await(),
        )
    }

    private companion object {
        /** §3.2's `STAT_FS_TIMEOUT`. A bound, not a floor — a healthy volume answers in microseconds. */
        val STORAGE_TIMEOUT = 3.seconds
    }
}
