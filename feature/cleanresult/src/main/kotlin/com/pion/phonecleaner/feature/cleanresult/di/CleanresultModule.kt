package com.pion.phonecleaner.feature.cleanresult.di

import com.pion.phonecleaner.feature.cleanresult.CleanResultViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * `cleanResultModule` — the presentation module for the cleanresult cluster.
 *
 * **`viewModelOf(...)` and `factoryOf(...)` only. Never a `single`** (LLM.md §6.1, §6.3).
 * A `single` ViewModel keeps the last scan's rows and its jobs alive for the whole process —
 * the competitor's Activity-field behaviour with a longer lifetime.
 *
 * Repositories and engines belong to a `<cluster>DataModule` in `:data`, which is the only module
 * allowed to declare them (§6.4: one declaring module per type, or a duplicate is a silent override).
 *
 * Provenance of everything this ViewModel resolves, so nobody re-derives why none of it is here:
 *
 * | What | Declared in |
 * |---|---|
 * | `CleanupLedger` | `coreDataModule` |
 * | `AppLogger` | `coreModule` |
 *
 * `FeatureUsageRepository` and `AnalyticsRepository` were resolved here until the suggestion list was
 * removed (owner decision 2026-09-03). Both still exist and are still declared in `coreDataModule`;
 * this screen simply no longer has anything to ask them.
 *
 * `CleanupSummary` comes from `params`, not a `get()`: it is the route argument, and this module
 * cannot name the `@Serializable` route type that carries it (`LLM.md` §7.2).
 *
 * Screens: cleanresult (phases Counting -> Revealed)
 */
val cleanResultModule = module {
    viewModel { params -> CleanResultViewModel(params.get(), get(), get()) }
}
