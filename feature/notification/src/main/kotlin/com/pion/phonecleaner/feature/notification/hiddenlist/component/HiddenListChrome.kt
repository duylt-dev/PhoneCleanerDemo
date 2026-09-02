package com.pion.phonecleaner.feature.notification.hiddenlist.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.state.LoadingOverlay
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.notification.R
import com.pion.phonecleaner.feature.notification.hiddenlist.ClearStage

/**
 * The banner shown while the master switch is off
 * (`docs/screens/17-notification-and-permissions.md` §3.3).
 *
 * The list keeps rendering behind it. The hidden-list route observes the master switch itself, which is
 * why `hidingsettings` needs no `NavigateToDisabledWall` Effect and why Back on that screen simply pops
 * (§2.5).
 */
@Composable
internal fun HidingPausedBanner(onEnable: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = ScreenGutter, vertical = Spacing.sm),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.hidden_notifications_paused_banner),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onEnable) {
                Text(stringResource(R.string.hidden_notifications_paused_action))
            }
        }
    }
}

/** The bottom bar. Disabled while clearing and when there is nothing to clear. */
@Composable
internal fun ClearAllButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenGutter, vertical = Spacing.md),
        enabled = enabled,
    ) {
        Text(stringResource(R.string.hidden_notifications_clear_all))
    }
}

/**
 * The clear overlay, and the timeout that is its real driver.
 *
 * §3.3 specifies a looping composition for `Running` and a one-shot completion composition with a
 * `finishedListener` for `Finished`, **plus** a `LaunchedEffect` timeout firing the same intent if the
 * animation never completes. There is no animation asset in this repository and none is invented, so
 * the timeout is the whole mechanism here and the overlay is `LoadingOverlay`. Swapping in a
 * composition is a change to this file and to nothing else — which is the point of the completion being
 * an Intent rather than an animation callback (`LLM.md` §7.1).
 *
 * `Finished` reaches `onFinished` **immediately**: the wipe is already committed by then, and holding a
 * committed result behind decoration is what leaves the competitor's overlay stranded when a rotation
 * resets the Activity field driving it.
 */
@Composable
internal fun ClearingOverlay(stage: ClearStage, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    when (stage) {
        ClearStage.Idle -> Unit

        ClearStage.Running -> LoadingOverlay(
            modifier = modifier,
            label = stringResource(R.string.hidden_notifications_clearing),
        )

        is ClearStage.Finished -> {
            LoadingOverlay(
                modifier = modifier,
                label = stringResource(R.string.hidden_notifications_clearing),
            )
            LaunchedEffect(stage) { onFinished() }
        }
    }
}
