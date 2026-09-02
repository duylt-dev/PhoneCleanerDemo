package com.pion.phonecleaner.feature.device.di

import com.pion.phonecleaner.feature.device.batteryinfo.BatteryInfoViewModel
import com.pion.phonecleaner.feature.device.batteryscan.BatteryScanViewModel
import com.pion.phonecleaner.feature.device.devicestatusdetail.DeviceStatusDetailViewModel
import com.pion.phonecleaner.feature.device.devicestatusscan.DeviceStatusScanViewModel
import com.pion.phonecleaner.feature.device.runningapps.RunningAppsViewModel
import com.pion.phonecleaner.feature.device.runningappsscan.RunningAppsScanViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * `deviceModule` — the presentation module for the device cluster.
 *
 * **`viewModelOf(...)` and `factoryOf(...)` only. Never a `single`** (LLM.md §6.1, §6.3).
 * A `single` ViewModel keeps the last scan's rows and its jobs alive for the whole process —
 * the competitor's Activity-field behaviour with a longer lifetime.
 *
 * Repositories and engines belong to a `<cluster>DataModule` in `:data`, which is the only module
 * allowed to declare them (§6.4: one declaring module per type, or a duplicate is a silent override).
 *
 * Provenance of everything these six ViewModels resolve, so nobody re-derives why none of it is here
 * (`docs/screens/18-device-battery-and-apps.md` §8 — the cluster report proposed all six locally, and
 * four of those are the exact collisions `docs/system-architecture.md` §5.1 measures):
 *
 * | What | Declared in |
 * |---|---|
 * | `DeviceMetricsRepository` · `BatteryRepository` · `RunningAppsRepository` · `ScanBadgeRepository` · `DeviceScanSessionStore` | `deviceDataModule` |
 * | `StorageInfoRepository` — read *inside* `AndroidDeviceMetricsRepository` | `storageDataModule` |
 * | `FeatureUsageRepository` · `AnalyticsRepository` · `InstalledAppsRepository` · `PermissionRepository` | `coreDataModule` |
 * | `AppIconLoader` — resolved by `RunningAppRow` with `koinInject()`, never by a ViewModel | `coreUiModule` |
 * | `AppLogger` · `DispatcherProvider` · `AppClock` | `coreModule` |
 * | every use case | `domainModule`, as a `factory` |
 *
 * Every one is **argument-free**: a scan's payload reaches its detail screen through
 * `DeviceScanSessionStore`, not through a Koin parameter, because a parameter cannot cross a
 * `NavHost` edge and all three scan routes pop themselves (§0.2, system-architecture §10.3 **U1**).
 *
 * Screens: devicestatusscan · devicestatusdetail · batteryscan · batteryinfo · runningappsscan · runningapps
 */
val deviceModule = module {
    viewModelOf(::DeviceStatusScanViewModel)
    viewModelOf(::BatteryScanViewModel)
    viewModelOf(::BatteryInfoViewModel)
    viewModelOf(::DeviceStatusDetailViewModel)
    viewModelOf(::RunningAppsScanViewModel)
    viewModelOf(::RunningAppsViewModel)
}
