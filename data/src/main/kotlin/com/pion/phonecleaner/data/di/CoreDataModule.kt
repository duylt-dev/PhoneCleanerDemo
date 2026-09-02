package com.pion.phonecleaner.data.di

import com.pion.phonecleaner.data.analytics.RemoteAnalyticsRepository
import com.pion.phonecleaner.data.database.AppDatabase
import com.pion.phonecleaner.data.ledger.DataStoreCleanupLedger
import com.pion.phonecleaner.data.app.PackageManagerInstalledAppsRepository
import com.pion.phonecleaner.data.notification.AppNotifier
import com.pion.phonecleaner.data.notification.AppNotifierImpl
import com.pion.phonecleaner.data.permission.AndroidPermissionRepository
import com.pion.phonecleaner.data.ledger.DataStoreFeatureUsageRepository
import com.pion.phonecleaner.data.lifecycle.AppLifecycleObserver
import com.pion.phonecleaner.domain.repository.AnalyticsRepository
import com.pion.phonecleaner.domain.repository.CleanupLedger
import com.pion.phonecleaner.domain.repository.FeatureUsageRepository
import com.pion.phonecleaner.domain.repository.InstalledAppsRepository
import com.pion.phonecleaner.domain.repository.PermissionRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import kotlin.random.Random

/**
 * `coreDataModule` — the `:data` bindings **every cluster shares** (`docs/system-architecture.md`
 * §5.4, `LLM.md` §6.4).
 *
 * ### Read §5.1 before adding a line here, and before adding a `single` anywhere else
 *
 * > **Koin overrides silently by default.** Two `single<X>` declarations in two loaded modules are not
 * > a compile error and not a startup error — the winner is whichever module loaded last. A duplicate
 * > binding is a **runtime coin-flip on module load order.**
 *
 * That is not hypothetical here. Twelve independently written cluster designs contained, between
 * them, **six** declarations of `single<FeatureUsageRepository>` (one under a different name),
 * **three** of `single<PermissionRepository>`, **four** of `single<InstalledAppsRepository>`, four of
 * an app-icon loader under three names, two of `single<CleanupLedger>`, and a second `single<Clock>`.
 * Every one is a screen silently getting a different instance depending on load order.
 *
 * **A per-cluster module declares only types no other cluster names.** Everything on §6.4's shared
 * list is declared here, once, and removed from the cluster modules — not merely mentioned once in
 * prose. `checkModules()` in `:app` catches a *missing* binding; nothing catches a duplicate, so this
 * rule is the only defence.
 */
val coreDataModule = module {

    single<FeatureUsageRepository> {
        // Random.Default and not a `single<Random>`: the argument exists so a test can pin the
        // recommendation, and a test constructs this class directly (§5.3).
        DataStoreFeatureUsageRepository(get(), get(), Random.Default)
    }

    single<AnalyticsRepository> { RemoteAnalyticsRepository(get()) }

    single<CleanupLedger> { DataStoreCleanupLedger(get()) }

    single { AppLifecycleObserver() }

    // Claimed by clusters 02, 05 and 11 — declared HERE and nowhere else (§6.4). The implementation
    // is `internal` to :data, so no cluster module could declare it even by mistake.
    single<PermissionRepository> { AndroidPermissionRepository(androidContext(), get()) }

    // Claimed by clusters 05, 07, 08 and 09. THREE arguments — the draft comment below guessed two;
    // the class as written takes (Context, DispatcherProvider, PermissionRepository).
    single<InstalledAppsRepository> { PackageManagerInstalledAppsRepository(androidContext(), get(), get()) }

    // Room — exactly two tables (§7.3). The DAOs are bound here and not in the clusters that read
    // them: `hidden_notifications` is read by the notification cluster and `threat_cache` by the
    // security cluster, and both appendices say explicitly that this module owns them
    // (docs/screens/17:718, docs/screens/15:208).
    single { AppDatabase.build(androidContext()) }
    single { get<AppDatabase>().hiddenNotificationDao() }
    single { get<AppDatabase>().threatCacheDao() }

    // The one place the app posts a notification, so the one place a channel is created. Claimed by
    // clusters 08 and 11; homed here rather than in either, because two `single<AppNotifier>` lines
    // in two cluster modules is a load-order coin flip at runtime and not a compile error
    // (`LLM.md` §6.4). `AppNotifierImpl` is `internal` to :data, so no cluster module could declare
    // it even by mistake.
    single<AppNotifier> { AppNotifierImpl(androidContext(), get()) }
}
