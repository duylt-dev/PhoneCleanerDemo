package com.pion.phonecleaner.data.di

import com.pion.phonecleaner.data.app.PackageManagerAppControlRepository
import com.pion.phonecleaner.data.app.StorageStatsAppRepository
import com.pion.phonecleaner.data.files.DefaultWhatsAppScanner
import com.pion.phonecleaner.data.files.EmptyWhatsAppRoots
import com.pion.phonecleaner.data.files.Md5DuplicateFinder
import com.pion.phonecleaner.domain.repository.AppControlRepository
import com.pion.phonecleaner.domain.repository.AppStorageStatsRepository
import com.pion.phonecleaner.domain.repository.DuplicateFinder
import com.pion.phonecleaner.domain.repository.WhatsAppRoots
import com.pion.phonecleaner.domain.repository.WhatsAppScanner
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * `filesDataModule` — the file-tools cluster's own `:data` components
 * (`docs/screens/14-file-tools-and-app-manager.md` §0.4, §5.4, §6.4).
 *
 * ### Every type below is named by no other cluster — which is what makes these `single`s legal
 *
 * `LLM.md` §6.4: **a shared type is declared exactly once, in the module that owns its layer, and a
 * per-cluster module may declare only types no other cluster names.** Koin overrides silently by
 * default, so a second `single<X>` is a load-order coin flip at runtime, not a compile error.
 *
 * **`InstalledAppsRepository` is deliberately NOT here.** Four clusters claim it — files, app-lock,
 * notification and device — so it belongs in `coreDataModule` and nowhere else. This cluster wrote
 * its implementation (`data/app/PackageManagerInstalledAppsRepository.kt`, `internal` precisely so
 * that no cluster module *can* declare it) and reported the one line that binds it. Declaring it here
 * instead is the exact defect §5.1 opens with, and it would not fail the build.
 *
 * Declared elsewhere, and never redeclared here:
 *
 * | Binding | Owner |
 * |---|---|
 * | `StorageScanner`, `MediaStoreRepository`, `FileDeleter`, `FileDigest`, `StorageRootProvider`, `StorageInfoRepository`, `DirectorySizer` | `storageDataModule` |
 * | `InstalledAppsRepository`, `FeatureUsageRepository`, `CleanupLedger`, `AnalyticsRepository`, `PermissionRepository` | `coreDataModule` |
 * | `AppIconLoader` | `coreUiModule` |
 * | `DispatcherProvider`, `AppClock`, `AppLogger`, `MinimumDuration`, the app `DataStore` | `coreModule` |
 * | every use case | `domainModule`, as a `factory` |
 * | `ByteFormatter`, `FeatureCatalog` | nothing — pure objects gain nothing from injection |
 *
 * **No `single(named("io")) { Dispatchers.IO }`.** `DispatcherProvider` replaces the named
 * qualifiers, and the dispatcher choice is made inside each repository, never at a call site.
 *
 * WIRING — this module is not yet in `:app/App.kt`'s `appModules`; that file belongs to another
 * owner and the line is reported rather than added. Nothing in the files cluster resolves until it is
 * there.
 */
val filesDataModule = module {

    single<AppStorageStatsRepository> { StorageStatsAppRepository(androidContext(), get()) }

    single<AppControlRepository> { PackageManagerAppControlRepository(androidContext(), get()) }

    // PENDING OWNER DECISION (1) — the catalogue fork. `EmptyWhatsAppRoots` carries no path table;
    // an asset- or code-backed catalogue replaces exactly this line and nothing else.
    single<WhatsAppRoots> { EmptyWhatsAppRoots() }

    single<WhatsAppScanner> { DefaultWhatsAppScanner(get(), get(), get(), get()) }

    single<DuplicateFinder> { Md5DuplicateFinder(get(), get(), get(), get()) }
}
