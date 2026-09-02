package com.pion.phonecleaner.feature.notification.di

import com.pion.phonecleaner.feature.notification.gate.NotificationGateViewModel
import com.pion.phonecleaner.feature.notification.hiddenlist.HiddenNotificationsViewModel
import com.pion.phonecleaner.feature.notification.permissionmanager.PermissionManagerViewModel
import com.pion.phonecleaner.feature.notification.hidingsettings.NotificationHidingSettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * `notificationModule` — the presentation module for the notification cluster.
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
 * | `NotificationCleanerRepository` · `NotificationHidingSettingsStore` · `HiddenNotificationNotifier` · `NotificationInterceptionPolicy` · `AppPermissionScanRepository` · `FeatureStatsRepository` | `notificationDataModule` |
 * | `PermissionRepository` · `InstalledAppsRepository` · `AnalyticsRepository` · `FeatureUsageRepository` · `AppNotifier` · `AppDatabase` + `hiddenNotificationDao()` | `coreDataModule` |
 * | `AppIconLoader` | `coreUiModule` |
 * | `MinimumDuration` · `DispatcherProvider` · `AppLogger` | `coreModule` |
 * | every use case | `domainModule`, as a `factory` |
 * | `SensitivePermissionCatalog` · `PermissionGrouping` | **not in Koin** — pure objects in `:domain` |
 *
 * Screens: gate · hidingsettings · hiddenlist · permissionmanager
 */
val notificationModule = module {
    viewModelOf(::NotificationGateViewModel)
    viewModelOf(::NotificationHidingSettingsViewModel)
    viewModelOf(::HiddenNotificationsViewModel)

    // `SavedStateHandle` comes from `params`, not a `get()`: Koin hands over the nav back-stack
    // entry's handle, and without it the `tab` route argument is not readable at all (`LLM.md` §6.3).
    viewModel { params ->
        PermissionManagerViewModel(params.get(), get(), get(), get(), get(), get(), get())
    }
}
