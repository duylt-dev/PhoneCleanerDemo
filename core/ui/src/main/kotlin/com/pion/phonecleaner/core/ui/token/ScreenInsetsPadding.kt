package com.pion.phonecleaner.core.ui.token

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The top edge — and the three other edges the top edge is usually mistaken for.
 *
 * **This exists because `statusBarsPadding()` cost a real defect** (MVI §11): a page reached for it
 * in five places, the system bars were hidden at the time so the inset was zero, and the notch was
 * still a hole in the glass. Five controls sat under the cutout on every phone that has one. The
 * status bar coming back fixes the phone held upright and nothing else — turn it sideways and the
 * cutout is on an edge no bar reports.
 *
 * So the inset is `systemBars ∪ displayCutout`, on all four sides, and it is applied by **the
 * screen's outermost container** — or by `OverlayHeader`, which floats over content so nothing else
 * is in a position to (MVI §11's table).
 *
 * `safeDrawing` is deliberately not used: it folds in the IME, so the whole page would shift when a
 * keyboard opens. Whether that is right is a per-screen decision, and a screen that wants it says so.
 */
@Composable
fun Modifier.screenInsetsPadding(): Modifier =
    windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))

/**
 * The same inset on chosen sides — `WindowInsetsSides.Top + WindowInsetsSides.Horizontal` for a
 * header, when the bottom edge belongs to something else (a `SelectionBar`, a `Scaffold`).
 */
@Composable
fun Modifier.screenInsetsPadding(sides: WindowInsetsSides): Modifier =
    windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout).only(sides))
