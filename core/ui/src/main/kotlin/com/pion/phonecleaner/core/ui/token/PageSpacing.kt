package com.pion.phonecleaner.core.ui.token

import androidx.compose.ui.unit.dp

/**
 * The five gaps that must agree **across** screens — MVI §11's second row. `Spacing` says how far
 * apart two things are; this says where the page's own furniture sits, and a literal repeated on
 * three screens is exactly what it replaces.
 *
 * The three derived from [Spacing] are gaps. [listBottom] and [snackbarLift] are **positions**, not
 * gaps: MVI §11 says a position is not on the 4 dp scale at all and must carry a sentence saying what
 * it was measured against. Both do.
 *
 * UNKNOWN — no measurement for any of the five is stated in the corpus. Looked in
 * `docs/android-mvi-best-practices.md` §11 (which names the five members but gives no values),
 * `docs/system-architecture.md` §4.7, and `docs/screens/21-shared-models-and-ui.md` §4. The
 * competitor has no design system to read one off: its chrome is `<include>` plus `findViewById`
 * with no styles and no themed attributes (§4.7). The values below are therefore chosen, not
 * recovered — but they are chosen **once**, which is the whole point of the type.
 */
object PageSpacing {
    /** Above a `PageHeader`, inside the page's own inset padding. */
    val headerTop = Spacing.sm

    /** Between the header's baseline block and the first item of content. */
    val headerToContent = Spacing.lg

    /** Between two sections of one scrolling page. */
    val sectionGap = Spacing.xl

    /**
     * Bottom `contentPadding` for a list that a `SelectionBar` floats over.
     * Measured against `SelectionBar`: its 72 dp bar height plus one [Spacing.xl], so the last row
     * can still be scrolled clear of the bar instead of resting under it.
     */
    val listBottom = 96.dp

    /**
     * How far a snackbar sits above the bottom edge.
     * Measured against `SelectionBar` again: the same 72 dp, so a snackbar raised while a selection
     * is active does not cover the Delete action it is reporting on.
     */
    val snackbarLift = 72.dp
}
