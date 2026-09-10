package com.pion.phonecleaner.feature.photo.di

import com.pion.phonecleaner.feature.photo.albumdetail.AlbumDetailViewModel
import com.pion.phonecleaner.feature.photo.blurry.BlurryPhotosViewModel
import com.pion.phonecleaner.feature.photo.albums.AlbumsViewModel
import com.pion.phonecleaner.feature.photo.compressor.PhotoCompressorViewModel
import com.pion.phonecleaner.feature.photo.compressrun.CompressRunViewModel
import com.pion.phonecleaner.feature.photo.preview.PhotoPreviewViewModel
import com.pion.phonecleaner.feature.photo.privacy.PhotoPrivacyViewModel
import com.pion.phonecleaner.feature.photo.similar.SimilarPhotosViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * `:feature:photo` — **`viewModelOf` / `viewModel { }` only. Never a `single`** (`LLM.md` §3.7, §6.4,
 * and `docs/screens/13-photo-and-media.md` §0.4).
 *
 * Every repository and engine these ViewModels take is declared once elsewhere:
 * `PhotoRepository` · `SimilarPhotoScanner` · `PerceptualHasher` · `PhotoCompressor` ·
 * `ExifRepository` · `SimilarPhotoSessionStore` · `BlurDetector` · `BlurryPhotoScanner` ·
 * `BlurryPhotoSessionStore` in `photoDataModule`; `FileDeleter` in
 * `storageDataModule`; `AnalyticsRepository` and `FeatureUsageRepository` in `coreDataModule`;
 * `AppLogger` and `DispatcherProvider` in `coreModule`. A second declaration of any of them is a
 * silent Koin override decided by module load order, not a compile error (`LLM.md` §6.4).
 *
 * The `viewModel { params -> … savedStateHandle = params.get() … }` form is required wherever a
 * screen reads a route argument **or persists a selection**: it is what makes Koin hand over the
 * nav back-stack entry's `SavedStateHandle` rather than a fresh one (`LLM.md` §6.3).
 *
 * The photo **use cases** are deliberately absent. `LLM.md` §6.4 gives every use case one home,
 * `domainModule`, and `domain/di/DomainModule.kt` is not this cluster's file; the `factoryOf` lines
 * it still needs are reported to its owner rather than declared a second time here.
 *
 * `SimilarPhotosViewModel` and `BlurryPhotosViewModel` take no `SavedStateHandle`: each one's
 * selection lives in its own session store, which `preview` writes to and the grid observes, so
 * `viewModelOf` is the right form for both (§1.2, §2.4).
 */
val photoModule = module {
    viewModelOf(::AlbumsViewModel)
    viewModel { params ->
        AlbumDetailViewModel(
            savedStateHandle = params.get(),
            loadAlbumPhotos = get(),
            deletePhotos = get(),
            analytics = get(),
            // PermissionRepository, from coreDataModule — the delete confirm's advisory read of
            // AppPermission.AllFiles (plan 260908-0801-trash-bin, Phase 07).
            permissions = get(),
            log = get(),
        )
    }
    // SimilarPhotosViewModel and BlurryPhotosViewModel both gained a PermissionRepository
    // constructor parameter in the same phase; viewModelOf resolves it by type, no change needed here.
    viewModelOf(::SimilarPhotosViewModel)
    // Same form and the same reason as SimilarPhotosViewModel: its selection lives in
    // BlurryPhotoSessionStore, so it takes no SavedStateHandle.
    viewModelOf(::BlurryPhotosViewModel)
    viewModel { params ->
        PhotoPreviewViewModel(
            savedStateHandle = params.get(),
            // Both stores, one chosen from the route's `source` argument. See the ViewModel's KDoc:
            // the two grids keep separate sessions, so a delete on one must not prune the other's.
            similar = get(),
            blurry = get(),
            log = get(),
        )
    }
    viewModel { params ->
        PhotoCompressorViewModel(
            savedStateHandle = params.get(),
            loadCompressiblePhotos = get(),
            estimateSavings = get(),
            markFeatureUsed = get(),
            analytics = get(),
            log = get(),
        )
    }
    viewModel { params ->
        CompressRunViewModel(
            savedStateHandle = params.get(),
            compressPhotos = get(),
            photos = get(),
            analytics = get(),
            log = get(),
        )
    }
    viewModel { params ->
        PhotoPrivacyViewModel(
            savedStateHandle = params.get(),
            loadGeotagged = get(),
            stripLocation = get(),
            markFeatureUsed = get(),
            analytics = get(),
            log = get(),
        )
    }
}
