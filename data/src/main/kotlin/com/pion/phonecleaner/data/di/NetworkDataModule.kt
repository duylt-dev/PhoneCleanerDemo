package com.pion.phonecleaner.data.di

import com.pion.phonecleaner.data.network.NetworkStatsTrafficRepository
import com.pion.phonecleaner.data.network.UnconfiguredSpeedTestRepository
import com.pion.phonecleaner.domain.repository.NetworkTrafficRepository
import com.pion.phonecleaner.domain.repository.SpeedTestRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * `networkDataModule` — the network cluster's own `:data` components
 * (`docs/screens/19-network-and-speed-test.md` §4).
 *
 * `LLM.md` §6.4: a per-cluster module may declare only types **no other cluster names**. Everything
 * these two screens also need — `PermissionRepository`, `InstalledAppsRepository`,
 * `FeatureUsageRepository`, `AnalyticsRepository` (`coreDataModule`), `AppIconLoader`
 * (`coreUiModule`), `DispatcherProvider`, `AppClock`, `AppLogger` (`coreModule`) and every use case
 * (`domainModule`) — is bound elsewhere and is deliberately absent here. The cluster's own research
 * proposed six of those bindings under four invented names; Koin overrides silently, so each would
 * have been a load-order coin flip rather than a compile error.
 *
 * The two halves of this cluster are separable on purpose: dropping the speed test deletes the
 * `SpeedTestRepository` line below and two screens, and touches the traffic half not at all.
 */
val networkDataModule = module {

    single<NetworkTrafficRepository> {
        NetworkStatsTrafficRepository(androidContext(), get(), get(), get())
    }

    /**
     * PENDING OWNER DECISION 2 — the speed test's byte source (`docs/system-architecture.md` §10.1
     * P2). [UnconfiguredSpeedTestRepository] performs no measurement and reports no number; it emits
     * `SpeedTestProgress.NotConfigured` and completes. Settling the decision replaces **this one
     * line** with an implementation named for its mechanism, or deletes it together with the two
     * speed-test screens. Either way the traffic half above is untouched.
     */
    single<SpeedTestRepository> { UnconfiguredSpeedTestRepository() }
}
