package com.pion.phonecleaner.feature.settings.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.domain.model.settings.LegalDocument
import com.pion.phonecleaner.feature.settings.R
import com.pion.phonecleaner.feature.settings.component.AppIdentityBlock
import com.pion.phonecleaner.feature.settings.component.appVersionLabel
import com.pion.phonecleaner.feature.settings.component.SettingsCard
import com.pion.phonecleaner.feature.settings.component.SettingsCardSpacing
import com.pion.phonecleaner.feature.settings.component.SettingsRow
import com.pion.phonecleaner.feature.settings.component.SettingsRowDivider

/**
 * `docs/screens/20-settings-language-and-push.md` §3.3. Stateless: `(state, onIntent) -> Unit`.
 *
 * `onIntent` is passed to the rows as-is where it can be; the three rows below take distinct
 * arguments, so each lambda is allocated once per composition of a four-row column — not per item of
 * a list, which is the allocation `LLM.md` §8 forbids.
 */
@Composable
internal fun AboutScreen(
    state: AboutState,
    onIntent: (AboutIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PageHeader(
                title = stringResource(R.string.settings_about_title),
                onBack = { onIntent(AboutIntent.BackPressed) },
            )
            Spacer(Modifier.weight(1f))
            AppIdentityBlock(
                appName = state.appName,
                version = appVersionLabel(state.versionName, state.versionCode),
            )
            Spacer(Modifier.weight(1f))
            SettingsCard(
                Modifier.padding(
                    horizontal = SettingsCardSpacing.horizontal,
                    vertical = SettingsCardSpacing.bottom,
                ),
            ) {
                SettingsRow(
                    label = stringResource(R.string.settings_about_terms_of_service),
                    onClick = {
                        onIntent(AboutIntent.LegalDocumentTapped(LegalDocument.TermsOfService))
                    },
                )
                SettingsRowDivider()
                SettingsRow(
                    label = stringResource(R.string.settings_about_privacy_policy),
                    onClick = {
                        onIntent(AboutIntent.LegalDocumentTapped(LegalDocument.PrivacyPolicy))
                    },
                )
                if (state.isDeveloperSectionVisible) {
                    SettingsRowDivider()
                    SettingsRow(
                        label = stringResource(R.string.settings_about_developer_tools),
                        onClick = { onIntent(AboutIntent.DeveloperRowTapped) },
                    )
                }
            }
        }
    }
}
