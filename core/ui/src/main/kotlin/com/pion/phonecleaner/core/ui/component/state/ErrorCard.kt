package com.pion.phonecleaner.core.ui.component.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.ui.R
import com.pion.phonecleaner.core.ui.error.userMessage
import com.pion.phonecleaner.core.ui.token.Spacing

/**
 * **This component replaces nothing.** No screen in the competitor corpus has an error state at all
 * (`r2-10-network.md:1758` D3, quoted by system-architecture §4.7), so every failure there is either
 * a silent no-op or a crash.
 *
 * It takes the `AppError` itself rather than a message, because the copy is resolved here by
 * `ErrorMessages.kt` at render time — the ViewModel must never build user-facing text (MVI §5).
 *
 * A screen renders it from `state.error`, and the matching effect carries the error too:
 * `ShowMessage(error)`, never a read of `state.error` in the collector, which runs one main-queue
 * turn before the matching `setState` renders (LLM.md §5 — a shipped bug elsewhere).
 */
@Composable
fun ErrorCard(
    error: AppError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(error.userMessage(), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}
