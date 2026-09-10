package com.pion.phonecleaner.data.di

import com.pion.phonecleaner.data.work.TrashPurgeWorker
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.dsl.module

/**
 * `backgroundModule` — the name `LLM.md` §6.1 has always reserved for WorkManager wiring, created
 * here for the app's first worker (plan 260908-0801 phase 06).
 *
 * `SettingsDataModule.kt:52-65` says `ResidentWidgetSettingsRepository` and `PushRepository` belong
 * here "when it is written". **They are deliberately not moved in this change.** Moving two working
 * bindings buys this feature nothing, and duplicating them would be a load-order coin flip
 * (`LLM.md` §6.4). The deferral is recorded as an `LLM.md` §11 row in phase 10.
 */
val backgroundModule = module {
    // The app's only worker. `workerOf` and not `single` (LLM.md §6.3): WorkManager owns the
    // lifetime, and Koin's factory — registered by `workManagerFactory()` in App.kt, BEFORE
    // `modules(...)` — supplies Context and WorkerParameters.
    workerOf(::TrashPurgeWorker)
}
