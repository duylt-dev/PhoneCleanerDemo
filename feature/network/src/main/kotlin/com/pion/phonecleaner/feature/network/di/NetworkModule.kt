package com.pion.phonecleaner.feature.network.di

import com.pion.phonecleaner.feature.network.speedtest.SpeedTestViewModel
import com.pion.phonecleaner.feature.network.speedtestresult.SpeedTestResultViewModel
import com.pion.phonecleaner.feature.network.traffic.NetworkTrafficViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * `networkModule` — the presentation module for the network cluster.
 *
 * **`viewModelOf(...)` and `factoryOf(...)` only. Never a `single`** (LLM.md §6.1, §6.3).
 * A `single` ViewModel keeps the last report's rows and its jobs alive for the whole process —
 * the competitor's Activity-field behaviour with a longer lifetime.
 *
 * Repositories and engines belong to a `<cluster>DataModule` in `:data`, which is the only module
 * allowed to declare them (§6.4: one declaring module per type, or a duplicate is a silent override).
 *
 * Provenance of everything these ViewModels resolve, so nobody re-derives why none of it is here:
 *
 * | What | Declared in |
 * |---|---|
 * | `NetworkTrafficRepository` · `SpeedTestRepository` | `networkDataModule` |
 * | `PermissionRepository` · `InstalledAppsRepository` · `FeatureUsageRepository` | `coreDataModule` |
 * | `AppIconLoader` | `coreUiModule` |
 * | `AppLogger` · `DispatcherProvider` · `AppClock` | `coreModule` |
 * | every use case | `domainModule`, as a `factory` |
 * | `ByteFormatter` | nothing — a pure object gains nothing from injection |
 *
 * The cluster's own research proposed `UsageAccessRepository`, `AppInfoRepository`,
 * `AppIconFetcherFactory` and a `single<ByteFormatter>`; all four lost in
 * `docs/system-architecture.md` §4.1 and none of them is declared here.
 *
 * `SavedStateHandle` comes from `params`, not `get()`: Koin hands over the nav back-stack entry's
 * handle, and without it the period and filter are re-read from scratch on every recreation
 * (`LLM.md` §6.3).
 *
 * Screens: traffic · speedtest · speedtestresult
 */
val networkModule = module {

    viewModel { params ->
        NetworkTrafficViewModel(params.get(), get(), get(), get(), get(), get())
    }

    // No `params`: this screen takes no route argument and keeps nothing across process death.
    viewModelOf(::SpeedTestViewModel)

    // `params` carries the nav back-stack entry's handle, which is where the two route arguments are.
    viewModel { params ->
        SpeedTestResultViewModel(params.get(), get(), get())
    }
}
