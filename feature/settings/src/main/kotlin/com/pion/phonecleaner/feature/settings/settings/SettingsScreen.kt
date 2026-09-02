package com.pion.phonecleaner.feature.settings.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.feature.settings.R
import com.pion.phonecleaner.feature.settings.component.AppIdentityBlock
import com.pion.phonecleaner.feature.settings.component.appVersionLabel
import com.pion.phonecleaner.feature.settings.component.SettingsCard
import com.pion.phonecleaner.feature.settings.component.SettingsCardSpacing
import com.pion.phonecleaner.feature.settings.component.SettingsRow
import com.pion.phonecleaner.feature.settings.component.SettingsRowDivider
import com.pion.phonecleaner.feature.settings.component.SettingsSwitchRow
import com.pion.phonecleaner.feature.settings.language.endonymRes

/**
 * `docs/screens/20-settings-language-and-push.md` §1.3. **No `LazyColumn`: four rows.**
 *
 * The `0dp × 0dp` `gone` "Permission manager" `TextView` becomes a real row: the screen it names is
 * otherwise reachable only from the home toolbar, so the competitor's layout advertises an entry
 * point that does not exist (§1.4 delta 3).
 */
@Composable
internal fun SettingsScreen(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PageHeader(
                title = stringResource(R.string.settings_title),
                onBack = { onIntent(SettingsIntent.BackPressed) },
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.weight(1f))
                AppIdentityBlock(
                    appName = state.appName,
                    version = appVersionLabel(state.versionName, state.versionCode),
                )
                Spacer(Modifier.weight(1f))
                SettingsRows(state, onIntent)
            }
        }
    }
}

@Composable
private fun SettingsRows(state: SettingsState, onIntent: (SettingsIntent) -> Unit) {
    SettingsCard(
        Modifier.padding(
            horizontal = SettingsCardSpacing.horizontal,
            vertical = SettingsCardSpacing.bottom,
        ),
    ) {
        SettingsRow(
            label = stringResource(R.string.settings_row_language),
            onClick = { onIntent(SettingsIntent.LanguageRowTapped) },
            trailing = state.languageLabel(),
        )
        SettingsRowDivider()
        SettingsRow(
            label = stringResource(R.string.settings_row_permission_centre),
            onClick = { onIntent(SettingsIntent.PermissionCentreRowTapped) },
            // `takeIf { it > 0 }`: a badge reading "0" claims something is wrong when nothing is.
            badge = state.missingPermissionCount.takeIf { it > 0 }?.toString(),
        )
        SettingsRowDivider()
        ResidentWidgetRow(state, onIntent)
        SettingsRowDivider()
        SettingsRow(
            label = stringResource(R.string.settings_row_about),
            onClick = { onIntent(SettingsIntent.AboutRowTapped) },
        )
    }
}

/**
 * The opt-in switch for the resident status-bar widget.
 *
 * UNKNOWN — **its placement.** PENDING OWNER DECISION 4 settles that the widget ships opt-in and
 * default OFF and defers the widget itself; no source designs where the switch sits.
 * `docs/screens/20-settings-language-and-push.md` §1.3 draws it as the third of four rows on this
 * card, between Permissions and About, and that is what is built here. Looked for a placement rule in
 * that appendix §1 and §7, in `docs/screens/21-shared-models-and-ui.md`, and in
 * `docs/reverse-engineering/03-out-of-app-behaviour.md`; none states one, because in the competitor
 * the switch does not exist at all. Moving it — to its own card, or to a notifications group once one
 * exists — is a change to this composable and nothing else.
 */
@Composable
private fun ResidentWidgetRow(state: SettingsState, onIntent: (SettingsIntent) -> Unit) {
    SettingsSwitchRow(
        label = stringResource(R.string.settings_row_resident_widget),
        checked = state.isResidentWidgetEnabled,
        onCheckedChange = { onIntent(SettingsIntent.ResidentWidgetToggled(it)) },
        supporting = stringResource(R.string.settings_row_resident_widget_supporting),
    )
}

/**
 * The current language's endonym, or the "System default" label when the app follows the system.
 *
 * Resolved at **render** time. The competitor resolves its feature names to `String`s at object
 * initialisation, so in a 17-locale app every one of them stays stale until the process restarts
 * (`LLM.md` §2).
 */
@Composable
private fun SettingsState.languageLabel(): String {
    val language = currentLanguage ?: return stringResource(R.string.settings_language_follow_system)
    return endonymRes(language.tag)?.let { stringResource(it) } ?: language.tag
}
