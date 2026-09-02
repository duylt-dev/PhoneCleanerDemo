package com.pion.phonecleaner.feature.device.devicestatusdetail

import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.device.BatterySnapshot
import com.pion.phonecleaner.domain.model.device.CpuInfo
import com.pion.phonecleaner.domain.model.device.DeviceIdentity
import com.pion.phonecleaner.domain.model.device.DeviceMetrics
import com.pion.phonecleaner.domain.model.device.DisplayInfo
import com.pion.phonecleaner.domain.model.device.MemoryInfo
import com.pion.phonecleaner.domain.model.device.StorageInfo

/**
 * `devicestatusdetail` (`docs/screens/18-device-battery-and-apps.md` §3.1). Replaces `MortatioActivity`.
 *
 * **Six independently-nullable readings, not one snapshot object.** The six have wildly different
 * costs — `Build.*` is free, `/proc/stat` is two file reads half a second apart, `StatFs` can block
 * on a slow volume — so one non-null object would make the page wait for the slowest. Each `null` is
 * a real state, "not read yet, or not readable", that its card renders as a shimmering track and an
 * "Unavailable" line.
 */
@Immutable
data class DeviceStatusDetailState(
    val identity: DeviceIdentity? = null,
    val memory: MemoryInfo? = null,
    val storage: StorageInfo? = null,
    val battery: BatterySnapshot? = null,
    val cpu: CpuInfo? = null,
    val display: DisplayInfo? = null,
    val isLoading: Boolean = true,
    val error: AppError? = null,
) : UiState {

    /** The page is usable the moment any one card lands. */
    val hasAnyCard: Boolean get() =
        identity != null || memory != null || storage != null ||
            battery != null || cpu != null || display != null

    /** Folds a [DeviceMetrics] in without touching [battery], which has its own live source. */
    fun withMetrics(metrics: DeviceMetrics): DeviceStatusDetailState = copy(
        identity = metrics.identity ?: identity,
        memory = metrics.memory ?: memory,
        storage = metrics.storage ?: storage,
        cpu = metrics.cpu ?: cpu,
        display = metrics.display ?: display,
        isLoading = false,
    )
}

sealed interface DeviceStatusDetailIntent : UiIntent {
    data object ScreenResumed : DeviceStatusDetailIntent
    data object RetryTapped : DeviceStatusDetailIntent
    data object CheckMemoryTapped : DeviceStatusDetailIntent
    data object CheckStorageTapped : DeviceStatusDetailIntent
    data object CheckBatteryTapped : DeviceStatusDetailIntent
    data object BackPressed : DeviceStatusDetailIntent
}

/**
 * Three *Check* buttons navigate onward and this route stays on the back stack — it does **not**
 * pop itself, which matches the competitor's `startActivity` without a `finish()` and is the
 * behaviour a user coming back from the junk cleaner expects (§8, the navigation graph).
 */
sealed interface DeviceStatusDetailEffect : UiEffect {
    data object NavigateToRunningApps : DeviceStatusDetailEffect
    data object NavigateToJunkClean : DeviceStatusDetailEffect
    data object NavigateToBattery : DeviceStatusDetailEffect
    data object NavigateBack : DeviceStatusDetailEffect
}
