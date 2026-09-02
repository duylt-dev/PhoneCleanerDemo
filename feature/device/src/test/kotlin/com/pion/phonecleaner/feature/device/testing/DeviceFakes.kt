package com.pion.phonecleaner.feature.device.testing

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.asFailure
import com.pion.phonecleaner.core.common.result.asSuccess
import com.pion.phonecleaner.domain.model.device.BatteryHealth
import com.pion.phonecleaner.domain.model.device.BatterySnapshot
import com.pion.phonecleaner.domain.model.device.BatteryTechnology
import com.pion.phonecleaner.domain.model.device.ChargeState
import com.pion.phonecleaner.domain.model.device.CpuInfo
import com.pion.phonecleaner.domain.model.device.DeviceIdentity
import com.pion.phonecleaner.domain.model.device.DeviceMetrics
import com.pion.phonecleaner.domain.model.device.DisplayInfo
import com.pion.phonecleaner.domain.model.device.MemoryInfo
import com.pion.phonecleaner.domain.model.device.RunningApp
import com.pion.phonecleaner.domain.model.device.StorageInfo
import com.pion.phonecleaner.domain.model.device.UsageAccessState
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.repository.AnalyticsRepository
import com.pion.phonecleaner.domain.repository.BatteryRepository
import com.pion.phonecleaner.domain.repository.DeviceMetricsRepository
import com.pion.phonecleaner.domain.repository.DeviceScanSessionStore
import com.pion.phonecleaner.domain.repository.FeatureUsageRepository
import com.pion.phonecleaner.domain.repository.RunningAppsRepository
import com.pion.phonecleaner.domain.repository.ScanBadgeRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlin.time.Instant

/**
 * Hand-written fakes, no mocking library (`LLM.md` §9). Every failure mode is a switchable field, so
 * a test names the condition it is asserting instead of configuring a matcher.
 */
internal class FakeDeviceMetricsRepository : DeviceMetricsRepository {

    var identity: DeviceIdentity? = DeviceIdentity("Pion P1", "14")
    var memory: MemoryInfo? = MemoryInfo(totalBytes = 8_000_000_000L, availableBytes = 3_000_000_000L)
    var storage: StorageInfo? = StorageInfo(totalBytes = 128_000_000_000L, availableBytes = 40_000_000_000L)
    var cpu: CpuInfo? = CpuInfo(abis = persistentListOf("arm64-v8a"), cores = 8, currentFrequencyMhz = 1800, busyPercent = 12)
    var display: DisplayInfo? = DisplayInfo(widthPx = 1080, heightPx = 2400, densityDpi = 420)

    /** Set to make [memory] throw, which is the third path `launchSafely` catches. */
    var throwOnMemory: Boolean = false

    /** Milliseconds each read waits, so a test can hold a scan open on the virtual clock. */
    var readDelayMillis: Long = 0L

    var memoryCalls: Int = 0
        private set

    override suspend fun identity(): AppResult<DeviceIdentity> = answer(identity)

    override suspend fun memory(): AppResult<MemoryInfo> {
        memoryCalls++
        if (throwOnMemory) error("memory read failed")
        return answer(memory)
    }

    override suspend fun storage(): AppResult<StorageInfo> = answer(storage)

    override suspend fun cpu(): AppResult<CpuInfo> = answer(cpu)

    override suspend fun display(): AppResult<DisplayInfo> = answer(display)

    private suspend fun <T : Any> answer(value: T?): AppResult<T> {
        if (readDelayMillis > 0) delay(readDelayMillis)
        return value?.asSuccess() ?: AppError.Unexpected("unreadable").asFailure()
    }
}

internal class FakeBatteryRepository(
    var snapshots: List<BatterySnapshot> = listOf(batterySnapshot()),
) : BatteryRepository {
    var throwOnObserve: Boolean = false
    var subscriptions: Int = 0
        private set

    override fun observe(): Flow<BatterySnapshot> = flow {
        subscriptions++
        if (throwOnObserve) error("battery feed failed")
        snapshots.forEach { emit(it) }
    }
}

internal class FakeRunningAppsRepository : RunningAppsRepository {
    var apps: ImmutableList<RunningApp>? = persistentListOf(RunningApp("com.a"), RunningApp("com.b"))
    var stopped: MutableSet<String> = mutableSetOf()
    var access: UsageAccessState = UsageAccessState.Denied
    var listCalls: Int = 0
        private set

    override suspend fun stoppableApps(): AppResult<ImmutableList<RunningApp>> {
        listCalls++
        return apps?.asSuccess() ?: AppError.Unexpected("enumeration failed").asFailure()
    }

    override suspend fun isStopped(packageName: String): AppResult<Boolean> =
        (packageName in stopped).asSuccess()

    override suspend fun usageAccess(): UsageAccessState = access
}

internal class FakeScanBadgeRepository : ScanBadgeRepository {
    var marks: Int = 0
        private set

    override fun observeRunningAppsBadge(): Flow<Boolean> = MutableStateFlow(true)

    override suspend fun markScannedToday(): AppResult<Unit> {
        marks++
        return Unit.asSuccess()
    }
}

/** The real store is in-memory too; this one only counts, so a test can assert the hand-off. */
internal class FakeDeviceScanSessionStore : DeviceScanSessionStore {
    override var deviceMetrics: DeviceMetrics? = null
    override var batterySnapshot: BatterySnapshot? = null
    override var runningApps: ImmutableList<RunningApp>? = null

    override fun clear() {
        deviceMetrics = null
        batterySnapshot = null
        runningApps = null
    }
}

internal class FakeAnalyticsRepository : AnalyticsRepository {
    val events = mutableListOf<AnalyticsEvent>()
    override fun track(event: AnalyticsEvent) {
        events += event
    }
}

internal class FakeFeatureUsageRepository : FeatureUsageRepository {
    val marked = mutableListOf<FeatureId>()
    override suspend fun markUsed(feature: FeatureId) {
        marked += feature
    }

    override fun lastUsed(feature: FeatureId): Flow<Instant?> = MutableStateFlow(null)

    override fun staleFeatures(): Flow<ImmutableList<FeatureId>> = MutableStateFlow(persistentListOf())

    override suspend fun recommend(): FeatureId = FeatureId.DeviceStatus
}

/** Every nullable filled, so a test that cares about one of them says which by overriding it. */
internal fun batterySnapshot(
    percent: Int = 64,
    chargeState: ChargeState = ChargeState.DISCHARGING,
    capacityMah: Int? = 4200,
    brightnessPercent: Int? = 55,
): BatterySnapshot = BatterySnapshot(
    percent = percent,
    voltageMillivolts = 3900,
    temperatureCelsius = 31.5f,
    health = BatteryHealth.GOOD,
    technology = BatteryTechnology.LI_ION,
    rawTechnology = "Li-ion",
    chargeState = chargeState,
    capacityMah = capacityMah,
    brightnessPercent = brightnessPercent,
    chargeTimeRemaining = null,
)
