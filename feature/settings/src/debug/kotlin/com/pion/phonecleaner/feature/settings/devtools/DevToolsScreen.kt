package com.pion.phonecleaner.feature.settings.devtools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.settings.R

/**
 * `docs/screens/20-settings-language-and-push.md` §6.3: a `LazyColumn` of labelled field-and-action
 * rows. **Deliberately unstyled — this screen has no design and should not acquire one.**
 */
@Composable
internal fun DevToolsScreen(
    state: DevToolsState,
    onIntent: (DevToolsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PageHeader(
                title = stringResource(R.string.settings_devtools_title),
                onBack = { onIntent(DevToolsIntent.BackPressed) },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(ScreenGutter),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item(key = PushSectionKey, contentType = SectionType) {
                    PushSection(state, onIntent)
                }
            }
        }
    }
}

@Composable
private fun PushSection(state: DevToolsState, onIntent: (DevToolsIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = stringResource(R.string.settings_devtools_push_header),
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedTextField(
            value = state.payloadInput,
            onValueChange = { onIntent(DevToolsIntent.PayloadChanged(it)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = PayloadFieldHeight),
            label = { Text(stringResource(R.string.settings_devtools_push_hint)) },
            singleLine = false,
        )
        Button(onClick = { onIntent(DevToolsIntent.DeliverTapped) }) {
            Text(stringResource(R.string.settings_devtools_push_send))
        }
    }
}

private val PayloadFieldHeight = 120.dp
private const val PushSectionKey = "devtools.push"
private const val SectionType = "devtools.section"
