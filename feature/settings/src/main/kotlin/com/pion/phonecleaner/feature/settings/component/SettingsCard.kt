package com.pion.phonecleaner.feature.settings.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing

/**
 * The grouped container both `settings` and `about` put their rows in
 * (`docs/screens/20-settings-language-and-push.md` §1.3, §3.3).
 *
 * It lives in the cluster's own `component/` package and not in `:core:ui`: two screens of **one**
 * cluster share it, which `LLM.md` §4 puts here exactly. Promoting it would be the only legal way to
 * share it with another cluster, and no other cluster has these rows.
 *
 * The competitor repeats the same six-view `RelativeLayout` in `oxyidenc` and `bursmitiv`, and
 * duplicates the identity block four times across two XML files.
 */
@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        content = content,
    )
}

/**
 * The hairline between two rows. A **position**, not a gap, so it is off the 4 dp spacing scale and
 * named here rather than written as a literal at four call sites (MVI §11).
 */
@Composable
internal fun SettingsRowDivider() {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        thickness = RowDividerThickness,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

private val RowDividerThickness = 0.5.dp

/**
 * The gutter the two cards sit in, and the gap below the last one — **tokens, not literals**.
 *
 * The appendix writes `padding(horizontal = 20.dp, bottom = 32.dp)`; 20 dp is not on `:core:ui`'s
 * spacing scale and MVI §11 forbids a raw dimension at a call site, so the gutter is the project's
 * own [ScreenGutter]. The bottom gap is [Spacing.xxl] and **not** the competitor's 128 dp: that
 * margin was making room for an ad slot these screens do not have
 * (`docs/screens/20-settings-language-and-push.md` §1.3).
 */
internal object SettingsCardSpacing {
    val horizontal = ScreenGutter

    val bottom = Spacing.xxl
}
