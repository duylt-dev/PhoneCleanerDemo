package com.pion.phonecleaner.feature.onboarding.di

import com.pion.phonecleaner.feature.onboarding.appresume.AppResumeViewModel
import com.pion.phonecleaner.feature.onboarding.devicecheck.DeviceCheckViewModel
import com.pion.phonecleaner.feature.onboarding.splash.SplashViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * `onboardingModule` — the presentation module for the onboarding cluster
 * (`docs/screens/10-splash-and-onboarding.md` §1.4).
 *
 * **`viewModel { }` / `viewModelOf(...)` only. Never a `single`** (`LLM.md` §6.1, §6.3). A `single`
 * ViewModel keeps its jobs and its rows alive for the whole process — the competitor's Activity-field
 * behaviour with a *longer* lifetime.
 *
 * `SplashViewModel` takes its `SavedStateHandle` from `params` rather than through `viewModelOf`,
 * because it reads a route argument: Koin then injects the nav back-stack entry's handle, and
 * without it the argument is re-read from the Intent on every recreation (`LLM.md` §6.3).
 *
 * ### What this cluster consumes and does NOT declare (`LLM.md` §6.4)
 *
 * | Type | Its one declaring module |
 * |---|---|
 * | `PermissionRepository` | `coreDataModule` |
 * | `DispatcherProvider` · `AppLogger` · `DataStore<Preferences>` | `coreModule` |
 * | `StorageInfoRepository` | `storageDataModule` |
 * | `OnboardingStateRepository` · `ConsentRepository` · `DeviceCheckProbe` · the three pacing records | `onboardingDataModule` |
 *
 * Six independently written cluster designs each declared their own `single<FeatureUsageRepository>`;
 * Koin resolves a duplicate by module load order, silently, at runtime. Nothing on that list is
 * repeated here.
 *
 * WIRING — the three bindings below resolve only once `:app/App.kt` also loads
 * `onboardingDataModule`. That file is the one assembly point (§6.2) and is not this change's to
 * edit; the exact line is reported rather than added, because adding a `single` here to work around
 * it is precisely the defect §6.4 exists to prevent.
 */
val onboardingModule = module {
    viewModel { params -> SplashViewModel(params.get(), get(), get(), get(), get(), get()) }
    viewModelOf(::AppResumeViewModel)
    viewModelOf(::DeviceCheckViewModel)
}
