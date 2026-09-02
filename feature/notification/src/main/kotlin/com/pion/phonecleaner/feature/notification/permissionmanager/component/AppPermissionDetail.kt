package com.pion.phonecleaner.feature.notification.permissionmanager.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.permission.AppPermissionReport
import com.pion.phonecleaner.domain.model.permission.GrantedPermission
import com.pion.phonecleaner.feature.notification.R
import com.pion.phonecleaner.feature.notification.component.AppIconImage
import com.pion.phonecleaner.feature.notification.permissionmanager.PermissionManagerIntent
import com.pion.phonecleaner.feature.notification.permissionmanager.PermissionSection
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

/**
 * The detail sheet's body (`docs/screens/17-notification-and-permissions.md` §4.3).
 *
 * The **second** `ExpandableListView` disappears here. `expandedSections` lives on `State`, so it
 * survives rotation *and* the refresh caused by returning from Settings — the competitor builds a new
 * adapter on every emission and loses all expand/collapse state each time (§4.5).
 *
 * The `Other` bucket is rendered. The competitor drops every permission that is neither tabled nor
 * `PROTECTION_NORMAL`, so signature and untabled dangerous permissions are invisible and its two counts
 * do not add up to what the app holds (§4.5).
 */
@Composable
internal fun ColumnScope.AppPermissionDetail(
    report: AppPermissionReport,
    expandedSections: ImmutableSet<PermissionSection>,
    onIntent: (PermissionManagerIntent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        AppIconImage(packageName = report.packageName)
        Text(
            text = report.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
    }
    OutlinedButton(
        onClick = { onIntent(PermissionManagerIntent.ManageTapped) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.permission_manager_manage))
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = DetailListMaxHeight),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.sm),
    ) {
        section(PermissionSection.Sensitive, report.sensitive, expandedSections, onIntent)
        section(PermissionSection.Normal, report.normal, expandedSections, onIntent)
        section(PermissionSection.Other, report.other, expandedSections, onIntent)
    }
}

/**
 * An empty bucket still gets its header, so the three counts visibly add up to what the app holds.
 *
 * Keys are `"${section}#${permission.name}"`: one permission name can only appear in one bucket, but
 * the section prefix means a permission moving between buckets after a refresh is a new key rather than
 * a row that changes meaning underneath the diff.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.section(
    section: PermissionSection,
    permissions: ImmutableList<GrantedPermission>,
    expandedSections: ImmutableSet<PermissionSection>,
    onIntent: (PermissionManagerIntent) -> Unit,
) {
    item(key = "header#$section", contentType = "section") {
        SectionRow(
            section = section,
            count = permissions.size,
            expanded = section in expandedSections,
            onClick = { onIntent(PermissionManagerIntent.DetailSectionToggled(section)) },
        )
    }
    if (section !in expandedSections) return
    items(
        count = permissions.size,
        key = { index -> "$section#${permissions[index].name}" },
        contentType = { "permission" },
    ) { index ->
        PermissionRow(permissions[index])
    }
}

@Composable
private fun SectionRow(
    section: PermissionSection,
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(section.labelRes()),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (expanded) "▴" else "▾",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * `label` and `description` are nullable because they come from the *declaring* app's resources:
 * `getString(info.labelRes)` throws when `labelRes` is 0, and the competitor's per-permission `catch`
 * then drops the permission silently, so its counts drift with no signal (§4.5). Null renders the raw
 * AOSP name — honest, and never a dropped row.
 */
@Composable
private fun PermissionRow(permission: GrantedPermission) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(
            text = permission.label ?: permission.name,
            style = MaterialTheme.typography.bodyMedium,
        )
        permission.description?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A position, not a gap: the sheet's list stops here so the Manage button is never scrolled away. */
private val DetailListMaxHeight = 360.dp
