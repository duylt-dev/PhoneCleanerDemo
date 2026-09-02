package com.pion.phonecleaner.feature.notification.permissionmanager.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.permission.AppPermissionReport
import com.pion.phonecleaner.domain.model.permission.PermissionGroup
import com.pion.phonecleaner.feature.notification.R
import com.pion.phonecleaner.feature.notification.component.AppIconImage
import com.pion.phonecleaner.feature.notification.permissionmanager.SpecialAccessRow

/** One app on the Apps tab, and the same row under an expanded group on the Sensitive tab. */
@Composable
internal fun AppPermissionRow(
    report: AppPermissionReport,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = ScreenGutter, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        AppIconImage(packageName = report.packageName)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                text = report.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.permission_manager_sensitive_count,
                    report.sensitiveCount,
                    report.sensitiveCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A group header on the Sensitive tab. It replaces one of the two `ExpandableListView`s — and with it
 * the `hasStableIds()` defect where every child id collapses to its position
 * (`docs/screens/17-notification-and-permissions.md` §4.3).
 */
@Composable
internal fun GroupHeader(
    group: PermissionGroup,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = ScreenGutter, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(group.id.labelRes()),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = pluralStringResource(
                R.plurals.permission_manager_group_app_count,
                group.apps.size,
                group.apps.size,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (expanded) CollapseGlyph else ExpandGlyph,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One special-access row, with the grant state the competitor's version cannot show (§4.5). */
@Composable
internal fun SpecialAccessRowUi(
    row: SpecialAccessRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = ScreenGutter, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(row.access.labelRes()),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        AssistChip(
            onClick = onClick,
            label = {
                Text(
                    stringResource(
                        if (row.isGranted) R.string.permission_manager_granted
                        else R.string.permission_manager_not_granted,
                    ),
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                labelColor = if (row.isGranted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ),
        )
    }
}

/**
 * Text disclosure markers rather than icons: this module carries no chevron drawable and
 * `material-icons-extended` is not on its classpath. A fabricated `R.drawable.*` would not compile.
 */
private const val ExpandGlyph = "▾"
private const val CollapseGlyph = "▴"
