package com.pion.phonecleaner.feature.notification.hiddenlist.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.pion.phonecleaner.core.ui.format.rememberRelativeTimestamp
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.notification.HiddenNotification
import com.pion.phonecleaner.feature.notification.R
import com.pion.phonecleaner.feature.notification.component.AppIconImage

/**
 * One hidden notification (`docs/screens/17-notification-and-permissions.md` §3.3).
 *
 * The timestamp is formatted **here**, from the model's `Instant`, so a locale change or a midnight
 * rollover re-renders correctly. The competitor holds a shared mutable `SimpleDateFormat` — and a
 * static **GMT** one at that, which is the wrong day for most of the world (§3.1).
 *
 * Kept flat and reverse-chronological for parity. Grouping by package with a `stickyHeader` is a
 * one-line change and is recorded as the obvious follow-up, not smuggled in (§3.5).
 */
@Composable
internal fun HiddenNotificationRow(
    notification: HiddenNotification,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timestamp = rememberRelativeTimestamp(
        remember(notification.postedAt) { notification.postedAt.toEpochMilliseconds() },
    )
    val title = notification.title.ifBlank { stringResource(R.string.hidden_notifications_no_title) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = ScreenGutter, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        AppIconImage(packageName = notification.packageName)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (notification.body.isNotBlank()) {
                Text(
                    text = notification.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = MaxBodyLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = timestamp,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Parity with the competitor's two-line cap; there is no expand, and none is invented here. */
private const val MaxBodyLines = 2
