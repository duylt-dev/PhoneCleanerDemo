package com.pion.phonecleaner.feature.notification.permissionmanager.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.state.EmptyState
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.permission.AppPermissionReport
import com.pion.phonecleaner.domain.model.permission.PermissionGroup
import com.pion.phonecleaner.domain.model.permission.PermissionGroupId
import com.pion.phonecleaner.feature.notification.R
import com.pion.phonecleaner.feature.notification.permissionmanager.PermissionManagerIntent
import com.pion.phonecleaner.feature.notification.permissionmanager.SpecialAccessRow
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

/**
 * The three tab bodies — three `LazyColumn`s over three slices of one `State`
 * (`docs/screens/17-notification-and-permissions.md` §4.3).
 *
 * `onIntent` is passed down as-is. A lambda allocated inside `items {}` is a new instance every
 * recomposition and defeats the skip for every row (`LLM.md` §8).
 */
@Composable
internal fun AppsTab(
    apps: ImmutableList<AppPermissionReport>,
    onIntent: (PermissionManagerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (apps.isEmpty()) {
        EmptyState(stringResource(R.string.permission_manager_empty), modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = Spacing.sm),
    ) {
        items(items = apps, key = { it.packageName }, contentType = { "app" }) { report ->
            AppPermissionRow(
                report = report,
                onClick = { onIntent(PermissionManagerIntent.AppRowTapped(report.packageName)) },
            )
        }
    }
}

/**
 * The Sensitive tab. `visibleGroups` has already dropped empty groups — a group with no apps is a row
 * that looks tappable and is not (§4.5).
 *
 * The child keys are `"${group.id}#${packageName}"`, which cannot collide: one app can appear under
 * several groups, so `packageName` alone is not unique in this list. The `ExpandableListView` this
 * replaces collapses every child id to its position.
 */
@Composable
internal fun SensitiveTab(
    groups: ImmutableList<PermissionGroup>,
    expandedGroups: ImmutableSet<PermissionGroupId>,
    onIntent: (PermissionManagerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (groups.isEmpty()) {
        EmptyState(stringResource(R.string.permission_manager_empty), modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = Spacing.sm),
    ) {
        groups.forEach { group ->
            item(key = group.id, contentType = "group") {
                GroupHeader(
                    group = group,
                    expanded = group.id in expandedGroups,
                    onClick = { onIntent(PermissionManagerIntent.GroupToggled(group.id)) },
                )
            }
            if (group.id in expandedGroups) {
                items(
                    items = group.apps,
                    key = { "${group.id}#${it.packageName}" },
                    contentType = { "app" },
                ) { report ->
                    AppPermissionRow(
                        report = report,
                        onClick = {
                            onIntent(PermissionManagerIntent.AppRowTapped(report.packageName))
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun SpecialAccessTab(
    rows: ImmutableList<SpecialAccessRow>,
    onIntent: (PermissionManagerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = Spacing.sm),
    ) {
        items(items = rows, key = { it.access }, contentType = { "special-access" }) { row ->
            SpecialAccessRowUi(
                row = row,
                onClick = { onIntent(PermissionManagerIntent.SpecialAccessTapped(row.access)) },
            )
        }
    }
}
