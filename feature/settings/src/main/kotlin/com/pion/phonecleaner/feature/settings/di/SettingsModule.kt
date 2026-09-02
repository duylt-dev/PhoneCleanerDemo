package com.pion.phonecleaner.feature.settings.di

import com.pion.phonecleaner.feature.settings.about.AboutViewModel
import com.pion.phonecleaner.feature.settings.language.LanguageViewModel
import com.pion.phonecleaner.feature.settings.permissioncentre.PermissionCentreViewModel
import com.pion.phonecleaner.feature.settings.settings.SettingsViewModel
import com.pion.phonecleaner.feature.settings.webview.WebViewViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * `settingsModule` — the presentation module for the settings cluster.
 *
 * **`viewModelOf(...)` and `factoryOf(...)` only. Never a `single`** (LLM.md §6.1, §6.3).
 * A `single` ViewModel keeps the last scan's rows and its jobs alive for the whole process —
 * the competitor's Activity-field behaviour with a longer lifetime.
 *
 * Repositories and engines belong to a `<cluster>DataModule` in `:data`, which is the only module
 * allowed to declare them (§6.4: one declaring module per type, or a duplicate is a silent override).
 *
 * Screens: settings · language · about · webview · permissioncentre · devtools (src/debug only)
 */
val settingsModule = module {
    viewModelOf(::AboutViewModel)
    viewModelOf(::LanguageViewModel)
    viewModelOf(::PermissionCentreViewModel)
    viewModelOf(::SettingsViewModel)

    /**
     * Explicit, not `viewModelOf`: this ViewModel reads a route argument, so Koin must hand it the
     * nav back-stack entry's `SavedStateHandle` (`LLM.md` §6.3). Without it the argument is re-read
     * from the `Intent` on every recreation.
     */
    viewModel { WebViewViewModel(savedStateHandle = get(), urls = get(), log = get()) }
}
