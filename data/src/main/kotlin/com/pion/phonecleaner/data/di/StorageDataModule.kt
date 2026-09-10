package com.pion.phonecleaner.data.di

import com.pion.phonecleaner.data.storage.AndroidStorageInfoRepository
import com.pion.phonecleaner.data.storage.AndroidStorageRootProvider
import com.pion.phonecleaner.data.storage.BoundedDirectorySizer
import com.pion.phonecleaner.data.storage.DefaultFileDeleter
import com.pion.phonecleaner.data.storage.DefaultMediaStoreRepository
import com.pion.phonecleaner.data.storage.DefaultStorageScanner
import com.pion.phonecleaner.data.storage.Md5FileDigest
import com.pion.phonecleaner.domain.repository.DirectorySizer
import com.pion.phonecleaner.domain.repository.FileDeleter
import com.pion.phonecleaner.domain.repository.FileDigest
import com.pion.phonecleaner.domain.repository.MediaStoreRepository
import com.pion.phonecleaner.domain.repository.StorageInfoRepository
import com.pion.phonecleaner.domain.repository.StorageRootProvider
import com.pion.phonecleaner.domain.repository.StorageScanner
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * `storageDataModule` — the file primitives and their support (`docs/system-architecture.md` §5.6).
 *
 * **This file is the entire cost of switching storage branch** (§8.4). The default branch is
 * `MediaStore` + the Storage Access Framework; the all-files branch swaps these implementations and
 * nothing else. A screen, a ViewModel and a use case never learn which branch is active, because they
 * name `StorageScanner` / `MediaStoreRepository` / `FileDeleter` — three `:domain` interfaces — and a
 * feature engine composes them. `DeleteOutcome.PendingConsent` exists in **both** branches so the
 * consent round trip is already handled everywhere; in the all-files branch the case never fires.
 *
 * The competitor collapses all of this into `deleteRecursively` behind `MANAGE_EXTERNAL_STORAGE`
 * (`MenaremovActivity.java:242`), which on API 30+ still cannot read `Android/data` or `Android/obb`
 * at all — so its most valuable catalogue rules are dead on the devices that need them.
 *
 * ### One deviation from §5.6's snippets, applied consistently
 *
 * §5.6 writes `Md5FileDigest(get())`, `BoundedDirectorySizer()` and
 * `AndroidStorageRootProvider(androidContext())`. Each implementation here takes a
 * `DispatcherProvider` as well, because §7.4 states the rule those snippets predate: **the dispatcher
 * choice is made inside the repository — `flowOn(dispatchers.io)` — not at the call site**, and never
 * as a `named("io")` qualifier. `cd.d.d`'s caller picks its dispatcher, so one repository behaves
 * differently on each screen. `Md5FileDigest` additionally takes a `Context`: in the default branch a
 * media row's only readable handle is its `content://` URI, and a digest that can only open paths
 * returns "no duplicates" for every media file on a modern device.
 *
 * The class names are §5.6's. `LLM.md` §3.6's tree spells three of them `…Impl`; §5's own naming rule
 * forbids that here, and cites this exact package as the reason — *"the mechanism is the thing that
 * changes; `Impl` on a class whose mechanism matters hides the change behind a name that says
 * nothing"*.
 */
val storageDataModule = module {

    single<StorageRootProvider> { AndroidStorageRootProvider(androidContext(), get(), get()) }

    // The third argument is `TrashRoots` (plan 260908-0801 phase 03). Without it the scanner's
    // exclusion is inert and an all-files walk lists every trashed file straight back into big files,
    // duplicates and the junk rules — so the junk cleaner would delete what the user can still restore.
    single<StorageScanner> { DefaultStorageScanner(androidContext(), get(), get()) }

    single<MediaStoreRepository> { DefaultMediaStoreRepository(androidContext(), get()) }

    single<FileDeleter> { DefaultFileDeleter(androidContext(), get()) }

    single<FileDigest> { Md5FileDigest(androidContext(), get()) }

    single<DirectorySizer> { BoundedDirectorySizer(get()) }

    single<StorageInfoRepository> { AndroidStorageInfoRepository(androidContext(), get()) }
}
