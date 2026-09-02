package com.pion.phonecleaner.feature.applock.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.dialog.AppDialog
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.feature.applock.R
import com.pion.phonecleaner.feature.applock.settings.component.SettingsDestructiveRow
import com.pion.phonecleaner.feature.applock.settings.component.SettingsNavigationRow
import com.pion.phonecleaner.feature.applock.settings.component.SettingsSwitchRow

/**
 * Three rows plus one destructive row — a `Column`, not a `LazyColumn`
 * (`docs/screens/16-app-lock.md` §4.3). Four always-visible items would pay for recycling that can
 * never happen.
 *
 * The header is `PageHeader` from `:core:ui`; the screen never writes its own (MVI §11).
 */
@Composable
internal fun AppLockSettingsScreen(
    state: AppLockSettingsState,
    onIntent: (AppLockSettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.screenInsetsPadding(),
            topBar = {
                PageHeader(
                    title = stringResource(R.string.app_lock_settings_title),
                    onBack = { onIntent(AppLockSettingsIntent.BackPressed) },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).padding(top = PageSpacing.headerToContent)) {
                SettingsSwitchRow(
                    icon = Icons.Filled.Lock,
                    title = stringResource(R.string.app_lock_enabled_title),
                    subtitle = stringResource(R.string.app_lock_enabled_subtitle),
                    checked = state.isAppLockEnabled,
                    stateDescription = state.isAppLockEnabled.onOffLabel(),
                    onCheckedChange = { onIntent(AppLockSettingsIntent.AppLockEnabledChanged(it)) },
                )
                SettingsSwitchRow(
                    icon = Icons.Filled.NewReleases,
                    title = stringResource(R.string.app_lock_new_app_title),
                    subtitle = stringResource(R.string.app_lock_new_app_subtitle),
                    checked = state.lockNewlyInstalled,
                    stateDescription = state.lockNewlyInstalled.onOffLabel(),
                    onCheckedChange = {
                        onIntent(AppLockSettingsIntent.LockNewlyInstalledChanged(it))
                    },
                )
                SettingsNavigationRow(
                    icon = Icons.Filled.Password,
                    title = stringResource(R.string.app_lock_change_pin_title),
                    subtitle = stringResource(R.string.app_lock_change_pin_subtitle),
                    onClick = { onIntent(AppLockSettingsIntent.ChangePasswordTapped) },
                )
                SettingsDestructiveRow(
                    icon = Icons.Filled.DeleteForever,
                    title = stringResource(R.string.app_lock_clear_title),
                    enabled = !state.isClearing,
                    onClick = { onIntent(AppLockSettingsIntent.ClearAppLockTapped) },
                )
            }
        }
    }
    when (state.dialog) {
        // It says what it takes with it BEFORE it runs (§4.5): the PIN and the locked-app list go
        // together, and once the PIN is hashed neither can be recovered.
        AppLockSettingsDialog.ClearAppLock -> AppDialog(
            onDismissRequest = { onIntent(AppLockSettingsIntent.ClearAppLockDismissed) },
            confirmLabel = stringResource(R.string.app_lock_clear_confirm),
            onConfirm = { onIntent(AppLockSettingsIntent.ClearAppLockConfirmed) },
            title = stringResource(R.string.app_lock_clear_title),
            body = stringResource(R.string.app_lock_clear_body),
            dismissLabel = stringResource(com.pion.phonecleaner.core.ui.R.string.action_cancel),
        )

        null -> Unit
    }
}

/** TalkBack reads the row's position, not just its label — the competitor's toggle reads neither. */
@Composable
private fun Boolean.onOffLabel(): String = stringResource(
    if (this) R.string.app_lock_state_on else R.string.app_lock_state_off,
)
