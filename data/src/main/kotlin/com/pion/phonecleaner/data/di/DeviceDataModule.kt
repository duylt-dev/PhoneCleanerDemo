package com.pion.phonecleaner.data.di

import com.pion.phonecleaner.data.device.AndroidBatteryRepository
import com.pion.phonecleaner.data.device.AndroidDeviceMetricsRepository
import com.pion.phonecleaner.data.device.DataStoreScanBadgeRepository
import com.pion.phonecleaner.data.device.InMemoryDeviceScanSessionStore
import com.pion.phonecleaner.data.device.PackageManagerRunningAppsRepository
import com.pion.phonecleaner.domain.repository.BatteryRepository
import com.pion.phonecleaner.domain.repository.DeviceMetricsRepository
import com.pion.phonecleaner.domain.repository.DeviceScanSessionStore
import com.pion.phonecleaner.domain.repository.RunningAppsRepository
import com.pion.phonecleaner.domain.repository.ScanBadgeRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * `deviceDataModule` — the device cluster's own data bindings, and **only** types no other cluster
 * names (`LLM.md` §6.4: one declaring module per type, or a second declaration is a silent override
 * decided by load order, which nothing catches).
 *
 * Five of the six bindings the cluster report proposed are declared elsewhere and are deliberately
 * absent here (`docs/screens/18-device-battery-and-apps.md` §8):
 *
 * | Binding | Its one home |
 * |---|---|
 * | `FeatureUsageRepository` · `AnalyticsRepository` · `InstalledAppsRepository` · `PermissionRepository` | `coreDataModule` |
 * | `StorageInfoRepository` — injected into [AndroidDeviceMetricsRepository] below | `storageDataModule` |
 * | `AppIconLoader` | `coreUiModule` |
 * | `DispatcherProvider` · `AppClock` · `DataStore<Preferences>` | `coreModule` |
 * | every use case | `domainModule`, as a `factory` |
 *
 * `DeviceScanSessionStore` is the sixth row, and it is a doc-drift fix: `docs/system-architecture.md`
 * §5.7 lists four bindings for this module. It is a `single` because a session store that is not
 * shared is not a hand-off (§0.2 / §10.3 **U1**).
 *
 * **`AppInfoLauncher` is not here, and does not exist.** §1.1 declares it in `:domain` returning an
 * `android.content.Intent` — but `:domain` is a `kotlin("jvm")` module with no Android on its
 * classpath (`LLM.md` §2), so that interface cannot compile. The Settings deep link is built and
 * started in `RunningAppsRoute`, which is where §6.2 already puts the platform work: *"`OpenSystemAppInfo`
 * is an Effect the Route performs"*. One binding fewer, and no Android type behind the `:domain` boundary.
 */
val deviceDataModule = module {

    single<DeviceMetricsRepository> {
        AndroidDeviceMetricsRepository(androidContext(), get(), get())
    }

    single<BatteryRepository> { AndroidBatteryRepository(androidContext(), get()) }

    single<RunningAppsRepository> { PackageManagerRunningAppsRepository(androidContext(), get()) }

    single<ScanBadgeRepository> { DataStoreScanBadgeRepository(get(), get()) }

    single<DeviceScanSessionStore> { InMemoryDeviceScanSessionStore() }
}
