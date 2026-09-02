package com.pion.phonecleaner.feature.notification.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing

/**
 * The loading state for a list of app rows.
 *
 * It exists because the competitor has none: its screen is blank until the enumeration returns, and a
 * `PackageManager` throw escapes into the coroutine, so the blank is permanent
 * (`docs/screens/17-notification-and-permissions.md` §2.5).
 *
 * `clearAndSetSemantics {}` — placeholder blocks carry no information, and announcing six empty rows
 * to a screen reader is worse than announcing nothing.
 */
@Composable
internal fun AppRowSkeletonList(count: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        repeat(count) { SkeletonRow() }
    }
}

@Composable
private fun SkeletonRow() {
    val color = MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenGutter, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(Modifier.size(AppIconSize).clip(RoundedCornerShape(Spacing.xs)).background(color)) { }
        Column(
            modifier = Modifier
                .weight(1f)
                .height(SkeletonLineHeight)
                .clip(RoundedCornerShape(Spacing.xxs))
                .background(color),
        ) { }
    }
}

/** A position, not a gap: the height of one placeholder text line. */
private val SkeletonLineHeight = 16.dp
