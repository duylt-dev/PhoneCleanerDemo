package com.pion.phonecleaner.core.ui.component.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing

/**
 * One empty state, replacing two includes used thirteen times between them and two duplicate string
 * ids with identical text (system-architecture §4.7; `docs/screens/21` §4.6 C6).
 *
 * The competitor's version sizes its text in **`dp`**, so it ignores the user's font-scale setting on
 * the one screen state a low-vision user is most likely to be reading (§4.6 C2). Here the size comes
 * from the type scale and is therefore in `sp`.
 *
 * [message] is a `String`, not a `@StringRes Int`: the caller resolves it with `stringResource`, which
 * is what keeps it correct after an in-app locale change.
 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = ScreenGutter, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null, // decorative: `message` already says it
                modifier = Modifier.size(EmptyStateIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

/**
 * A **position**, not a gap, so it is off the 4 dp scale and named (MVI §11): 72 dp is the size at
 * which a single centred glyph reads as illustration rather than as a control the user should press.
 */
private val EmptyStateIconSize = 72.dp
