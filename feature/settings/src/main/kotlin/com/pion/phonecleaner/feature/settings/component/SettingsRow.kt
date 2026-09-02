package com.pion.phonecleaner.feature.settings.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing

/**
 * One row of a [SettingsCard]. Replaces every copy of the competitor's six-view `RelativeLayout`
 * (`docs/screens/20-settings-language-and-push.md` §1.3).
 *
 * @param trailing the row's current value, rendered after the label. The competitor shows nothing
 *   here, so the user must enter the next screen to learn what is selected (§1.4 delta 4).
 * @param badge a count the row draws attention to; `null` hides it. `0` must be passed as `null` by
 *   the caller — a badge reading "0" is a claim that something is wrong when nothing is.
 */
@Composable
internal fun SettingsRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    badge: String? = null,
) {
    SettingsRowFrame(
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
        label = label,
    ) {
        if (badge != null) {
            Badge { Text(badge) }
        }
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A row whose whole hit area toggles a [Switch].
 *
 * `Role.Switch` on the row plus `onCheckedChange = null` on the control: one announced, focusable
 * target instead of the competitor's card-with-a-decorative-button, where the visible control is not
 * the thing you press (§5.4 delta 6).
 */
@Composable
internal fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    SettingsRowFrame(
        modifier = modifier.clickable(role = Role.Switch) { onCheckedChange(!checked) },
        label = label,
        supporting = supporting,
    ) {
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** The shape both rows share, so the two cannot drift apart in height or padding. */
@Composable
private fun SettingsRowFrame(
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .padding(horizontal = ScreenGutter, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        RowLabel(label = label, supporting = supporting, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun RowLabel(label: String, supporting: String?, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (supporting != null) {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The Material minimum touch target. A position, not a gap — named, per MVI §11. */
private val RowMinHeight = 56.dp
