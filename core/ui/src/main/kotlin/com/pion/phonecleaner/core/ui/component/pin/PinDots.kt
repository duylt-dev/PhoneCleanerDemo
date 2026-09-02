package com.pion.phonecleaner.core.ui.component.pin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.R
import com.pion.phonecleaner.core.ui.token.Spacing

/**
 * How many digits have been entered. **It never sees a digit.**
 *
 * `Conqterio`, the competitor's PIN pad, keeps the digits entered so far *inside the view*, decides
 * on its own when the PIN is complete, and fires a callback carrying the whole list. This component
 * takes a count. The digits live on the App Lock ViewModel's `State`, and completion is decided where
 * the comparison happens — **a component that never holds the PIN cannot leak it into a
 * recomposition, a screenshot or a saved-state bundle** (`docs/screens/21` §4.4).
 *
 * The whole row carries one content description and its children carry none: a screen reader that
 * announced each dot as it filled would be reading out the PIN's length in real time.
 *
 * @param shakeOffset a wrong-PIN nudge, driven by an animation the *screen* owns. The prompt shown
 *   with it ("enter" vs "confirm") is a sentence about the flow, not about this component, so it is an
 *   enum on the App Lock state and not a parameter here (§4.4).
 */
@Composable
fun PinDots(
    filledCount: Int,
    modifier: Modifier = Modifier,
    totalCount: Int = DefaultPinLength,
    shakeOffset: Dp = 0.dp,
) {
    val description = stringResource(R.string.pin_dots_state, filledCount, totalCount)
    Row(
        modifier = modifier
            .offset(x = shakeOffset)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(totalCount) { index ->
            val filled = index < filledCount
            Box(
                Modifier
                    .size(DotSize)
                    .background(
                        color = if (filled) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = CircleShape,
                    )
                    .border(DotStroke, MaterialTheme.colorScheme.outline, CircleShape),
            )
        }
    }
}

/** The competitor's own PIN length, and `docs/screens/21` §4.4's default: `PinDots(filledCount, 4)`. */
const val DefaultPinLength = 4

/** Positions, not gaps (MVI §11): a dot large enough to see across a room, at a hairline stroke. */
private val DotSize = 16.dp
private val DotStroke = 1.dp
