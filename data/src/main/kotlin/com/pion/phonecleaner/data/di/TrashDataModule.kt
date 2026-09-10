package com.pion.phonecleaner.data.di

import com.pion.phonecleaner.data.trash.RoomTrashRepository
import com.pion.phonecleaner.data.trash.TrashMover
import com.pion.phonecleaner.data.trash.TrashRoots
import com.pion.phonecleaner.domain.repository.TrashRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * `trashDataModule` — the trash cluster's `:data` bindings (`LLM.md` §6.1). Filled in by plan
 * 260908-0801 phase 03; the precedent for shape is `NotificationDataModule.kt`.
 *
 * `TrashRoots` is a `single` because it caches the rename-probe result; `TrashMover` because it holds
 * the `Context` and the roots. Both are `internal` to `:data`, so no cluster module can declare a
 * second one. `TrashCommit`/`TrashReconciler` are deliberately NOT here — they are internal
 * collaborators `RoomTrashRepository` constructs itself, the same shape `Media3VideoCompressor`
 * constructs `VideoTranscodeSession` (`filesDataModule`'s own KDoc).
 */
val trashDataModule = module {
    single { TrashRoots(androidContext(), get(), get(), get()) }
    single { TrashMover(androidContext(), get(), get(), get()) }
    single<TrashRepository> { RoomTrashRepository(get(), get(), get(), get(), get(), get(), androidContext()) }
}
