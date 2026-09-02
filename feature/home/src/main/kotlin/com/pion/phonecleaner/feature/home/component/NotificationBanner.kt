package com.pion.phonecleaner.feature.home.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenGutter
import com.pion.phonecleaner.feature.home.HomeIntent
import com.pion.phonecleaner.feature.home.R

/**
 * The competitor's `crutur` row, which asks for notification permission from the page itself.
 *
 * It is the sheet's second door, and in the competitor the two doors do not obey the same rules: the
 * `onResume` door is capped by a remote-config counter and the banner bypasses both the counter and
 * the cap (`docs/reverse-engineering/11-home.md` §7.3). Here the banner raises the same intent as
 * every other ask, so there is one path and one place a cap could ever live.
 *
 * The copy names what a notification is *for*. It makes no claim about what the app will find.
 */
@Composable
internal fun NotificationBanner(
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.screenGutter().fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(Icons.Filled.Notifications, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_notification_banner_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.home_notification_banner_body),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = { onIntent(HomeIntent.NotificationBannerTapped) }) {
                Text(stringResource(R.string.home_notification_banner_action))
            }
        }
    }
}
