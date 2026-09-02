package com.pion.phonecleaner.feature.junk.junkreview

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.state.EmptyState
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.junk.R
import com.pion.phonecleaner.feature.junk.junkreview.component.CollapsingJunkTotal
import com.pion.phonecleaner.feature.junk.junkreview.component.JunkCategoryHeader
import com.pion.phonecleaner.feature.junk.junkreview.component.JunkItemRow

/**
 * `equitio.xml` is a `CoordinatorLayout` + `AppBarLayout` + a `ConcatAdapter` of three adapters + a
 * custom `ItemDecoration`, about 1 200 lines of adapters in all. It becomes one `LazyColumn`
 * (`docs/screens/12-junk-cleaning.md` §4.3).
 *
 * Keys are `category.id` for headers and `item.path` for rows, so ticking one row animates one row —
 * and no row carries its own position, which is the crash class `java/ud/c.java:89,136` creates by
 * stashing the position in a `View` tag and casting it back (`LLM.md` §8).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JunkReviewScreen(
    state: JunkReviewState,
    onIntent: (JunkReviewIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Surface(modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = {
                        CollapsingJunkTotal(
                            totalBytes = state.totalBytes,
                            collapsedFraction = scrollBehavior.state.collapsedFraction,
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            bottomBar = { CleanCta(state, onIntent) },
        ) { padding ->
            if (state.isEmpty) {
                // "Nothing was found" — a statement about the scan. Not "Very Clean", which is a
                // compliment about the device (§8.3).
                EmptyState(
                    message = stringResource(R.string.junk_review_empty),
                    modifier = Modifier.padding(padding),
                )
            } else {
                JunkList(state, onIntent, padding)
            }
        }
    }
}

@Composable
private fun JunkList(
    state: JunkReviewState,
    onIntent: (JunkReviewIntent) -> Unit,
    padding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        // Replaces `yc.h`, a whole adapter whose only job is to emit one 400 dp spacer View.
        contentPadding = PaddingValues(bottom = PageSpacing.listBottom),
    ) {
        state.categories.forEach { category ->
            stickyHeader(key = category.id, contentType = HeaderContentType) {
                JunkCategoryHeader(
                    category = category,
                    checkState = state.checkStates[category.id] ?: CheckState.Unchecked,
                    expanded = category.id in state.expandedCategories,
                    onIntent = onIntent,
                )
            }
            if (category.id in state.expandedCategories) {
                items(
                    items = category.items,
                    key = { it.path },
                    contentType = { RowContentType },
                ) { item ->
                    // `onIntent` is passed down AS IS. A per-row lambda allocated inside `items {}`
                    // is a new instance every recomposition and defeats the skip for every row
                    // (`LLM.md` §8).
                    JunkItemRow(
                        item = item,
                        selected = item.path in state.selectedPaths,
                        onIntent = onIntent,
                    )
                }
            }
        }
    }
}

/**
 * The CTA carries the selected size through a `%1$s` placeholder.
 *
 * The competitor builds the same label by string concatenation with full-width CJK parentheses
 * `（ ）` in **every** locale, which is wrong punctuation in 16 of its 17 shipped ones
 * (`VibrnancActivity.java:466`, Delta R11).
 */
@Composable
private fun CleanCta(state: JunkReviewState, onIntent: (JunkReviewIntent) -> Unit) {
    Button(
        onClick = { onIntent(JunkReviewIntent.CleanTapped) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenGutter, vertical = Spacing.md),
        enabled = state.canClean,
    ) {
        Text(
            text = if (state.canClean) {
                stringResource(
                    R.string.junk_review_clean_cta,
                    rememberByteFormat().size(state.selectedBytes).toString(),
                )
            } else {
                // `isEmpty` and "nothing selected" look identical in the competitor (Delta R7).
                stringResource(R.string.junk_review_nothing_selected)
            },
        )
    }
}

private const val HeaderContentType = "junkCategoryHeader"
private const val RowContentType = "junkItemRow"
