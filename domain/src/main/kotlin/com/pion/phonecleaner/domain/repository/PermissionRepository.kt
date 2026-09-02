package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.permission.AppPermission
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.coroutines.flow.Flow

/**
 * "Does **this** app hold permission P?" — port 1 of the three the permission layer splits into.
 *
 * The competitor concentrates this in `od.z` (~870 lines, 14 predicates) plus a permission-library
 * wrapper; eight cluster designs produced eight ports of it and three of them declared
 * `single<PermissionRepository>` in three different Koin modules — a silent runtime override, not a
 * compile error. **One binding, in `coreDataModule`** (`docs/system-architecture.md` §4.4, §5.4).
 *
 * The losers, which must not reappear: `UsageAccessRepository`, `NotificationAccessRepository`,
 * `AppLockPermissionRepository`, `SpecialAccessRepository`.
 *
 * Port 2 is [AppPermissionScanRepository]. Port 3 is **not a repository at all**: launching a system
 * settings screen is an Effect the Route performs over `:data/permission/SpecialAccessIntents.kt`.
 *
 * [observe] re-emits on `ProcessLifecycle` RESUMED, which is what makes the permission funnel work:
 * the user grants in Settings, presses back, `LifecycleResumeEffect` fires `ScreenResumed`, this flow
 * re-emits and the same reducer is re-entered (`LLM.md` §7.4). The competitor's Permission Centre
 * checks once and overrides no `onResume`, so granting and returning leaves the stale card on screen.
 */
interface PermissionRepository {

    fun observe(): Flow<ImmutableSet<AppPermission>>

    fun isGranted(permission: AppPermission): Boolean

    /**
     * What [feature] still needs. Reads `FeatureCatalog.requires(feature)` and subtracts what is
     * held. Empty means the reducer may raise its navigation Effect.
     */
    fun missingFor(feature: FeatureId): ImmutableSet<AppPermission>
}
