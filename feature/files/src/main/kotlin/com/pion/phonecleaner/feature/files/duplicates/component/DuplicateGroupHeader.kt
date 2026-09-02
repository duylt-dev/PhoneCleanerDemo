package com.pion.phonecleaner.feature.files.duplicates.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import com.pion.phonecleaner.core.ui.component.list.SectionHeader
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.files.R

/**
 * One group's sticky header (`docs/screens/14-file-tools-and-app-manager.md` §2.3).
 *
 * It replaces `td.a`, a `RecyclerView.ItemDecoration` that adds a 6 dp gap — a class **absent from
 * `app-src`**, so the gap is a reconstruction and the sticky header is a design choice, not a port.
 *
 * The count goes through a `<plurals>`: the competitor appends an English "s" in all 17 locales.
 */
@Composable
internal fun DuplicateGroupHeader(
    copies: Int,
    sizeBytes: Long,
    modifier: Modifier = Modifier,
) {
    val bytes = rememberByteFormat()
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = Spacing.xxs,
    ) {
        SectionHeader(
            title = pluralStringResource(
                R.plurals.duplicates_group_header,
                copies,
                copies,
                bytes.size(sizeBytes).toString(),
            ),
            modifier = Modifier.padding(horizontal = ScreenGutter),
        )
    }
}
