package com.pion.phonecleaner.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pion.phonecleaner.core.ui.component.list.SectionHeader
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenGutter
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.feature.home.component.CleanHeroCard
import com.pion.phonecleaner.feature.home.component.CompanionFooter
import com.pion.phonecleaner.feature.home.component.FeatureGrid
import com.pion.phonecleaner.feature.home.component.HomeHeader
import com.pion.phonecleaner.feature.home.component.NotificationBanner

/**
 * Stateless: `state` and `onIntent`, never the ViewModel, and it derives no business truth.
 *
 * One `LazyColumn` replaces a `NestedScrollView` over 983 lines of XML with eight `<include>`s and
 * eleven hand-repeated tile blocks (delta 20). `onIntent` is passed down as-is — never wrapped per
 * item, which would defeat skipping for every tile on every emission (`LLM.md` §8).
 *
 * `LazyListState` is hoisted here because it is a fact about the arrangement, not about any control.
 */
@Composable
internal fun HomeScreen(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().screenInsetsPadding(),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(bottom = PageSpacing.listBottom),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(key = "header", contentType = "header") {
            HomeHeader(showWarning = state.showPermissionWarning, onIntent = onIntent)
        }
        item(key = "hero", contentType = "hero") {
            CleanHeroCard(
                storage = state.storage,
                junkPill = state.junkPill,
                needsSecurityScanToday = state.needsSecurityScanToday,
                isBusy = state.isBusy,
                isComingSoon = state.isHeroComingSoon,
                onIntent = onIntent,
            )
        }
        item(key = "banner", contentType = "banner") {
            // The slot is always present so its key is stable; only the card animates in and out.
            AnimatedVisibility(visible = state.showNotificationBanner) {
                NotificationBanner(onIntent = onIntent)
            }
        }
        state.sections.forEachIndexed { index, section ->
            val titleRes = section.titleRes
            if (titleRes != null) {
                item(key = "section-title-$index", contentType = "sectionTitle") {
                    SectionHeader(titleRes = titleRes, modifier = Modifier.screenGutter())
                }
            }
            item(key = "section-$index", contentType = "section") {
                FeatureGrid(
                    section = section,
                    downloadBytesPerSecond = state.downloadBytesPerSecond,
                    onIntent = onIntent,
                )
            }
        }
        item(key = "footer", contentType = "footer") {
            CompanionFooter(
                daysInstalled = state.daysInstalled,
                lifetimeSavedBytes = state.lifetimeSavedBytes,
            )
        }
    }
}
