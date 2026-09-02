package com.pion.phonecleaner.feature.junk.di

import com.pion.phonecleaner.feature.junk.junkclean.JunkCleanViewModel
import com.pion.phonecleaner.feature.junk.junkreview.JunkReviewViewModel
import com.pion.phonecleaner.feature.junk.junkscan.JunkScanViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * `junkModule` — the presentation module for the junk cluster
 * (`docs/screens/12-junk-cleaning.md` §7.2).
 *
 * **`viewModel { }` / `viewModelOf(...)` only. Never a `single`** (`LLM.md` §6.1, §6.3). A `single`
 * ViewModel keeps the last scan's rows and its jobs alive for the whole process — the competitor's
 * Activity-field behaviour with a *longer* lifetime.
 *
 * Repositories and engines belong to `junkDataModule` in `:data`, which is the only module allowed to
 * declare them (§6.4: one declaring module per type, or a duplicate is a silent override resolved by
 * load order).
 *
 * `JunkScanViewModel` takes its `SavedStateHandle` from `params` because it reads a route argument;
 * without it the argument is re-read from scratch on every recreation.
 *
 * WIRING — the three bindings below resolve only once `:app/App.kt` also loads `junkDataModule` and
 * `coreDataModule` declares `PermissionRepository`, and once `domainModule` declares
 * `CleanJunkUseCase`. All three files belong to other owners; the exact lines are reported rather
 * than added here, because adding a `single` to this module is precisely the defect §6.4 exists to
 * prevent.
 */
val junkModule = module {
    viewModel { params -> JunkScanViewModel(params.get(), get(), get(), get()) }
    viewModelOf(::JunkReviewViewModel)
    viewModelOf(::JunkCleanViewModel)
}
