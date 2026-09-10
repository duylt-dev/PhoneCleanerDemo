package com.pion.phonecleaner.feature.files.di

import com.pion.phonecleaner.feature.files.appmanager.AppManagerViewModel
import com.pion.phonecleaner.feature.files.audio.AudioManagerViewModel
import com.pion.phonecleaner.feature.files.bigfiles.BigFilesViewModel
import com.pion.phonecleaner.feature.files.duplicates.DuplicatesViewModel
import com.pion.phonecleaner.feature.files.video.VideoManagerViewModel
import com.pion.phonecleaner.feature.files.videocompressor.VideoCompressorViewModel
import com.pion.phonecleaner.feature.files.videocompressrun.VideoCompressRunViewModel
import com.pion.phonecleaner.feature.files.whatsapp.WhatsAppCleanerViewModel
import com.pion.phonecleaner.feature.files.zipfiles.ZipFilesViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * `filesModule` — the presentation module for the files cluster.
 *
 * **`viewModelOf(...)` and `factoryOf(...)` only. Never a `single`** (LLM.md §6.1, §6.3).
 * A `single` ViewModel keeps the last scan's rows and its jobs alive for the whole process —
 * the competitor's Activity-field behaviour with a longer lifetime.
 *
 * Repositories and engines belong to a `<cluster>DataModule` in `:data`, which is the only module
 * allowed to declare them (§6.4: one declaring module per type, or a duplicate is a silent override).
 *
 * Provenance of everything these ViewModels resolve, so nobody re-derives why none of it is here:
 *
 * | What | Declared in |
 * |---|---|
 * | `StorageScanner` · `MediaStoreRepository` · `FileDeleter` · `FileDigest` · `StorageRootProvider` | `storageDataModule` |
 * | `AppStorageStatsRepository` · `AppControlRepository` · `WhatsAppRoots` · `WhatsAppScanner` · `DuplicateFinder` | `filesDataModule` |
 * | `InstalledAppsRepository` · `FeatureUsageRepository` · `CleanupLedger` · `AnalyticsRepository` · `PermissionRepository` | `coreDataModule` |
 * | `AppIconLoader` | `coreUiModule` |
 * | `AppLogger` · `DispatcherProvider` | `coreModule` |
 * | every use case | `domainModule`, as a `factory` |
 *
 * `SavedStateHandle` comes from `params`, not a `get()`: Koin hands over the nav back-stack entry's
 * handle, and without it the selection is re-read from scratch on every recreation (`LLM.md` §6.3).
 *
 * WIRING — these bindings resolve only once `domainModule` declares this cluster's use cases and
 * `coreDataModule` declares `InstalledAppsRepository`. Both files belong to other owners and the
 * exact lines are reported rather than added here, because adding a `single` to a module this
 * cluster does not own is precisely the defect §6.4 prevents.
 *
 * Screens: bigfiles · duplicates · video · videocompressor · videocompressrun · audio · appmanager ·
 * whatsapp
 */
val filesModule = module {
    // One more `get()` than before phase 07: `PermissionRepository`, resolved from `coreDataModule`
    // — the confirm dialog's advisory read of `AppPermission.AllFiles` (plan `260908-0801-trash-bin`).
    viewModel { params ->
        BigFilesViewModel(params.get(), get(), get(), get(), get(), get(), get(), get())
    }

    viewModel { params ->
        DuplicatesViewModel(params.get(), get(), get(), get(), get(), get(), get(), get())
    }

    viewModel { params ->
        VideoManagerViewModel(params.get(), get(), get(), get(), get(), get(), get(), get())
    }

    // One more `get()` than `video`: `VideoEncoderCapabilities`, bound as a `single` in
    // `filesDataModule` (phase 04). `params.get()` for the `SavedStateHandle` — this screen keeps a
    // selection, a preset and a codec across process death (`LLM.md` §6.3).
    viewModel { params ->
        VideoCompressorViewModel(params.get(), get(), get(), get(), get(), get(), get())
    }

    // params.get() for SavedStateHandle (ids/preset/codec are route scalars, LLM.md §7.2), then
    // compressVideos, estimateCompression, checkSpace, deleteFiles, videos (VideoCandidateRepository),
    // analytics, permissions, log — in that constructor order (phase-07-run-screen.md step 7;
    // `permissions` added by plan `260908-0801-trash-bin` Phase 07).
    viewModel { params ->
        VideoCompressRunViewModel(
            params.get(), get(), get(), get(), get(), get(), get(), get(), get(),
        )
    }

    viewModel { params ->
        AudioManagerViewModel(params.get(), get(), get(), get(), get(), get(), get(), get())
    }

    viewModel { params ->
        AppManagerViewModel(params.get(), get(), get(), get(), get(), get(), get())
    }

    // No `params`: this screen keeps no selection across process death — buckets are re-scanned,
    // and a bucket id restored against a list that has not arrived yet selects nothing.
    viewModelOf(::WhatsAppCleanerViewModel)
    viewModelOf(::ZipFilesViewModel)
}
