package com.pion.phonecleaner.feature.antivirus.result.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pion.phonecleaner.core.ui.icon.AppIconLoader
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.security.ThreatVerdict
import com.pion.phonecleaner.feature.antivirus.R
import com.pion.phonecleaner.feature.antivirus.result.AntivirusResultIntent
import org.koin.compose.koinInject

/**
 * One finding (`docs/screens/15-antivirus.md` §2.3).
 *
 * **The icon is the real application icon**, resolved from the package name at draw time by
 * `AppIconLoader` (`coreUiModule`), `koinInject()`ed into the composable and never into a ViewModel.
 * The competitor gives every row one of two static drawables from a launcher-list probe, so every
 * row looks the same. A loose `.apk` has no package to resolve and falls back to a file icon.
 *
 * **The family name and the category are rendered.** They are already parsed and already in memory,
 * and the competitor renders neither — so its user is shown a level with no reason (§2.5).
 *
 * [isRemoving] is `finding.md5 in state.removingMd5s`: a per-row guard, not a screen-wide freeze and
 * not a process-global click debounce.
 *
 * `onIntent` is taken as-is and the tap emits the **event**, not the mutation.
 */
@Composable
internal fun FindingRow(
    finding: ThreatVerdict,
    isRemoving: Boolean,
    onIntent: (AntivirusResultIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !isRemoving) {
                onIntent(AntivirusResultIntent.FindingTapped(finding.md5))
            }
            .padding(horizontal = ScreenGutter, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FindingIcon(finding)
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(
                text = finding.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (finding.familyName.isNotBlank()) {
                Text(
                    text = finding.familyName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                RiskBadge(finding.risk)
                CategoryChip(finding.category)
            }
        }
        if (isRemoving) {
            CircularProgressIndicator(Modifier.size(BusyIndicatorSize))
        } else {
            // Additive, not a port: the competitor offers exactly one action per row, so a false
            // positive on an app the user trusts is unmanageable and returns on every check (§2.5).
            TextButton(
                onClick = { onIntent(AntivirusResultIntent.IgnorePressed(finding.md5)) },
            ) {
                Text(stringResource(R.string.antivirus_result_ignore))
            }
        }
    }
}

@Composable
private fun FindingIcon(finding: ThreatVerdict) {
    if (finding.isInstalledApp) {
        val icons = koinInject<AppIconLoader>()
        AsyncImage(
            model = icons.request(finding.packageName),
            imageLoader = icons.imageLoader,
            contentDescription = null, // decorative: the label beside it names the app
            modifier = Modifier.size(RowIconSize),
        )
    } else {
        Icon(
            imageVector = Icons.Filled.Android,
            contentDescription = null,
            modifier = Modifier.size(RowIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** **Positions**, not gaps (MVI §11): 40 dp is a legible launcher icon in a list row. */
private val RowIconSize = 40.dp
private val BusyIndicatorSize = 24.dp
