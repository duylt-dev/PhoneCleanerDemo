package com.pion.phonecleaner.core.ui.component.tile

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.token.Spacing

/**
 * A read-only fact: icon, label, value. Replaces `rentere`, included six times, all inside battery
 * info (system-architecture §4.7).
 *
 * [icon] is nullable — the contract in §4.7 writes `icon: Painter`, but a device-status row without a
 * glyph would otherwise have to pass a blank painter, which costs a draw and a content description
 * that says nothing. It stays first and positional so the call sites §4.7 describes read unchanged.
 *
 * [value] is a `String` because it is already formatted by the caller through `rememberByteFormat()`
 * or `stringResource` — this row does no formatting, so it cannot hold a stale locale.
 */
@Composable
fun LabelValueRow(
    icon: Painter?,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null, // decorative: `label` is the accessible name
                modifier = Modifier.size(RowIconSize).padding(end = Spacing.xs),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = label,
            modifier = Modifier.weight(1f).padding(start = Spacing.md),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}

/** A **position**, not a gap (MVI §11): matches the 24 dp Material list-icon size. */
private val RowIconSize = 24.dp
