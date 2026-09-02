package com.pion.phonecleaner.feature.antivirus.di

import com.pion.phonecleaner.feature.antivirus.result.AntivirusResultViewModel
import com.pion.phonecleaner.feature.antivirus.scan.AntivirusScanViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * `antivirusModule` — the presentation module for the antivirus cluster.
 *
 * **`viewModelOf(...)` and `viewModel { }` only. Never a `single`** (LLM.md §6.1, §6.3).
 * A `single` ViewModel keeps the last scan's rows and its jobs alive for the whole process —
 * the competitor's Activity-field behaviour with a longer lifetime.
 *
 * Repositories and engines belong to a `<cluster>DataModule` in `:data`, which is the only module
 * allowed to declare them (§6.4: one declaring module per type, or a duplicate is a silent override).
 *
 * Provenance of everything the two ViewModels resolve, so the next reader does not re-derive why
 * none of it is declared here:
 *
 * | What | Declared in |
 * |---|---|
 * | `SecurityScanRepository` · `PackageRemovalMonitor` | `securityDataModule` |
 * | `AppLogger` | `coreModule` |
 * | `MarkFeatureUsedUseCase` | `domainModule` |
 * | `RemoveFindingUseCase` · `IgnoreFindingUseCase` | `domainModule` — **not yet declared there** |
 * | `AppIconLoader` | `coreUiModule`, and `koinInject()`ed into the row, never into a ViewModel |
 *
 * `AntivirusResultViewModel` takes its `SavedStateHandle` from `params` because it reads a route
 * argument; without it the argument is re-read from scratch on every recreation (`LLM.md` §6.3).
 *
 * WIRING — these two bindings resolve only once `:app/App.kt` also loads `securityDataModule` and
 * `domainModule` declares `factoryOf(::RemoveFindingUseCase)` and `factoryOf(::IgnoreFindingUseCase)`.
 * Both files belong to other owners; the exact lines are reported rather than added here, because
 * adding a declaration to a module this cluster does not own is precisely the defect §6.4 prevents.
 *
 * Screens: scan · result
 */
val antivirusModule = module {
    viewModelOf(::AntivirusScanViewModel)
    viewModel { params ->
        AntivirusResultViewModel(params.get(), get(), get(), get(), get())
    }
}
