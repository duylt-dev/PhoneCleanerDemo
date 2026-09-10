package com.pion.phonecleaner.feature.trash.di

import com.pion.phonecleaner.feature.trash.TrashViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * `trashModule` — the presentation module for the trash cluster.
 *
 * **`viewModelOf(...)` and `factoryOf(...)` only. Never a `single`** (LLM.md §6.1, §6.3).
 * A `single` ViewModel keeps its rows and its jobs alive for the whole process — the competitor's
 * Activity-field behaviour with a longer lifetime.
 *
 * Repositories and the mover belong to `trashDataModule` in `:data`, which is the only module allowed
 * to declare them (§6.4: one declaring module per type, or a duplicate is a silent override).
 */
val trashModule = module {
    viewModelOf(::TrashViewModel)
}
