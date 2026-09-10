package com.pion.phonecleaner.feature.files.duplicates.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.pion.phonecleaner.core.ui.permission.needsAllFilesSettingsPage
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.file.FileKind
import com.pion.phonecleaner.feature.files.R
import com.pion.phonecleaner.feature.files.duplicates.DuplicatesIntent
import kotlinx.collections.immutable.ImmutableList

/**
 * Denial is a screen with a reason and a button, not `finish()`.
 *
 * The instructions are given **before** the user leaves. The competitor's answer to the same problem
 * is `FadeivatActivity` / `SimcenActivity`, two translucent overlays drawn over the system Settings
 * app from a background activity start — restricted since Android 10 and largely blocked on 14
 * (`LLM.md` §7.5). Those are deleted, not ported.
 */
@Composable
internal fun DuplicatesPermissionPanel(
    onIntent: (DuplicatesIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = ScreenGutter, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = stringResource(R.string.duplicates_permission_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            // Two bodies, because the grant has two shapes: a Settings page with a switch on API
            // 30+, a runtime dialog below it. One string would send half of all users looking for a
            // dialog that never appears. The predicate is the same one `DuplicatesRoute` picks its
            // launcher with, so the words and the button can never describe different flows.
            text = stringResource(
                if (needsAllFilesSettingsPage()) {
                    R.string.duplicates_permission_body_all_files
                } else {
                    R.string.duplicates_permission_body
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = { onIntent(DuplicatesIntent.GrantStoragePressed) }) {
            Text(stringResource(R.string.duplicates_permission_grant))
        }
    }
}

/**
 * The type filter. It exists because the corpus is no longer three media collections: with all-files
 * access a scan returns duplicate documents, archives and installers too, and a 900-row list with no
 * way to narrow it is a list nobody reads.
 *
 * **Only kinds actually present get a chip.** A filter that empties the list teaches the user that
 * the filter is broken, and the competitor's own tabs are fixed regardless of content.
 *
 * `horizontalScroll` rather than a wrapping `FlowRow`: six chips on a narrow phone would wrap to two
 * rows and push the list down by 48 dp on the screen where vertical space is the whole product.
 */
@Composable
internal fun DuplicateFilterChips(
    kinds: ImmutableList<FileKind>,
    selected: FileKind?,
    onIntent: (DuplicatesIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (kinds.size < 2) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = ScreenGutter, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onIntent(DuplicatesIntent.FilterSelected(null)) },
            label = { Text(stringResource(R.string.duplicates_filter_all)) },
        )
        kinds.forEach { kind ->
            FilterChip(
                selected = selected == kind,
                onClick = { onIntent(DuplicatesIntent.FilterSelected(kind)) },
                label = { Text(stringResource(kind.labelRes())) },
            )
        }
    }
}

/**
 * `FileKind` is a `:domain` enum and holds no resource id — `:domain` sees no `androidx.annotation`
 * (`LLM.md` §2). The mapping lives here, which is the same shape `ErrorMessages.kt` uses for
 * `AppError`.
 */
private fun FileKind.labelRes(): Int = when (this) {
    FileKind.Image -> R.string.duplicates_filter_image
    FileKind.Video -> R.string.duplicates_filter_video
    FileKind.Audio -> R.string.duplicates_filter_audio
    FileKind.Apk -> R.string.duplicates_filter_apk
    FileKind.Other -> R.string.duplicates_filter_other
}
