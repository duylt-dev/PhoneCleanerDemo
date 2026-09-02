package com.pion.phonecleaner.core.ui.component.header

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.R
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing

/**
 * The top bar for a **document** screen — a list, a settings page, a result page.
 *
 * Replaces six top-bar `<include>`s across 41 sites, which did one job at two title sizes with two
 * different back-icon ids, and carried six `visibility="gone"` decoy `TextView`s holding real
 * translated copy on 30 screens (system-architecture §4.7; `docs/screens/21` §4.6 C1). A slot that is
 * not filled does not exist in a composition, so the decoys have no port.
 *
 * **It does not apply the top inset.** The screen's outermost container does — in landscape the
 * display cutout moves to one side and the whole page has to move with it, not just its header
 * (MVI §11). Use [OverlayHeader] on an immersive screen, where nothing above the header is in a
 * position to apply it.
 *
 * **It takes no text style and no colour.** Weight and size belong to the type scale, chosen once
 * (MVI §11); MVI §11 permits colour parameters only for screens `LLM.md` §12 fixes to a palette, and
 * this project's §12 lists no such screen.
 *
 * @param actionLabel doubles as the action's content description when [actionIcon] is used.
 * @param bannerSlot `[AD GATE: banner slot]` — the layout reserves the space; the cluster that owns
 *   the screen decides whether anything fills it. The ad boundary itself is out of scope
 *   (`docs/screens/21` §4.3).
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
    bannerSlot: @Composable () -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        HeaderRow(title, onBack, actionLabel, actionIcon, onAction)
        bannerSlot()
    }
}

/**
 * The Material top-app-bar height. A **position**, not a gap, so it is off the 4 dp scale and named
 * (MVI §11): it is 56 dp so a screen can swap a `Scaffold(topBar = …)` for a `PageHeader` without the
 * content below it jumping.
 */
private val HeaderRowHeight = 56.dp

/** Shared by [PageHeader] and [OverlayHeader] so the two headers cannot drift apart. */
@Composable
internal fun HeaderRow(
    title: String,
    onBack: (() -> Unit)?,
    actionLabel: String?,
    actionIcon: ImageVector?,
    onAction: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(HeaderRowHeight).padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
            }
        } else {
            androidx.compose.foundation.layout.Spacer(Modifier.size(ScreenGutter))
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        when {
            onAction == null -> Unit
            actionIcon != null -> IconButton(onClick = onAction) {
                Icon(actionIcon, actionLabel)
            }

            actionLabel != null -> TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
