package com.pion.phonecleaner.feature.notification.gate

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.feature.notification.R

/**
 * `docs/screens/17-notification-and-permissions.md` §1.3.
 *
 * The two competitor Activities — `ComplectivActivity` and `ScamotrudActivity` — are the same layout
 * differing in three values, so they are one `when` over [NotificationGateReason] and one private
 * [GateBody] with three parameters. Two Activities, two manifest entries and two layouts, deleted.
 *
 * `Resolving` paints only the toolbar. It is not a spinner: the wait is the frame it takes one `Flow`
 * to emit, and a spinner for one frame is a flash.
 */
@Composable
internal fun NotificationGateScreen(
    state: NotificationGateState,
    onIntent: (NotificationGateIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().screenInsetsPadding()) {
            PageHeader(
                title = stringResource(R.string.notification_cleaner_title),
                onBack = { onIntent(NotificationGateIntent.BackPressed) },
            )
            AnimatedContent(targetState = state.reason, label = "notification-gate") { reason ->
                when (reason) {
                    NotificationGateReason.Resolving -> Spacer(Modifier.fillMaxSize())

                    NotificationGateReason.ListenerAccessMissing -> GateBody(
                        title = stringResource(R.string.notification_gate_listener_missing_title),
                        body = stringResource(R.string.notification_gate_listener_missing_body),
                        cta = stringResource(R.string.notification_gate_listener_missing_cta),
                        onIntent = onIntent,
                    )

                    NotificationGateReason.HidingDisabled -> GateBody(
                        title = stringResource(R.string.notification_gate_hiding_disabled_title),
                        body = stringResource(R.string.notification_gate_hiding_disabled_body),
                        cta = stringResource(R.string.notification_gate_hiding_disabled_cta),
                        onIntent = onIntent,
                    )
                }
            }
        }
    }
}

/**
 * The whole delta between the two competitor walls.
 *
 * The body copy is the "in-app instruction **before** `startActivity`" of §1.5, and it is the only
 * instruction there is: `od.z.R()` posts a 1 500 ms `Runnable` on a bare `new Handler()` that draws an
 * Activity over the system Settings screen — a background activity start over another app's UI,
 * restricted from Android 10 and largely blocked on 14. No overlay, and no timer.
 */
@Composable
private fun GateBody(
    title: String,
    body: String,
    cta: String,
    onIntent: (NotificationGateIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ScreenGutter, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = { onIntent(NotificationGateIntent.PrimaryCtaTapped) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(cta)
        }
    }
}
