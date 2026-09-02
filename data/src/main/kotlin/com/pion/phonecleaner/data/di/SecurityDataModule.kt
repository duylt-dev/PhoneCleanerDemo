package com.pion.phonecleaner.data.di

import com.pion.phonecleaner.data.security.ConsentStore
import com.pion.phonecleaner.data.security.DataStoreConsentStore
import com.pion.phonecleaner.data.security.DefaultSecurityScanRepository
import com.pion.phonecleaner.data.security.PackageRemovalMonitorImpl
import com.pion.phonecleaner.data.security.RoomScanHistoryStore
import com.pion.phonecleaner.data.security.ScanHistoryStore
import com.pion.phonecleaner.data.security.TrustLookClient
import com.pion.phonecleaner.data.security.TrustLookClientImpl
import com.pion.phonecleaner.domain.repository.PackageRemovalMonitor
import com.pion.phonecleaner.domain.repository.SecurityScanRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * `securityDataModule` — the cloud-scan cluster's own `:data` components
 * (`docs/screens/15-antivirus.md` §0.6, `docs/system-architecture.md` §5.7).
 *
 * ### Every type below is named by no other cluster — which is what makes these `single`s legal
 *
 * `LLM.md` §6.4: a shared type is declared **exactly once**, in the module that owns its layer, and a
 * per-cluster module may declare only types no other cluster names. Koin overrides silently, so a
 * second `single<X>` is a load-order coin flip at runtime rather than a compile error.
 *
 * **Declared elsewhere, and deliberately absent here** (§0.6's own table):
 *
 * | Binding | Owner |
 * |---|---|
 * | `AppDatabase` and every DAO, `threat_cache` included | `coreDataModule` |
 * | `AppIconLoader` — this cluster's round-2 report declared it a second time | `coreUiModule` |
 * | `AnalyticsRepository`, `PermissionRepository`, `InstalledAppsRepository`, `FeatureUsageRepository` | `coreDataModule` |
 * | `FileDeleter` | `storageDataModule` |
 * | `DispatcherProvider`, `AppClock`, `AppLogger`, the app `DataStore` | `coreModule` |
 * | `MarkFeatureUsedUseCase`, `RemoveFindingUseCase`, `IgnoreFindingUseCase` | `domainModule` |
 *
 * WIRING — this module is not yet in `:app/App.kt`'s `appModules`, and `domainModule` does not yet
 * declare the two use cases; both files belong to other owners, so the exact lines are reported
 * rather than added. Nothing in this cluster resolves until they are there.
 */
val securityDataModule = module {

    // A `single`, so the vendor client is built ONCE per process: its constructor wipes its own
    // preference file, and the competitor builds one per screen entry (§1.5).
    single<TrustLookClient> { TrustLookClientImpl(androidContext(), get(), get()) }

    single<ConsentStore> { DataStoreConsentStore(get(), get()) }

    // The DAO comes from coreDataModule. This cluster opens no second database (§0.4).
    single<ScanHistoryStore> { RoomScanHistoryStore(get(), get(), get()) }

    single<SecurityScanRepository> { DefaultSecurityScanRepository(get(), get(), get()) }

    single<PackageRemovalMonitor> { PackageRemovalMonitorImpl(androidContext()) }
}
