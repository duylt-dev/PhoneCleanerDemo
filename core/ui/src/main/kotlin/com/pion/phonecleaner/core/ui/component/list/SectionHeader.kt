package com.pion.phonecleaner.core.ui.component.list

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.token.Spacing

/**
 * The label above a group of rows. Home keys its sections on the header's own string id
 * (`docs/screens/11-home.md:253`), the settings permission centre uses the same component
 * (`docs/screens/20:612`), and `MediaList` uses the `String` overload for its month headers.
 *
 * @param trailing a per-section action — a month's own "select all", say. It is a slot rather than a
 *   parameter list so a section that needs nothing allocates nothing.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        trailing()
    }
}

/** The `@StringRes` form, resolved here rather than by the caller so the id can double as the key. */
@Composable
fun SectionHeader(
    @StringRes titleRes: Int,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) = SectionHeader(stringResource(titleRes), modifier, trailing)
