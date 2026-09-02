package com.pion.phonecleaner.feature.vault.di

import org.koin.dsl.module

/**
 * `vaultModule` — the presentation module for the vault cluster.
 *
 * **`viewModelOf(...)` and `factoryOf(...)` only. Never a `single`** (LLM.md §6.1, §6.3).
 * A `single` ViewModel keeps the last scan's rows and its jobs alive for the whole process —
 * the competitor's Activity-field behaviour with a longer lifetime.
 *
 * Repositories and engines belong to a `<cluster>DataModule` in `:data`, which is the only module
 * allowed to declare them (§6.4: one declaring module per type, or a duplicate is a silent override).
 *
 * Screens: UNKNOWN — no report designs this cluster
 */
val vaultModule = module {
    // viewModelOf(::XViewModel) — one line per screen, added with that screen's contract.
}
