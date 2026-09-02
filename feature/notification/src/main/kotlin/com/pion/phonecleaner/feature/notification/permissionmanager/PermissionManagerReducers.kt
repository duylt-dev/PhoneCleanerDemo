package com.pion.phonecleaner.feature.notification.permissionmanager

import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.model.permission.PermissionGroupId
import com.pion.phonecleaner.domain.repository.PermissionRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

/**
 * The pure half of `permissionmanager` — state in, state out, no I/O
 * (`docs/screens/17-notification-and-permissions.md` §4.2).
 *
 * Split out of the ViewModel for the reason `LLM.md` §4 gives: a file stays readable, and a fold over
 * a `Set` is testable through the ViewModel without a fake between it and the assertion.
 *
 * **Nothing here mutates.** `Tilhassl.l()` mutates the objects its `LiveData` holds and never re-posts;
 * Compose would never recompose from that (§4.5). Every function below returns a new value.
 */
internal fun PermissionManagerState.toggleGroup(id: PermissionGroupId): PermissionManagerState {
    val expanded = if (id in expandedGroups) expandedGroups - id else expandedGroups + id
    return copy(expandedGroups = expanded.toImmutableSet())
}

/**
 * Expand/collapse for the detail sheet's three buckets. It lives on `State`, so it survives rotation
 * **and** the refresh caused by returning from Settings — the competitor builds a new adapter on every
 * emission and loses all of it each time (§4.5).
 */
internal fun PermissionManagerState.toggleSection(
    section: PermissionSection,
): PermissionManagerState {
    val sheet = detailSheet ?: return this
    val sections = if (section in sheet.expandedSections) {
        sheet.expandedSections - section
    } else {
        sheet.expandedSections + section
    }
    return copy(detailSheet = sheet.copy(expandedSections = sections.toImmutableSet()))
}

/**
 * The six special-access rows of `ae.h1` / `ae.j1`, as constants of the shared `AppPermission` enum
 * (§0.1). `vd.d`'s parallel six-name list is dead code that looks authoritative, and is deleted (§4.5).
 *
 * `AppPermission.AllFiles` is **not** among them: `MANAGE_EXTERNAL_STORAGE` is never assumed grantable
 * (`docs/system-architecture.md` §8.1), and listing it here would advertise a branch this build does
 * not take.
 *
 * [SpecialAccessRow.isGranted] is new. The competitor's rows are a name and an icon, so its tab cannot
 * tell the user whether they already hold the grant; six reads make the tab honest at no structural
 * cost (§4.1).
 */
internal fun specialAccessRows(
    permissions: PermissionRepository,
): ImmutableList<SpecialAccessRow> =
    SpecialAccesses.map { access -> SpecialAccessRow(access, permissions.isGranted(access)) }
        .toImmutableList()

private val SpecialAccesses = listOf(
    AppPermission.NotificationListener,
    AppPermission.UsageStats,
    AppPermission.Overlay,
    AppPermission.WriteSettings,
    AppPermission.DoNotDisturb,
    AppPermission.IgnoreBatteryOptimizations,
)
