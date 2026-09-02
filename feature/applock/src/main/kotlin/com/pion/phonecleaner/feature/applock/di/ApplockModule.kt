package com.pion.phonecleaner.feature.applock.di

import com.pion.phonecleaner.feature.applock.applock.AppLockViewModel
import com.pion.phonecleaner.feature.applock.lockscreen.LockScreenViewModel
import com.pion.phonecleaner.feature.applock.pin.PinViewModel
import com.pion.phonecleaner.feature.applock.settings.AppLockSettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * `appLockModule` — the presentation module for the applock cluster
 * (`docs/screens/16-app-lock.md` §1.4).
 *
 * **`viewModel { }` / `viewModelOf(...)` only. Never a `single`** (`LLM.md` §6.1, §6.3). A `single`
 * ViewModel keeps the last enumeration's rows and its jobs alive for the whole process — the
 * competitor's Activity-field behaviour with a *longer* lifetime.
 *
 * `PinViewModel` and `LockScreenViewModel` take their `SavedStateHandle` from `params` because both
 * read an argument: the PIN mode from the route, the target package from `LockScreenActivity`'s
 * `Intent` extras, which `ComponentActivity` folds into the default handle. Without `params.get()`
 * the argument is re-read from scratch on every recreation (`LLM.md` §6.3).
 *
 * ### What this cluster consumes and does NOT declare (`LLM.md` §6.4)
 *
 * | Type | Its one declaring module |
 * |---|---|
 * | `AppLockRepository` · `AppLockSettingsRepository` · `AppLockPinRepository` · `ForegroundAppMonitor` | `appLockDataModule` |
 * | `InstalledAppsRepository` | `coreDataModule` — files, notification and device name it too |
 * | `PermissionRepository` | `coreDataModule` (`koinInject`ed in `AppLockRoute`, not in a ViewModel) |
 * | `AppIconLoader` | `coreUiModule` |
 * | `AppLogger` · `DispatcherProvider` · `AppClock` | `coreModule` |
 * | the eight use cases below | `domainModule` |
 *
 * Four of those rows are the exact collision `docs/system-architecture.md` §5.1 measures: this
 * cluster's own report declared `AppIconLoader`, `InstalledAppsDataSource`,
 * `AppLockPermissionRepository` and its own analytics port. Koin overrides silently by load order,
 * so a duplicate is a runtime coin flip rather than a compile error.
 *
 * WIRING — these four bindings resolve only once `:app/App.kt` loads `appLockDataModule`, once
 * `coreDataModule` declares `InstalledAppsRepository` (still unbound anywhere), and once
 * `domainModule` declares `ObserveLockableAppsUseCase`, `SetAppLockedUseCase`,
 * `ObserveAppLockSettingsUseCase`, `SetLockNewlyInstalledUseCase`, `SetAppLockEnabledUseCase`,
 * `VerifyPinUseCase`, `SavePinUseCase` and `ClearAppLockUseCase`. All three files belong to other
 * owners; the exact lines are reported rather than added here, because adding a `single` — or a
 * second `factoryOf` for a use case — is precisely the defect §6.4 exists to prevent.
 *
 * Screens: applock · pin · lockscreen · settings
 */
val appLockModule = module {
    viewModelOf(::AppLockViewModel)
    viewModel { params -> PinViewModel(params.get(), get(), get(), get()) }
    viewModel { params -> LockScreenViewModel(params.get(), get(), get(), get(), get()) }
    viewModelOf(::AppLockSettingsViewModel)
}
