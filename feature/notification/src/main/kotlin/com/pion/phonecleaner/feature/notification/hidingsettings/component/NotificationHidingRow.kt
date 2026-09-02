package com.pion.phonecleaner.feature.notification.hidingsettings.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.notification.NotificationHidingApp
import com.pion.phonecleaner.feature.notification.R
import com.pion.phonecleaner.feature.notification.component.AppIconImage

/**
 * One app row (`docs/screens/17-notification-and-permissions.md` §2.3).
 *
 * **A Material 3 `Switch`, with a `stateDescription`.** The competitor's switch is an `ImageView` with
 * two drawables, which is invisible to TalkBack and to any UI test that queries toggle state (§2.5).
 *
 * With the master switch off the row goes `enabled = false` and **stays visible**. The competitor sets
 * its whole `RecyclerView` to `GONE`, so the user loses the list at the moment they most want to
 * inspect it and the feature reads as uninstalled rather than paused (§2.5).
 *
 * [onCheckedChange] closes only over `app.packageName`, which is a stable `String`; nothing allocates a
 * lambda over the row object.
 */
@Composable
internal fun NotificationHidingRow(
    app: NotificationHidingApp,
    enabled: Boolean,
    isBusy: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stateLabel = stringResource(
        if (app.isHidingEnabled) R.string.notification_hiding_row_state_on
        else R.string.notification_hiding_row_state_off,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenGutter, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        AppIconImage(packageName = app.packageName)
        Text(
            text = app.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isBusy) {
            CircularProgressIndicator(Modifier.size(BusyIndicatorSize), strokeWidth = BusyStroke)
        } else {
            Switch(
                checked = app.isHidingEnabled,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.semantics { stateDescription = stateLabel },
            )
        }
    }
}

/** Positions, not gaps: sized to occupy the switch's footprint so the row does not reflow mid-write. */
private val BusyIndicatorSize = 24.dp
private val BusyStroke = 2.dp
