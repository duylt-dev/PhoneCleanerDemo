package com.pion.phonecleaner.domain.policy

import com.pion.phonecleaner.domain.catalog.SensitivePermissionCatalog
import com.pion.phonecleaner.domain.model.permission.AppPermissionReport
import com.pion.phonecleaner.domain.model.permission.PermissionGroup
import com.pion.phonecleaner.domain.model.permission.PermissionGroupId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Which apps hold something in each sensitive permission group.
 *
 * A **pure function over two lists**, with no I/O and no platform: a unit test with no fakes
 * (`LLM.md` §4, §9). The competitor's equivalent is thirty lines of nested loops sitting inside a
 * ViewModel (`docs/screens/17-notification-and-permissions.md:138`).
 *
 * Placement: `docs/system-architecture.md:127` and `LLM.md` §3.3 both name
 * `PermissionGrouping.groupsFor` as one of the two `:domain` policies.
 * `docs/screens/17-notification-and-permissions.md:118` instead hangs `groupsFor` off
 * `AppPermissionScanRepository`; the two binding documents win, and a rule with no I/O does not
 * belong on a port.
 */
object PermissionGrouping {

    /**
     * One [PermissionGroup] per group id, in `SensitivePermissionCatalog`'s fixed order, each
     * carrying the apps that hold at least one of its permissions.
     *
     * Every group is returned, including empty ones: which groups exist is a property of the
     * catalogue, not of this device's app list, and filtering empties is the screen's job —
     * `PermissionManagerState.visibleGroups` already does exactly that
     * (`docs/screens/17-notification-and-permissions.md:537`). Deciding it here would make the
     * function's output depend on a rendering preference.
     *
     * The [apps] parameter is a plain `List` because it is an input; the returned list is immutable
     * (`LLM.md` §8).
     */
    fun groupsFor(apps: List<AppPermissionReport>): ImmutableList<PermissionGroup> =
        SensitivePermissionCatalog.groupIds
            .map { id -> PermissionGroup(id, SensitivePermissionCatalog.permissionsIn(id), appsIn(id, apps)) }
            .toImmutableList()

    private fun appsIn(
        group: PermissionGroupId,
        apps: List<AppPermissionReport>,
    ): ImmutableList<AppPermissionReport> {
        val names = SensitivePermissionCatalog.permissionsIn(group).toSet()
        return apps.filter { report -> report.sensitive.any { it.name in names } }.toImmutableList()
    }
}
