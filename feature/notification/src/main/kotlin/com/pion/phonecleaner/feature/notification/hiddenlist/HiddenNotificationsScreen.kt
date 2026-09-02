package com.pion.phonecleaner.feature.notification.hiddenlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.component.state.EmptyState
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.notification.R
import com.pion.phonecleaner.feature.notification.component.AppRowSkeletonList
import com.pion.phonecleaner.feature.notification.hiddenlist.component.ClearAllButton
import com.pion.phonecleaner.feature.notification.hiddenlist.component.ClearingOverlay
import com.pion.phonecleaner.feature.notification.hiddenlist.component.HiddenNotificationRow
import com.pion.phonecleaner.feature.notification.hiddenlist.component.HidingPausedBanner

/**
 * `docs/screens/17-notification-and-permissions.md` §3.3.
 *
 * One keyed `LazyColumn` over `state.notifications`; `onIntent` is passed down as-is (`LLM.md` §8).
 */
@Composable
internal fun HiddenNotificationsScreen(
    state: HiddenNotificationsState,
    onIntent: (HiddenNotificationsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                PageHeader(
                    title = stringResource(R.string.hidden_notifications_title),
                    onBack = { onIntent(HiddenNotificationsIntent.BackPressed) },
                    actionLabel = stringResource(R.string.hidden_notifications_settings),
                    actionIcon = Icons.Filled.Settings,
                    onAction = { onIntent(HiddenNotificationsIntent.SettingsTapped) },
                )
                if (!state.isMasterEnabled) {
                    HidingPausedBanner(
                        onEnable = { onIntent(HiddenNotificationsIntent.ResumeHidingTapped) },
                    )
                }
                // No `ErrorCard` here, and that is deliberate. Every failure this screen can reach —
                // a dismiss, a clear, a master-switch write — already arrives as a `ShowMessage`
                // Effect, and none of them has a retry that means anything: the list itself is
                // flow-driven and re-emits on its own. A card with a retry button that re-runs an
                // unrelated action is worse than no card (`docs/screens/17` §3.5).
                when {
                    state.isLoading -> AppRowSkeletonList(SkeletonRowCount, Modifier.weight(1f))
                    state.isEmpty -> Box(Modifier.weight(1f)) {
                        EmptyState(stringResource(R.string.hidden_notifications_empty))
                    }

                    else -> NotificationList(state, onIntent, Modifier.weight(1f))
                }
                ClearAllButton(
                    enabled = state.isClearAllEnabled,
                    onClick = { onIntent(HiddenNotificationsIntent.ClearAllTapped) },
                )
            }
            AnimatedVisibility(visible = state.isClearing) {
                ClearingOverlay(
                    stage = state.clearStage,
                    onFinished = { onIntent(HiddenNotificationsIntent.ClearAnimationFinished) },
                )
            }
        }
    }
}

@Composable
private fun NotificationList(
    state: HiddenNotificationsState,
    onIntent: (HiddenNotificationsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = Spacing.sm),
    ) {
        items(
            items = state.notifications,
            key = { it.key },
            contentType = { "notification" },
        ) { notification ->
            HiddenNotificationRow(
                notification = notification,
                onClick = {
                    onIntent(HiddenNotificationsIntent.NotificationTapped(notification.key))
                },
            )
        }
    }
}

private const val SkeletonRowCount = 5
