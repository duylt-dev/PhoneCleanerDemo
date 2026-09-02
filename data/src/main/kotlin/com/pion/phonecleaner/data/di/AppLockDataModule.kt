package com.pion.phonecleaner.data.di

import com.pion.phonecleaner.data.applock.DataStoreAppLockPinRepository
import com.pion.phonecleaner.data.applock.DataStoreAppLockRepository
import com.pion.phonecleaner.data.applock.DataStoreAppLockSettings
import com.pion.phonecleaner.data.applock.ForegroundAppResolver
import com.pion.phonecleaner.data.applock.UsageStatsForegroundAppMonitor
import com.pion.phonecleaner.domain.repository.AppLockPinRepository
import com.pion.phonecleaner.domain.repository.AppLockRepository
import com.pion.phonecleaner.domain.repository.AppLockSettingsRepository
import com.pion.phonecleaner.domain.repository.ForegroundAppMonitor
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * `appLockDataModule` — the App Lock cluster's own `:data` components
 * (`docs/screens/16-app-lock.md` §1.4).
 *
 * ### Every type below is named by no other cluster — which is what makes these `single`s legal
 *
 * `LLM.md` §6.4: a shared type is declared exactly once, in the module that owns its layer, and a
 * per-cluster module may declare only types no other cluster names. Koin overrides silently by load
 * order, so a second `single<X>` is a runtime coin flip, not a compile error.
 *
 * §1.4 of the appendix lists what this cluster's own report wanted to declare and what actually owns
 * each one. **None of these is declared here:**
 *
 * | Wanted by the App Lock report | Actually owned by |
 * |---|---|
 * | `InstalledAppsDataSource` (`vd.c`) | `InstalledAppsRepository` in `coreDataModule` — files, notification and device name it too |
 * | `AppLockPermissionRepository` (`od.z` + XXPermissions) | `PermissionRepository` in `coreDataModule` — three clusters declared it under three names |
 * | its own `AppIconLoader` (`od.p0.c` + Glide) | `coreUiModule` — four reports declared it under three names |
 * | its own `AnalyticsTracker` (`qd.b` + `pd.a`) | `AnalyticsRepository` in `coreDataModule` — nine reports, four names, one binding |
 * | `FeatureUsageRepository` (`qd.a` + `ae.o0`) | `coreDataModule`, reached through `MarkFeatureUsedUseCase` in `domainModule` |
 * | the 4-second scan floor (`od.q0.a`) | `MinimumDuration` in `coreModule` |
 * | `DataStore<Preferences>`, `AppClock`, `DispatcherProvider`, the app scope | `coreModule` |
 *
 * Those four rows are the exact collision `docs/system-architecture.md` §5.1 measures for this
 * cluster.
 *
 * ### Two deviations from §1.4's literal listing, both stated
 *
 * 1. §1.4 writes `UsageStatsForegroundAppMonitor(androidContext(), get(), get(), get())`. The
 *    monitor takes no `Context`: the three platform reads it needs — usage access, screen state and
 *    the foreground package — are behind [ForegroundAppResolver], so the loop's policy is testable
 *    without a `Context` and the one place that can throw `SecurityException` is one file. That adds
 *    the fifth `single` below, of a type no other cluster names.
 * 2. `Pbkdf2PinHasher` and `KeystoreSaltVault` are **not** Koin bindings. §2.4 says exactly that:
 *    the KDF is a constructor detail of the PIN repository, not a separate binding.
 *
 * WIRING — this module is not in `:app/App.kt`'s `appModules` and adding it belongs to whoever owns
 * that file. Nothing in the App Lock cluster resolves until it is there, and
 * `InstalledAppsRepository` (still unbound anywhere) must exist before `AppLockRepository` resolves.
 */
val appLockDataModule = module {

    single { ForegroundAppResolver(androidContext()) }

    // ← the list half of od.o0: the store is a Set, and it is pruned against PackageManager.
    single<AppLockRepository> { DataStoreAppLockRepository(get(), get(), get(), get()) }

    // ← od.d0's app_lock_enable / app_lock_new_app, off the main thread and on one upstream.
    single<AppLockSettingsRepository> { DataStoreAppLockSettings(get(), get(), get()) }

    // ← app_lock_pwd. A salted PBKDF2 digest with a Keystore-wrapped salt, replacing four
    //   plaintext ASCII digits written with commit() on the main thread.
    single<AppLockPinRepository> { DataStoreAppLockPinRepository(get(), get(), get(), get()) }

    // ← od.e0 (the 500 ms forever-poll) + od.o0.g() (the hour-wide re-query). The app scope, so the
    //   watchdog is a child of something with an owner rather than of an inline CoroutineScope.
    single<ForegroundAppMonitor> {
        UsageStatsForegroundAppMonitor(get(), get(), get(), get(), get(), get(), get(named(APP_SCOPE)))
    }
}
