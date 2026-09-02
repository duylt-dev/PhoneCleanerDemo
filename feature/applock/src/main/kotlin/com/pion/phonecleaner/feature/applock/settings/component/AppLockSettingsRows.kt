package com.pion.phonecleaner.feature.applock.settings.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing

/**
 * The three row shapes App Lock settings is made of (`docs/screens/16-app-lock.md` §4.3).
 *
 * A Material 3 [Switch] with an explicit `stateDescription`, **not** an `ImageView` with two
 * drawables: the competitor's toggle is invisible to TalkBack and to any UI test that queries toggle
 * state, because nothing about it is a toggle to the accessibility tree.
 */
@Composable
internal fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    stateDescription: String,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    RowFrame(icon = icon, title = title, subtitle = subtitle, modifier = modifier) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { this.stateDescription = stateDescription },
        )
    }
}

@Composable
internal fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RowFrame(
        icon = icon,
        title = title,
        subtitle = subtitle,
        modifier = modifier.clickable(onClick = onClick),
    )
}

/**
 * The destructive row. It is a row and not a `Button` because it sits in the same column as the
 * other three; what marks it is the error colour, and the confirmation it opens.
 */
@Composable
internal fun SettingsDestructiveRow(
    icon: ImageVector,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = ScreenGutter, vertical = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null, // decorative: `title` already says it
            modifier = Modifier.size(RowIconSize),
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            text = title,
            modifier = Modifier.padding(start = Spacing.lg),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun RowFrame(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenGutter, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(RowIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = Spacing.lg),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

/** A **position**, not a gap (MVI §11): 24 dp is the Material list-row leading-icon size. */
private val RowIconSize = 24.dp
