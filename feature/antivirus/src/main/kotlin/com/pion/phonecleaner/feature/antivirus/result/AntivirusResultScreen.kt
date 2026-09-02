package com.pion.phonecleaner.feature.antivirus.result

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.state.EmptyState
import com.pion.phonecleaner.core.ui.component.state.ErrorCard
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.feature.antivirus.R
import com.pion.phonecleaner.feature.antivirus.component.PoweredByTrustlook
import com.pion.phonecleaner.feature.antivirus.result.component.FindingRow
import com.pion.phonecleaner.feature.antivirus.result.component.RemoveFindingDialog
import com.pion.phonecleaner.feature.antivirus.result.component.ResultHeadline

/**
 * Stateless: `(state, onIntent) -> Unit`, never the ViewModel (MVI §4).
 *
 * **The empty branch is two states, not one.** "Nothing was flagged" is a statement about a check
 * that ran; "no check has finished yet" is a statement about one that never did — and in this build
 * it is the branch the user actually reaches, because the vendor SDK has no Maven coordinate and
 * `TrustLookClient` raises `ScanFailure.SdkUnavailable` before anything is written. Collapsing the
 * two would let a component that never ran read as a clean bill of health, which is exactly the
 * claim the wording ban exists to prevent.
 *
 * The attribution sits below the content in every branch — a licence requirement, not decoration.
 */
@Composable
internal fun AntivirusResultScreen(
    state: AntivirusResultState,
    onIntent: (AntivirusResultIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pending = state.pendingRemoval
    val error = state.error
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().screenInsetsPadding()) {
            PageHeader(
                title = stringResource(R.string.antivirus_title),
                onBack = { onIntent(AntivirusResultIntent.BackPressed) },
            )
            Box(Modifier.weight(1f)) {
                when {
                    error != null -> ErrorCard(
                        error = error,
                        // Re-reads the record. The read failed; the check itself is a different
                        // action, and offering it here would restart a cloud round trip nobody
                        // asked for.
                        onRetry = { onIntent(AntivirusResultIntent.ScreenStarted) },
                        modifier = Modifier
                            .padding(horizontal = ScreenGutter)
                            .padding(top = PageSpacing.headerToContent),
                    )

                    state.hasNeverScanned -> EmptyStateWithRescan(
                        message = stringResource(R.string.antivirus_result_never_scanned),
                        onIntent = onIntent,
                    )

                    state.isEmpty -> EmptyStateWithRescan(
                        message = stringResource(R.string.antivirus_result_none),
                        onIntent = onIntent,
                    )

                    else -> FindingList(state, onIntent)
                }
            }
            if (!state.isLoading && state.findings.isNotEmpty()) RescanBar(onIntent)
            PoweredByTrustlook(Modifier.align(Alignment.CenterHorizontally))
        }
        if (pending != null) RemoveFindingDialog(pending, onIntent)
    }
}

@Composable
private fun FindingList(
    state: AntivirusResultState,
    onIntent: (AntivirusResultIntent) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.lg),
    ) {
        item(key = HeadlineKey, contentType = HeadlineContentType) {
            ResultHeadline(
                count = state.findingCount,
                level = state.headlineLevel,
                scannedAtEpochMs = state.scannedAtEpochMs,
            )
        }
        items(
            items = state.findings,
            // The MD5, never the index and never the package name: a loose `.apk` and a test-file
            // hit both carry an empty package name, so the package name is not unique (§2.3).
            key = { it.md5 },
            contentType = { RowContentType },
        ) { finding ->
            // `onIntent` is passed down AS IS. A per-row lambda allocated inside `items {}` is a new
            // instance every recomposition and defeats the skip for every row (`LLM.md` §8).
            FindingRow(
                finding = finding,
                isRemoving = finding.md5 in state.removingMd5s,
                onIntent = onIntent,
            )
        }
    }
}

/** A dead end otherwise: the competitor's result screen has no way back to a fresh check (§2.5). */
@Composable
private fun EmptyStateWithRescan(
    message: String,
    onIntent: (AntivirusResultIntent) -> Unit,
) {
    EmptyState(
        message = message,
        action = {
            Button(onClick = { onIntent(AntivirusResultIntent.RescanPressed) }) {
                Text(stringResource(R.string.antivirus_result_rescan))
            }
        },
    )
}

@Composable
private fun RescanBar(onIntent: (AntivirusResultIntent) -> Unit) {
    Button(
        onClick = { onIntent(AntivirusResultIntent.RescanPressed) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenGutter, vertical = Spacing.md),
    ) {
        Text(stringResource(R.string.antivirus_result_rescan))
    }
}

private const val HeadlineKey = "headline"
private const val HeadlineContentType = "headline"
private const val RowContentType = "finding"
