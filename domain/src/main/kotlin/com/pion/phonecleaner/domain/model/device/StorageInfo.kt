package com.pion.phonecleaner.domain.model.device

/**
 * Volume totals, in bytes.
 *
 * OWNERSHIP NOTE — this is the only file this agent wrote under `model/device/`. `MemoryInfo`,
 * `CpuInfo`, `DisplayInfo`, `DeviceIdentity`, `BatterySnapshot`, `ScanStep` and `StepState` belong to
 * the device cluster. `StorageInfo` is here because it is the return type of two repositories owned
 * by two different modules: the shared `StorageInfoRepository` (`storageDataModule`, read by home,
 * splash/onboarding and device) and the cluster's own `DeviceMetricsRepository`. Shape taken
 * verbatim from `docs/screens/18-device-battery-and-apps.md:79`.
 *
 * Read through `StorageStatsManager.getTotalBytes`/`getFreeBytes(UUID_DEFAULT)` with
 * `StatFs(filesDir)` as the fallback — never `Environment.getExternalStorageDirectory()`, which is
 * deprecated and reports the wrong volume with adopted storage
 * (`docs/screens/10-splash-and-onboarding.md:582`). That choice lives in `:data`; nothing here knows
 * about it.
 */
data class StorageInfo(
    val totalBytes: Long,
    val availableBytes: Long,
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0L)
}
