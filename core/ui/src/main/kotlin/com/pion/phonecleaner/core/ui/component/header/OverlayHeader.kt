package com.pion.phonecleaner.core.ui.component.header

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding

/**
 * The top bar for an **immersive** screen — one drawing over a Lottie, a scan animation or a
 * full-bleed gradient. Four screens do (system-architecture §4.7).
 *
 * Identical to [PageHeader] in every respect but one: **it applies the top inset itself**, because it
 * floats over content and nothing above it is in a position to (MVI §11). That single difference is
 * the reason there are two headers rather than one with a boolean — a boolean makes the wrong answer
 * as easy to type as the right one, and the wrong answer put five controls under a display cutout.
 *
 * The horizontal sides are taken too: held sideways, the cutout is on an edge no status bar reports.
 * The bottom is not — this header owns only its own strip.
 *
 * It draws no background, so the imagery behind it shows through. A screen whose backdrop needs a
 * different content colour provides `LocalContentColor` around it rather than passing a colour in;
 * MVI §11 keeps colour out of a header's signature.
 */
@Composable
fun OverlayHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
    bannerSlot: @Composable () -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .screenInsetsPadding(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) {
        HeaderRow(title, onBack, actionLabel, actionIcon, onAction)
        bannerSlot()
    }
}
