package com.pion.phonecleaner.feature.junk.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.Spacing

/**
 * The big number, animating toward whatever the state currently says.
 *
 * Shared by `junkscan` and `junkclean`, which is why it lives in the cluster's own `component/`
 * package (`LLM.md` §4) rather than in either screen.
 *
 * It replaces two competitor mechanisms at once:
 *
 *  * `wc.e`'s runnable `d` — a 50 ms `Handler` tick with a hand-written five-bucket magnitude step
 *    table, in which `PlaybackStateCompat.ACTION_SET_CAPTIONING_ENABLED` is used as the number
 *    1 048 576 (`docs/screens/12-junk-cleaning.md` §3.3);
 *  * `MenaremovActivity.W(target)` — a suspending wrapper around a `ValueAnimator` with a
 *    `CancellableContinuation`, an `invokeOnCancellation` and a hand-held animator field cancelled in
 *    `onDestroy`, which **each deletion awaits before the next begins**: forty selected paths cost at
 *    least ten seconds of animation regardless of filesystem speed (Delta C5).
 *
 * **This counter is never on the deletion's critical path.** It interpolates toward the target; the
 * loop that produces the target never waits for it.
 *
 * The interpolation is a `Float`, so a value in the gigabytes is exact only to tens of bytes
 * mid-flight. That is display precision on a number that is redrawn every frame; the *state* carries
 * the exact `Long` and the ledger is written from that, never from what is on screen — the
 * competitor's ledger is fed by re-parsing its own formatted output (`md.g4.e()`).
 */
@Composable
fun AnimatedByteCounter(
    targetBytes: Long,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(
        targetValue = targetBytes.toFloat(),
        animationSpec = tween(durationMillis = CounterDurationMillis),
        label = "junkByteCounter",
    )
    val format = rememberByteFormat()
    val displayed = format.size(animated.toLong().coerceAtLeast(0L))
    val exact = format.size(targetBytes.coerceAtLeast(0L))
    Row(
        // The announced value is the settled one: a screen reader reading an interpolation would say
        // a different number every frame and none of them the answer.
        modifier = modifier.semantics { contentDescription = exact.toString() },
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(text = displayed.value, style = MaterialTheme.typography.displayMedium)
        Text(
            text = displayed.unit,
            modifier = Modifier.padding(start = Spacing.xs),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** One tween, replacing five hand-written magnitude buckets. */
private const val CounterDurationMillis = 400
