package com.pion.phonecleaner.feature.junk.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The phase illustration, and **the owner of the completion gate**.
 *
 * Both `junkscan` and `junkclean` may only leave once the work has finished *and* this composable has
 * reported that its finishing animation is done (`JunkScanState.canLeave`, `JunkCleanState.canLeave`).
 * The competitor implements the same two-condition gate as recursive Lottie replays plus a 1 500 ms
 * wall clock, with an **unbounded self-recursion branch** and a synchronous callback when the
 * composition fails to load (`TaribrActivity.java:325-332`, `wc/j.java:156-160`, Delta S2).
 *
 * [onCompletionFinished] is therefore raised by a `LaunchedEffect` timer that runs regardless of what
 * the illustration does, so a failed or missing animation can never strand the user on this screen —
 * the exact hazard the competitor's path creates.
 *
 * ## UNKNOWN — the Lottie compositions, and why this file is not called `JunkLottie.kt`
 *
 * §3.3 and §5.3 name two Lottie animations, `search_trash` and `clean_trash`. **Neither asset exists
 * in this repository** — looked for under every module's `src/main/res/raw` and `src/main/assets`,
 * and in the version catalogue's own usage: `lottie-compose` is declared in
 * `gradle/libs.versions.toml` and is on no module's dependency list. A `JunkLottie` that loads no
 * Lottie composition would be a name asserting something untrue, and `LLM.md` §5 requires a file to
 * be named for the type it declares — so the appendix's `component/JunkLottie.kt` slot is filled by
 * this file under an honest name. Adding the dependency and pointing it at an asset that is not
 * there would ship a broken animation; the composition goes in here, behind the same two parameters,
 * on the day the assets do.
 *
 * DEVIATION — §3.3 says `MinimumDuration` (`:core:common/policy`) supplies the floor "so a test sets
 * it to `Duration.ZERO`". `MinimumDuration` is a constructor-injected collaborator, and this is a
 * leaf composable two levels below the Route; injecting it here would put a Koin lookup inside a
 * drawing function. The floor is a named constant instead, and the thing a test actually asserts —
 * that `canLeave` becomes true and exactly one navigation effect is raised — is a ViewModel test that
 * calls `onIntent(CompletionAnimationFinished)` directly (§8.2).
 */
@Composable
fun JunkPhaseAnimation(
    isFinished: Boolean,
    onCompletionFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val handler by rememberUpdatedState(onCompletionFinished)
    LaunchedEffect(isFinished) {
        if (!isFinished) return@LaunchedEffect
        delay(CompletionFallbackMillis)
        handler()
    }

    val transition = rememberInfiniteTransition(label = "junkPhase")
    val pulse by transition.animateFloat(
        initialValue = PulseMinAlpha,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PulseDurationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "junkPhasePulse",
    )

    Box(modifier.size(IllustrationSize), contentAlignment = Alignment.Center) {
        if (isFinished) {
            Icon(
                imageVector = Icons.Filled.Done,
                contentDescription = null, // decorative: the phase text beside it says the same
                modifier = Modifier.size(IconSize),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(IconSize).alpha(pulse),
                strokeWidth = StrokeWidth,
            )
        }
    }
}

/**
 * The safety timer. It is also, today, the minimum time the finished state is visible — the floor
 * §3.3 describes, so a scan that completes instantly does not flash past.
 */
private const val CompletionFallbackMillis = 900L
private const val PulseDurationMillis = 900
private const val PulseMinAlpha = 0.45f
private val IllustrationSize = 160.dp
private val IconSize = 72.dp
private val StrokeWidth = 6.dp
