package com.pion.phonecleaner.feature.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The competitor's `equialt` / `entirais` red dot, drawn once instead of three times.
 *
 * It carries a [description] because a bare coloured circle is invisible to a screen reader, and the
 * dot is the *only* signal the competitor gives that a safety check has not run today — a signal
 * with no accessible name is a signal that is not there for the user who most needs it.
 */
@Composable
internal fun AttentionDot(
    description: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(DotSize)
            .semantics { contentDescription = description }
            .background(MaterialTheme.colorScheme.error, CircleShape),
    )
}

/** A **position**, not a gap (MVI §11): the diameter at which a dot reads as a marker, not as dirt. */
private val DotSize = 8.dp
