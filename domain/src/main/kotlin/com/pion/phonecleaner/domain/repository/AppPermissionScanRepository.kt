package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.permission.AppPermissionReport
import kotlinx.collections.immutable.ImmutableList

/**
 * "What permissions do **other** apps hold?" — port 2 of the three
 * (`docs/system-architecture.md` §4.4). Ports the competitor's per-app walker `vd.d.b`/`.c`/`.f`.
 *
 * Renamed from `PermissionScanRepository` / `AppPermissionsRepository` / `PermissionCatalogRepository`
 * so it cannot be read as "this app's permissions" — that is [PermissionRepository].
 *
 * DECLARED IN `notificationDataModule`, not `coreDataModule` (`docs/system-architecture.md` §5.7).
 * The interface is here because §4.4 defines it as one of the three ports the whole permission layer
 * collapses to; the implementation and its Koin binding belong to the notification cluster.
 *
 * NOTE for that cluster: `docs/screens/17-notification-and-permissions.md:118` also puts
 * `groupsFor(apps)` on this interface. It is **not** here. `docs/system-architecture.md:127` and
 * `LLM.md` §3.3 both place it in `:domain/policy/` as a pure function —
 * [com.pion.phonecleaner.domain.policy.PermissionGrouping] — and a pure rule over domain types is a
 * unit test with no fakes, which it stops being the moment it is a method on a port.
 */
interface AppPermissionScanRepository {

    suspend fun scanAll(): AppResult<ImmutableList<AppPermissionReport>>

    /** null means the package holds nothing worth reporting. */
    suspend fun refresh(packageName: String): AppResult<AppPermissionReport?>
}
