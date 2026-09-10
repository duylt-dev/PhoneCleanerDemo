package com.pion.phonecleaner.feature.files.zipfiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.component.state.EmptyState
import com.pion.phonecleaner.core.ui.component.state.ErrorCard
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.feature.files.R
import com.pion.phonecleaner.feature.files.component.ToolBanner
import com.pion.phonecleaner.feature.files.component.ToolOverlay

@Composable
internal fun ZipFilesScreen(
    state: ZipFilesState,
    onIntent: (ZipFilesIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().screenInsetsPadding()) {
            Column(Modifier.fillMaxSize()) {
                PageHeader(
                    title = stringResource(R.string.zip_files_title),
                    onBack = { onIntent(ZipFilesIntent.BackPressed) },
                )
                state.error?.let { error ->
                    ErrorCard(
                        error = error,
                        onRetry = { onIntent(ZipFilesIntent.CreateZipPressed) },
                        modifier = Modifier.padding(horizontal = ScreenGutter),
                    )
                }
                if (state.ignoredCount > 0) {
                    ToolBanner(
                        pluralStringResource(
                            R.plurals.zip_files_ignored,
                            state.ignoredCount,
                            state.ignoredCount,
                        ),
                    )
                }
                state.outcome?.let { outcome ->
                    ToolBanner(
                        stringResource(
                            R.string.zip_files_success,
                            outcome.fileName,
                            rememberByteFormat().size(outcome.outputBytes).toString(),
                        ),
                    )
                }
                if (state.pickedFiles.isEmpty()) {
                    EmptyState(message = stringResource(R.string.zip_files_empty))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = PageSpacing.listBottom),
                    ) {
                        items(state.pickedFiles, key = { it.uri }) { file ->
                            ListItem(
                                headlineContent = { Text(file.displayName) },
                                supportingContent = {
                                    val text = if (file.sizeBytes >= 0L) {
                                        rememberByteFormat().size(file.sizeBytes).toString()
                                    } else {
                                        stringResource(R.string.zip_files_unknown_size)
                                    }
                                    Text(text)
                                },
                            )
                        }
                    }
                }
                ZipActions(state, onIntent)
            }
            ToolOverlay(
                phase = state.phase,
                onCompletionFinished = { onIntent(ZipFilesIntent.CompletionAnimationFinished) },
                scanningLabel = stringResource(R.string.zip_files_creating),
                onCancel = { onIntent(ZipFilesIntent.BackPressed) },
            )
        }
    }
}

@Composable
private fun ZipActions(
    state: ZipFilesState,
    onIntent: (ZipFilesIntent) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenGutter, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.zip_files_limit),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(
                onClick = { onIntent(ZipFilesIntent.PickFilesPressed) },
                modifier = Modifier.weight(1f),
                enabled = !state.isBusy,
            ) {
                Text(stringResource(R.string.zip_files_pick))
            }
            Button(
                onClick = { onIntent(ZipFilesIntent.CreateZipPressed) },
                modifier = Modifier.weight(1f),
                enabled = state.canCreate,
            ) {
                Text(stringResource(R.string.zip_files_create))
            }
        }
    }
}
