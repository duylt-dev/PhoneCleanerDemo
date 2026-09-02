package com.pion.phonecleaner.data.di

import com.pion.phonecleaner.data.notification.DataStoreNotificationHidingSettings
import com.pion.phonecleaner.data.notification.DataStoreFeatureStats
import com.pion.phonecleaner.data.notification.HiddenNotificationNotifierImpl
import com.pion.phonecleaner.data.notification.PackageManagerAppPermissionScanRepository
import com.pion.phonecleaner.data.notification.RoomNotificationCleanerRepository
import com.pion.phonecleaner.domain.policy.DefaultNotificationInterceptionPolicy
import com.pion.phonecleaner.domain.policy.NotificationInterceptionPolicy
import com.pion.phonecleaner.domain.repository.AppPermissionScanRepository
import com.pion.phonecleaner.domain.repository.FeatureStatsRepository
import com.pion.phonecleaner.domain.repository.HiddenNotificationNotifier
import com.pion.phonecleaner.domain.repository.NotificationCleanerRepository
import com.pion.phonecleaner.domain.repository.NotificationHidingSettingsStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * `notificationDataModule` — the `:data` bindings the notification cluster owns
 * (`docs/system-architecture.md` §5.7, `docs/screens/17-notification-and-permissions.md` §4.4).
 *
 * **It declares only types no other cluster names.** Everything on §6.4's shared list is declared in
 * `coreDataModule`, `storageDataModule` or `coreModule`, once. Koin overrides silently by load order,
 * so a second `single` of a shared type is a runtime coin flip, not a compile error (`LLM.md` §6.4).
 *
 * Declared elsewhere, deliberately absent here:
 *
 * | Binding | Its one home |
 * |---|---|
 * | `PermissionRepository` · `InstalledAppsRepository` · `AnalyticsRepository` · `FeatureUsageRepository` | `coreDataModule` |
 * | `AppDatabase` + `hiddenNotificationDao()` | `coreDataModule` — Room holds exactly two tables and this is one of them |
 * | `AppNotifier` | `coreDataModule` — it is the one place a notification is posted, app-wide |
 * | `AppIconLoader` | `coreUiModule` |
 * | `MinimumDuration` · `DispatcherProvider` · `AppClock` · `DataStore<Preferences>` | `coreModule` |
 * | every use case | `domainModule`, as a `factory` |
 * | `SensitivePermissionCatalog` · `PermissionGrouping` | **not in Koin** — pure objects in `:domain` |
 */
val notificationDataModule = module {

    single<NotificationHidingSettingsStore> {
        DataStoreNotificationHidingSettings(get(), get(), get(), get())
    }

    // The DAO comes from `coreDataModule`; this class never opens a second database.
    single<NotificationCleanerRepository> {
        RoomNotificationCleanerRepository(get(), get(), get())
    }

    single<HiddenNotificationNotifier> {
        HiddenNotificationNotifierImpl(androidContext(), get(), get(), get(), get())
    }

    // A pure rule with no state and no platform — but declared, because the interceptor service
    // resolves it through Koin's service-locator form and the system builds that service through its
    // no-arg constructor (§4.4). `SensitivePermissionCatalog` and `PermissionGrouping` stay out of
    // Koin because nothing resolves them that way (`LLM.md` §6.3).
    single<NotificationInterceptionPolicy> { DefaultNotificationInterceptionPolicy() }

    // `includeWithoutLauncher = false` is passed by this class, not by a caller: the App Manager and
    // the Permission Manager are two different enumerations of the same port (`InstalledAppsRepository`).
    single<AppPermissionScanRepository> {
        PackageManagerAppPermissionScanRepository(androidContext(), get(), get())
    }

    single<FeatureStatsRepository> { DataStoreFeatureStats(get(), get()) }
}
