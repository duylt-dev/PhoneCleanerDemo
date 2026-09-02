package com.pion.phonecleaner.feature.junk.junkreview.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.junk.JunkCategory
import com.pion.phonecleaner.domain.model.junk.JunkCategoryId
import com.pion.phonecleaner.feature.junk.R
import com.pion.phonecleaner.feature.junk.junkreview.CheckState
import com.pion.phonecleaner.feature.junk.junkreview.JunkReviewIntent

/**
 * One section header, drawn by `stickyHeader`.
 *
 * That one move deletes `zc.a` outright: 335 lines of `onDrawOver` measurement, an
 * `OnItemTouchListener` bolted on so taps hit the right row, six callbacks wired into the adapter's
 * private fields, a duplicate header layout that is a byte-for-byte copy of another, and one method
 * jadx could not decompile (`docs/screens/12-junk-cleaning.md` §4.3).
 *
 * `TriStateCheckbox` is where [CheckState.Partial] shows up — an indeterminate box, the value the
 * competitor has no way to express (Delta R2).
 *
 * It takes `onIntent` as-is and builds its own two lambdas here rather than in the `stickyHeader`
 * block, so nothing is allocated per item inside the list scope (`LLM.md` §8).
 */
@Composable
internal fun JunkCategoryHeader(
    category: JunkCategory,
    checkState: CheckState,
    expanded: Boolean,
    onIntent: (JunkReviewIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .clickable { onIntent(JunkReviewIntent.CategoryHeaderTapped(category.id)) }
                .padding(horizontal = ScreenGutter, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TriStateCheckbox(
                state = checkState.toToggleableState(),
                onClick = { onIntent(JunkReviewIntent.CategoryCheckTapped(category.id)) },
            )
            Column(Modifier.weight(1f).padding(horizontal = Spacing.sm)) {
                Text(
                    text = stringResource(category.id.titleRes()),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = rememberByteFormat().size(category.totalBytes).toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(
                    if (expanded) R.string.junk_category_collapse else R.string.junk_category_expand,
                ),
            )
        }
    }
}

private fun CheckState.toToggleableState(): ToggleableState = when (this) {
    CheckState.Checked -> ToggleableState.On
    CheckState.Unchecked -> ToggleableState.Off
    CheckState.Partial -> ToggleableState.Indeterminate
}

/**
 * The category title, resolved at RENDER time from a string resource.
 *
 * The competitor's engine hands its listener the English literals `"System Cache"`,
 * `"App Residuals"` and `"APK Files"` and the screen has nothing else to draw, in an app with a
 * 17-locale in-app language picker (`docs/screens/12-junk-cleaning.md` §1.2). The enum carries no
 * resource id either — that mapping lives here, above the domain layer (`LLM.md` §2).
 */
private fun JunkCategoryId.titleRes(): Int = when (this) {
    JunkCategoryId.SystemCache -> R.string.junk_category_system_cache
    JunkCategoryId.AppResidual -> R.string.junk_category_app_residual
    JunkCategoryId.ApkFiles -> R.string.junk_category_apk_files
}
