package com.pion.phonecleaner.data.device

import android.app.ActivityManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.asFailure
import com.pion.phonecleaner.core.common.result.asSuccess
import com.pion.phonecleaner.domain.model.device.CpuInfo
import com.pion.phonecleaner.domain.model.device.DeviceIdentity
import com.pion.phonecleaner.domain.model.device.DisplayInfo
import com.pion.phonecleaner.domain.model.device.MemoryInfo
import com.pion.phonecleaner.domain.model.device.StorageInfo
import com.pion.phonecleaner.domain.repository.DeviceMetricsRepository
import com.pion.phonecleaner.domain.repository.StorageInfoRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The five device-status readings (`docs/screens/18-device-battery-and-apps.md` §1.1, §3.5).
 * Replaces `cd.a` and `od.p0.q()`.
 *
 * **Every read is off the main thread**, inside this class. The competitor performs all five
 * synchronously in `MortatioActivity.z()` before the first frame: `/proc/stat` plus `StatFs` plus
 * `getRealMetrics` is a guaranteed jank frame, and an ANR on a slow volume.
 *
 * [storage] **delegates to [StorageInfoRepository]** rather than running its own `StatFs`. That
 * repository is the one home, splash and this cluster all read (`docs/system-architecture.md` §5.6),
 * and it already knows the two things a second copy would get wrong: to prefer
 * `StorageStatsManager.getTotalBytes`, which reports the capacity printed on the box, and never to
 * touch `Environment.getExternalStorageDirectory()`, which is deprecated and reports the wrong volume
 * on adopted storage. It is bound in `storageDataModule`; nothing here redeclares it.
 */
internal class AndroidDeviceMetricsRepository(
    private val context: Context,
    private val storageInfo: StorageInfoRepository,
    private val dispatchers: DispatcherProvider,
) : DeviceMetricsRepository {

    /**
     * `Build.MODEL` sometimes already begins with the manufacturer ("Pixel 8" does not, "Xiaomi 14"
     * does), so the join is conditional and case-insensitive — the same test `cd.a.f()` makes, and
     * the reason this is one already-joined string rather than two fields the UI concatenates.
     */
    override suspend fun identity(): AppResult<DeviceIdentity> = read {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val name = when {
            manufacturer.isEmpty() -> model
            model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "${manufacturer.replaceFirstChar { it.titlecase(Locale.ROOT) }} $model"
        }
        DeviceIdentity(
            manufacturerAndModel = name.ifBlank { Build.DEVICE.orEmpty() },
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
        )
    }

    /**
     * Bytes, not megabytes. `cd.a.d()` divides by 1 048 576 at the source and hands the UI a number
     * whose unit is baked in; here the model carries bytes and `rememberByteFormat()` picks the unit
     * at render time, in the reader's locale (`docs/system-architecture.md` §4.2).
     */
    override suspend fun memory(): AppResult<MemoryInfo> = read {
        val manager = context.getSystemService(ActivityManager::class.java)
            ?: error("ActivityManager unavailable")
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        MemoryInfo(totalBytes = info.totalMem, availableBytes = info.availMem)
    }

    override suspend fun storage(): AppResult<StorageInfo> = storageInfo.current()

    /**
     * Two samples [SAMPLE_GAP_MILLIS] apart, so the number is the busy share of *that interval* and
     * not of the time since boot. The `delay` is why this call is worth hiding behind the scan
     * animation — which is exactly what §2.5 makes the animation do.
     *
     * **Both sources are sampled at both instants, and `/proc/stat` is preferred where it is
     * readable.** On this project's test device it is not: measured 2026-09-03 on `RF8Y60B9NCZ`
     * (SM-A165F, Android 16), an app-process `open("/proc/stat")` returns `EACCES`, which is why the
     * card showed "Not available" on both CPU rows and why [CpuIdleSampler] exists. `/proc/stat` is
     * still tried first because where the policy does allow it, it is the exact figure rather than
     * one reconstructed from per-core idle residency — and one failed `open` costs microseconds.
     */
    override suspend fun cpu(): AppResult<CpuInfo> = read {
        val statBefore = ProcStatSampler.read()
        val idleBefore = CpuIdleSampler.read()
        delay(SAMPLE_GAP_MILLIS)
        val statAfter = ProcStatSampler.read()
        val idleAfter = CpuIdleSampler.read()
        CpuInfo(
            abis = Build.SUPPORTED_ABIS.orEmpty().toList().toImmutableList(),
            cores = Runtime.getRuntime().availableProcessors(),
            currentFrequencyMhz = CpuFrequencyReader.currentMhz(),
            busyPercent = ProcStatSampler.busyPercent(statBefore, statAfter)
                ?: CpuIdleSampler.busyPercent(idleBefore, idleAfter),
        )
    }

    /**
     * `Resources.getSystem()` carries the device's own display metrics and is not deprecated;
     * `WindowManager.getDefaultDisplay().getRealMetrics()`, which `cd.a.g()` calls, has been
     * deprecated since API 30.
     *
     * **No "screen quality" figure is derived.** The competitor renders a progress track at
     * `densityDpi / 640`, which measures nothing; the two numbers are shown plain (§3.5).
     */
    override suspend fun display(): AppResult<DisplayInfo> = read {
        val metrics = Resources.getSystem().displayMetrics
        DisplayInfo(
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
        )
    }

    /**
     * One dispatcher hop and one failure shape for all five reads.
     *
     * The catch is deliberately broad at this one boundary: `getSystemService` can return null on a
     * stripped build, a sysfs read can raise `SecurityException`, and `Build.*` can be absent under a
     * unit-test stub. None of those is worth a distinct arm — every one of them means "this reading
     * is not available", which the model already expresses as a null field.
     */
    private suspend inline fun <T> read(crossinline block: suspend () -> T): AppResult<T> =
        withContext(dispatchers.io) {
            try {
                block().asSuccess()
            } catch (throwable: Throwable) {
                if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                AppError.Unexpected(throwable.message).asFailure()
            }
        }

    private companion object {
        /**
         * ~500 ms, per §3.5 — long enough for the counters to move, short enough to hide behind a
         * scan. It lives here and not on either sampler because both take their pair across it.
         */
        const val SAMPLE_GAP_MILLIS = 500L
    }
}
