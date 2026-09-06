package com.pion.phonecleaner.data.di

import com.pion.phonecleaner.data.photo.AndroidExifRepository
import com.pion.phonecleaner.data.photo.BitmapPhotoCompressor
import com.pion.phonecleaner.data.photo.DctPerceptualHasher
import com.pion.phonecleaner.data.photo.DefaultBlurryPhotoScanner
import com.pion.phonecleaner.data.photo.DefaultSimilarPhotoScanner
import com.pion.phonecleaner.data.ledger.DataStoreCompressedPhotoLedger
import com.pion.phonecleaner.data.photo.InMemoryBlurryPhotoSessionStore
import com.pion.phonecleaner.data.photo.InMemorySimilarPhotoSessionStore
import com.pion.phonecleaner.data.photo.LaplacianBlurDetector
import com.pion.phonecleaner.data.photo.MediaStorePhotoRepository
import com.pion.phonecleaner.domain.repository.BlurDetector
import com.pion.phonecleaner.domain.repository.BlurryPhotoScanner
import com.pion.phonecleaner.domain.repository.BlurryPhotoSessionStore
import com.pion.phonecleaner.domain.repository.CompressedPhotoLedger
import com.pion.phonecleaner.domain.repository.ExifRepository
import com.pion.phonecleaner.domain.repository.PerceptualHasher
import com.pion.phonecleaner.domain.repository.PhotoCompressor
import com.pion.phonecleaner.domain.repository.PhotoRepository
import com.pion.phonecleaner.domain.repository.SimilarPhotoScanner
import com.pion.phonecleaner.domain.repository.SimilarPhotoSessionStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * `photoDataModule` — the photo cluster's own components (`LLM.md` §6.1).
 *
 * Every implementation here is `internal` to `:data`, so no `:feature` module could declare one even
 * by mistake. Nothing on §6.4's shared list appears: `MediaStoreRepository`, `FileDeleter` and
 * `FileDigest` are `storageDataModule`'s, and this cluster composes them rather than rebinding them.
 *
 * `SimilarPhotoSessionStore` is an in-memory `single` and not a Room table on purpose: a scan result
 * is a session, not a record (`docs/system-architecture.md` §5.7). Room holds exactly two tables.
 *
 * `CompressedPhotoLedger` is a `single` here rather than in `coreDataModule` because no other cluster
 * names it — §6.4 allows a per-cluster module exactly that. It writes through the one
 * `DataStore<Preferences>` `coreModule` binds; it never creates a second store.
 */
val photoDataModule = module {
    single<PhotoRepository> { MediaStorePhotoRepository(androidContext(), get(), get()) }
    single<PerceptualHasher> { DctPerceptualHasher(androidContext(), get()) }
    single<SimilarPhotoScanner> { DefaultSimilarPhotoScanner(get(), get(), get()) }
    single<PhotoCompressor> { BitmapPhotoCompressor(androidContext(), get(), get(), get()) }
    single<ExifRepository> { AndroidExifRepository(androidContext(), get(), get(), get()) }
    single<SimilarPhotoSessionStore> { InMemorySimilarPhotoSessionStore() }
    single<BlurDetector> { LaplacianBlurDetector(androidContext()) }
    single<BlurryPhotoScanner> { DefaultBlurryPhotoScanner(get(), get(), get()) }
    // A SECOND session store, not a second binding of the first: the two screens can be open in the
    // same back stack and a delete on one must not prune the other's groups (BlurryPhotoSessionStore).
    single<BlurryPhotoSessionStore> { InMemoryBlurryPhotoSessionStore() }
    single<CompressedPhotoLedger> { DataStoreCompressedPhotoLedger(get()) }
}
