package com.pion.phonecleaner.feature.home.di

import com.pion.phonecleaner.feature.home.HomeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * `homeModule` — the presentation module for the home cluster
 * (`docs/screens/11-home.md` §1.4).
 *
 * **It declares ONE thing, and that is the point of this file.** Home is the hub, and a hub is
 * exactly where a duplicate `single` gets written: `r2-02` Part B proposed a data module here with
 * ten `single`s and a use-case module with five `factory`s. Nine of those ten `single`s are already
 * owned elsewhere, and six independently written cluster designs each declared their own
 * `single<FeatureUsageRepository>` — the collision the audit exists to prevent, because **Koin
 * overrides silently by load order rather than failing to compile** (`LLM.md` §6.4).
 *
 * Provenance of everything this ViewModel resolves, so the next reader does not have to re-derive
 * why none of it is declared here:
 *
 * | What | Declared in |
 * |---|---|
 * | `FeatureUsageRepository` · `AnalyticsRepository` · `CleanupLedger` · `PermissionRepository` | `coreDataModule` |
 * | `StorageInfoRepository` | `storageDataModule` |
 * | `JunkRepository` | `junkDataModule` |
 * | `AppLogger` | `coreModule` |
 * | `MarkFeatureUsedUseCase` | `domainModule` |
 * | `FeatureCatalog` · `FeatureDescriptors` · `HomeSections` | **nothing** — pure objects (§6.3) |
 *
 * `SavedStateHandle` comes from `params` rather than a `get()`: Koin hands over the nav back-stack
 * entry's handle, and without it the route argument is re-read from scratch on every recreation
 * (`LLM.md` §6.3).
 *
 * WIRING — this binding resolves only once `coreDataModule` also declares `PermissionRepository`
 * and `:app` loads `junkDataModule`. Both files belong to other owners and the exact lines are
 * reported rather than added here, because adding a `single` to a module this cluster does not own
 * is precisely the defect §6.4 prevents.
 *
 * Screens: home
 */
val homeModule = module {
    viewModel { params ->
        HomeViewModel(params.get(), get(), get(), get(), get(), get(), get(), get(), get())
    }
}
