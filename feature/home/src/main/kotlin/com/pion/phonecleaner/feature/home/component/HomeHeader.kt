package com.pion.phonecleaner.feature.home.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenGutter
import com.pion.phonecleaner.feature.home.HomeIntent
import com.pion.phonecleaner.feature.home.R

/**
 * The page title, the settings gear and — only when something is actually missing — the permission
 * row. It draws no header of its own: `PageHeader` owns the height, the type scale and the gear slot
 * (MVI §11), and the warning goes in its `bannerSlot`.
 *
 * The competitor sets a granted permission card to `GONE`; a warning that *appears* rather than a card
 * that vanishes is the shape `LLM.md` §7.4 asks for.
 *
 * UNKNOWN — the resident status-bar widget ships opt-in, default OFF by owner decision, and this
 * header's action row is where its toggle would most naturally sit. **No report designs that
 * control** (`docs/screens/11-home.md` §4.3 item 5): not its copy, not whether it is a switch here or
 * a row in Settings, not what it says when the notification channel is blocked. Nothing is drawn for
 * it, because a toggle invented here would be a control the owner never specified.
 */
@Composable
internal fun HomeHeader(
    showWarning: Boolean,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    PageHeader(
        title = stringResource(R.string.home_header_title),
        modifier = modifier,
        actionLabel = stringResource(R.string.home_open_settings),
        actionIcon = Icons.Filled.Settings,
        onAction = { onIntent(HomeIntent.SettingsTapped) },
        bannerSlot = {
            if (showWarning) PermissionWarningRow { onIntent(HomeIntent.PermissionWarningTapped) }
        },
    )
}

@Composable
private fun PermissionWarningRow(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.screenGutter().fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ScreenGutter, vertical = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.WarningAmber, contentDescription = null)
            Text(
                text = stringResource(R.string.home_permission_warning),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
