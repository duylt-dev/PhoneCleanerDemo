package com.pion.phonecleaner.core.ui.token

import androidx.compose.ui.unit.dp

/**
 * Every gap in the app, on a 4 dp scale with two deliberate off-scale members at the small end.
 *
 * MVI §11: `Spacer(Modifier.height(12.dp))` at a call site is the thing this object exists to
 * prevent. The cost is not tidiness — five screens that each wrote their own chrome ran their header
 * boxes at 0/16, 0/4, 12/4, 12/4 and 10/12, and nothing on any *one* screen looked wrong. The app did,
 * because the heading moved every time the user crossed a screen.
 *
 * `DesignTokenTest` reads the sources as text to enforce this, because a literal and a token compile
 * to identical bytecode and the difference survives nowhere else (LLM.md §10.5).
 */
object Spacing {
    /** Hairline separation inside a single control — a badge from its label. */
    val xxs = 2.dp

    /** Between two lines of one item: a row's title and its subtitle. */
    val xs = 4.dp

    /** Between adjacent controls in one group. */
    val sm = 8.dp

    /** The default gap between list rows. */
    val md = 12.dp

    /** The default gap between distinct blocks, and the page gutter's value. */
    val lg = 16.dp

    /** Between sections of one page. */
    val xl = 24.dp

    /** Around a page's own top and bottom content. */
    val xxl = 32.dp
}
