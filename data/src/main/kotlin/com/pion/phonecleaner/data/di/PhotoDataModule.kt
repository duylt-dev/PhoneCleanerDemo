package com.pion.phonecleaner.data.di

import com.pion.phonecleaner.data.photo.AndroidExifRepository
import com.pion.phonecleaner.data.photo.BitmapPhotoCompressor
import com.pion.phonecleaner.data.photo.DctPerceptualHasher
import com.pion.phonecleaner.data.photo.DefaultSimilarPhotoScanner
import com.pion.phonecleaner.data.photo.InMemorySimilarPhotoSessionStore
import com.pion.phonecleaner.data.photo.MediaStorePhotoRepository
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
 */
val photoDataModule = module {
    single<PhotoRepository> { MediaStorePhotoRepository(androidContext(), get(), get()) }
    single<PerceptualHasher> { DctPerceptualHasher(androidContext(), get()) }
    single<SimilarPhotoScanner> { DefaultSimilarPhotoScanner(get(), get(), get()) }
    single<PhotoCompressor> { BitmapPhotoCompressor(androidContext(), get(), get(), get()) }
    single<ExifRepository> { AndroidExifRepository(androidContext(), get(), get(), get()) }
    single<SimilarPhotoSessionStore> { InMemorySimilarPhotoSessionStore() }
}
