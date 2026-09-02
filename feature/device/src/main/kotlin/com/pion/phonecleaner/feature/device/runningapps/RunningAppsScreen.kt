package com.pion.phonecleaner.feature.device.runningapps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.component.list.SectionHeader
import com.pion.phonecleaner.core.ui.component.state.EmptyState
import com.pion.phonecleaner.core.ui.component.state.ErrorCard
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.feature.device.R
import com.pion.phonecleaner.feature.device.component.MemoryRing
import com.pion.phonecleaner.feature.device.runningapps.component.ForceStopInstructionsSheet
import com.pion.phonecleaner.feature.device.runningapps.component.RunningAppRow
import com.pion.phonecleaner.feature.device.runningapps.component.UsageAccessCard

/**
 * `runningapps` (`docs/screens/18-device-battery-and-apps.md` §6.2). Stateless:
 * `(state, onIntent) -> Unit`, never the ViewModel (MVI §4).
 *
 * `key = { it.packageName }` is what makes this screen work at all: the competitor calls
 * `notifyDataSetChanged()` on **every** resume, which rebinds every visible row, restarts every icon
 * load and loses scroll anchoring — every time the user returns from Settings.
 *
 * The *Done* button is pinned below the list rather than scrolling with it, and the competitor's
 * 270 dp transparent spacer is deleted: spacing comes from the token scale.
 *
 * `onIntent` is passed down as-is; per-item lambdas are `remember`ed inside the row, never allocated
 * in `items {}` (`LLM.md` §8).
 */
@Composable
internal fun RunningAppsScreen(
    state: RunningAppsState,
    onIntent: (RunningAppsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().screenInsetsPadding()) {
            PageHeader(
                title = stringResource(R.string.running_apps_title),
                onBack = { onIntent(RunningAppsIntent.BackPressed) },
            )
            RunningAppsList(
                state = state,
                onIntent = onIntent,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { onIntent(RunningAppsIntent.SkipTapped) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenGutter, vertical = Spacing.md),
            ) {
                Text(stringResource(R.string.running_apps_skip))
            }
        }
        state.instructionsFor?.let { packageName ->
            ForceStopInstructionsSheet(
                packageName = packageName,
                onOpenSettings = { onIntent(RunningAppsIntent.InstructionsOpenSettingsTapped) },
                onDismiss = { onIntent(RunningAppsIntent.InstructionsDismissed) },
            )
        }
    }
}

@Composable
private fun RunningAppsList(
    state: RunningAppsState,
    onIntent: (RunningAppsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onGrantUsageAccess = remember(onIntent) {
        { onIntent(RunningAppsIntent.UsageAccessGrantTapped) }
    }
    val onDismissUsageAccess = remember(onIntent) {
        { onIntent(RunningAppsIntent.UsageAccessDismissed) }
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = Spacing.md, bottom = Spacing.xxl),
    ) {
        item(key = "ring", contentType = "ring") {
            // Labelled "Device memory", and it does NOT move after a Stop: a number that falls when
            // the user taps something is a claim about what the tap did (§6.5).
            MemoryRing(
                memory = state.memory,
                modifier = Modifier.padding(bottom = Spacing.lg),
            )
        }
        if (state.error != null) {
            item(key = "error", contentType = "error") {
                ErrorCard(
                    error = state.error,
                    onRetry = { onIntent(RunningAppsIntent.RetryTapped) },
                    modifier = Modifier.padding(horizontal = ScreenGutter, vertical = Spacing.sm),
                )
            }
        }
        if (state.isUsageAccessVisible) {
            item(key = "usage-access", contentType = "usage-access") {
                UsageAccessCard(
                    onGrant = onGrantUsageAccess,
                    onDismiss = onDismissUsageAccess,
                    modifier = Modifier.padding(horizontal = ScreenGutter, vertical = Spacing.sm),
                )
            }
        }
        item(key = "section", contentType = "section") {
            SectionHeader(title = stringResource(R.string.running_apps_section))
        }
        if (state.isEmpty) {
            // The competitor has no empty state at all: a header, a ring and an empty box (§6.2).
            item(key = "empty", contentType = "empty") {
                EmptyState(message = stringResource(R.string.running_apps_empty))
            }
        }
        items(
            count = state.apps.size,
            key = { index -> state.apps[index].packageName },
            contentType = { "app" },
        ) { index ->
            RunningAppRow(app = state.apps[index], onIntent = onIntent)
        }
    }
}
