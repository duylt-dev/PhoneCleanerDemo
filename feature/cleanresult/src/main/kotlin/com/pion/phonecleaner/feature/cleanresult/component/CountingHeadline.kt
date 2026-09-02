package com.pion.phonecleaner.feature.cleanresult.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

/**
 * The count-up, which is the one honest job of the competitor's 3 500 ms "Cleaning" destination
 * (`docs/screens/14-file-tools-and-app-manager.md` §8).
 *
 * **Nothing reads a value back out of a `Text`.** The competitor's `ValueAnimator` reads its own
 * start value out of the `TextView` it animates, and `onDestroy` fails to cancel it — so it goes on
 * to navigate from a finished Activity. Here the figure is derived from an animated fraction, and
 * the animation dies with the composition.
 *
 * The composable owns the animation and reports completion with an Intent ([onFinished]); the reveal
 * is never driven by an ad SDK's close callback.
 */
@Composable
internal fun CountingHeadline(
    targetBytes: Long,
    counting: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    var started by remember { mutableStateOf(false) }
    val fraction by animateFloatAsState(
        // A screen re-entered after the reveal shows the final figure immediately; only a live
        // Counting phase animates.
        targetValue = if (started || !counting) 1f else 0f,
        animationSpec = tween(if (counting) CountMillis else 0, easing = LinearEasing),
        label = "cleanResultCountFraction",
    )

    LaunchedEffect(counting) {
        if (!counting) return@LaunchedEffect
        started = true
        delay(CountMillis.toLong())
        onFinished()
    }

    val bytes = rememberByteFormat()
    val shown = (targetBytes * fraction.coerceIn(0f, 1f)).roundToLong()
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = ScreenGutter, vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = bytes.size(shown).toString(),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
        )
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
        }
    }
}

/** A **duration**, not a gap: how long the figure takes to reach its total. */
private const val CountMillis = 900
