package com.pion.phonecleaner.data.onboarding

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.asSuccess
import com.pion.phonecleaner.domain.model.onboarding.DeviceCheckProbe
import com.pion.phonecleaner.domain.model.onboarding.DeviceInfoField
import com.pion.phonecleaner.domain.model.onboarding.DeviceInfoValue
import com.pion.phonecleaner.domain.repository.StorageInfoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The five device-check readings, named for the mechanism (`LLM.md` §5): `Build`, the
 * `WindowManager`'s metrics, and the one `StorageInfoRepository` that already owns the storage
 * branch.
 *
 * **It re-declares nothing.** `StorageInfoRepository` is `storageDataModule`'s and is injected, not
 * re-implemented — `AssimssesActivity` reaches `StatFs(Environment.getExternalStorageDirectory())`
 * directly, which is deprecated and reports the wrong volume with adopted storage
 * (`docs/screens/10-splash-and-onboarding.md` §3.5 delta 5). That branch lives in one place.
 *
 * `withContext(dispatchers.io)` is set **here**, in the implementation, never by the caller
 * (`LLM.md` §6.5): `cd.d.d`'s caller picks the dispatcher, so the same reader behaves differently
 * per screen.
 */
internal class PlatformDeviceCheckProbe(
    private val context: Context,
    private val storageInfo: StorageInfoRepository,
    private val dispatchers: DispatcherProvider,
) : DeviceCheckProbe {

    override suspend fun read(field: DeviceInfoField): AppResult<DeviceInfoValue> =
        withContext(dispatchers.io) {
            try {
                when (field) {
                    DeviceInfoField.Device -> DeviceInfoValue.Text(deviceName()).asSuccess()
                    DeviceInfoField.OsVersion ->
                        DeviceInfoValue.Text("Android ${Build.VERSION.RELEASE}").asSuccess()

                    DeviceInfoField.ScreenResolution -> screenSize().asSuccess()
                    DeviceInfoField.ScreenDensity ->
                        DeviceInfoValue.Dpi(context.resources.displayMetrics.densityDpi).asSuccess()

                    DeviceInfoField.StorageUsed -> storage()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation // NEVER swallow this: it is how a coroutine is told to stop.
            } catch (throwable: Throwable) {
                // A real failure, not `"0KB/0KB"`. Every competitor probe swallows its exception and
                // returns a fallback the user cannot tell from a reading (delta 10).
                AppResult.Failure(AppError.Unexpected(throwable.message))
            }
        }

    /**
     * `cd.a.f()` — manufacturer capitalised, then the model, de-duplicated so a `Build.MODEL` that
     * already carries the brand ("Pixel 8" under manufacturer "Google") is not printed twice.
     */
    private fun deviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        return when {
            model.startsWith(manufacturer, ignoreCase = true) -> model
            manufacturer.isEmpty() -> model
            else -> "${manufacturer.replaceFirstChar { it.titlecase(Locale.ROOT) }} $model"
        }
    }

    /**
     * The **whole display**, not this window: on API 30+ `maximumWindowMetrics`, and
     * `Display.getRealMetrics` below it.
     *
     * `docs/screens/10-splash-and-onboarding.md` §3.5 delta 6 names
     * `WindowMetricsCalculator.computeMaximumWindowMetrics()`. UNKNOWN — that class needs
     * `androidx.window`, which is **not** in `gradle/libs.versions.toml`; looked for `window` and
     * `androidx-window` there and in `:data`'s `build.gradle.kts`, and neither declares it. Adding a
     * library to the version catalogue is outside this change's ownership, so the platform APIs the
     * library wraps are used directly and the deprecated call is confined to the pre-30 branch.
     */
    @Suppress("DEPRECATION")
    private fun screenSize(): DeviceInfoValue {
        val windowManager = context.getSystemService(WindowManager::class.java)
            ?: return DeviceInfoValue.Pixels(0, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            DeviceInfoValue.Pixels(bounds.width(), bounds.height())
        } else {
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            DeviceInfoValue.Pixels(metrics.widthPixels, metrics.heightPixels)
        }
    }

    private suspend fun storage(): AppResult<DeviceInfoValue> = when (val info = storageInfo.current()) {
        is AppResult.Success ->
            DeviceInfoValue.Storage(info.value.usedBytes, info.value.totalBytes).asSuccess()

        is AppResult.Failure -> info
    }
}
