package com.pion.phonecleaner.feature.notification.hidingsettings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.list.SectionHeader
import com.pion.phonecleaner.core.ui.component.state.EmptyState
import com.pion.phonecleaner.core.ui.component.state.ErrorCard
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.notification.R
import com.pion.phonecleaner.feature.notification.component.AppRowSkeletonList
import com.pion.phonecleaner.feature.notification.hidingsettings.component.MasterHidingCard
import com.pion.phonecleaner.feature.notification.hidingsettings.component.NotificationHidingRow

/**
 * `docs/screens/17-notification-and-permissions.md` §2.3.
 *
 * One keyed `LazyColumn`, so a toggle recomposes one row. `onIntent` is passed down as-is: a lambda
 * allocated inside `items {}` is a new instance every recomposition and defeats the skip for every row
 * (`LLM.md` §8).
 */
@Composable
internal fun NotificationHidingSettingsScreen(
    state: NotificationHidingSettingsState,
    onIntent: (NotificationHidingSettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PageHeader(
                title = stringResource(R.string.notification_hiding_settings_title),
                onBack = { onIntent(NotificationHidingSettingsIntent.BackPressed) },
            )
            MasterHidingCard(
                checked = state.isMasterEnabled,
                enabledCount = state.enabledCount,
                onCheckedChange = { onIntent(NotificationHidingSettingsIntent.MasterToggled(it)) },
                modifier = Modifier.padding(horizontal = ScreenGutter),
            )
            state.error?.let { error ->
                ErrorCard(
                    error = error,
                    onRetry = { onIntent(NotificationHidingSettingsIntent.RetryTapped) },
                    modifier = Modifier.padding(top = Spacing.md, start = ScreenGutter, end = ScreenGutter),
                )
            }
            when {
                state.isLoading -> AppRowSkeletonList(
                    count = SkeletonRowCount,
                    modifier = Modifier.padding(top = PageSpacing.headerToContent),
                )

                state.isEmpty -> EmptyState(stringResource(R.string.notification_hiding_empty))

                else -> AppList(state, onIntent, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AppList(
    state: NotificationHidingSettingsState,
    onIntent: (NotificationHidingSettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = Spacing.md, bottom = Spacing.xxl),
    ) {
        item(key = "apps-header", contentType = "header") {
            SectionHeader(stringResource(R.string.notification_hiding_apps_header))
        }
        items(
            items = state.apps,
            key = { it.packageName },
            contentType = { "app" },
        ) { app ->
            NotificationHidingRow(
                app = app,
                // Disabled, NOT hidden: the competitor sets the whole list to GONE (§2.5).
                enabled = state.isMasterEnabled,
                isBusy = app.packageName in state.togglingPackages,
                onCheckedChange = { enabled ->
                    onIntent(NotificationHidingSettingsIntent.AppToggled(app.packageName, enabled))
                },
            )
        }
    }
}

/** Enough placeholder rows to fill a phone screen once, and no more. */
private const val SkeletonRowCount = 6
