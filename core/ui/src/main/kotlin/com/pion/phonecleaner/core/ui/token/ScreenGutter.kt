package com.pion.phonecleaner.core.ui.token

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * The page edge — one value for the whole app.
 *
 * MVI §11 lists `ScreenGutter + 4.dp` as the forbidden form, not just a raw literal: a page that is
 * *nearly* aligned with its neighbour is worse than one that is obviously different, because nobody
 * can see which of the two is wrong.
 */
val ScreenGutter: Dp = Spacing.lg

/**
 * The same value as a `LazyColumn`/`LazyVerticalGrid` `contentPadding`.
 *
 * Horizontal only. Vertical list padding is a per-screen decision — a list under a `SelectionBar`
 * needs [PageSpacing.listBottom] and one without a bar does not — so it is not baked in here.
 */
val ScreenGutterPadding: PaddingValues = PaddingValues(horizontal = ScreenGutter)

/** `Modifier.screenGutter()` for content that is not a lazy list. */
fun Modifier.screenGutter(): Modifier = padding(horizontal = ScreenGutter)
