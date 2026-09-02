package com.pion.phonecleaner.feature.settings.di

import com.pion.phonecleaner.feature.settings.devtools.DevToolsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * `devToolsModule` — **`src/debug` only**, and therefore not compiled into a release build at all
 * (`docs/screens/20-settings-language-and-push.md` §7, §6.4 delta 1).
 *
 * ### Why it declares no `single`, unlike the appendix's sketch
 *
 * §7 sketches this module as
 * `single<DeviceIdRepository> { DataStoreDeviceIdRepository(get()) }` plus the ViewModel. Two rules
 * forbid the first line here:
 *
 *  1. **A `:feature` module declares `viewModel`/`factory` and never a `single`** (`LLM.md` §6.1,
 *     §6.3) — this file is in `:feature:settings`.
 *  2. `DataStoreDeviceIdRepository` is a `:data` class, and `:feature` may not depend on `:data`
 *     (`LLM.md` §2). The sketch's line cannot compile from here whatever the DI rules said.
 *
 * The device-id override is cut outright in any case — see [DevToolsViewModel] and §6.4 delta 5.
 * `PushRepository`, which the ViewModel does take, is a `:domain` interface bound once in
 * `settingsDataModule`, so the bench drives the **real** implementation.
 *
 * UNKNOWN — how `:app` loads this. `App.kt`'s `appModules` is one list in `src/main` and is not this
 * cluster's file; loading a debug-only module needs either a debug source set in `:app` or a
 * variant-aware list there. Reported rather than written, because the `:app` module is off-limits to this
 * change. Until it is loaded, `DevToolsViewModel` cannot resolve — and the route does not exist yet
 * either, so nothing can reach it.
 */
val devToolsModule = module {
    viewModelOf(::DevToolsViewModel)
}
