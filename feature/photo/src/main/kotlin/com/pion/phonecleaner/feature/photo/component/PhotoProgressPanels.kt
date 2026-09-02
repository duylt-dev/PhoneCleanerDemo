package com.pion.phonecleaner.feature.photo.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.photo.R

/**
 * Real progress, or an honestly indeterminate bar when the engine cannot know the total yet.
 *
 * The competitor pads every scan to 4 000 ms with `delay(od.q0.a(t0))` and covers the wait with a
 * Lottie, so a 40-second scan and a 2-second scan look the same
 * (`docs/screens/13-photo-and-media.md` §0.5). There is no floor here — `MinimumDuration` exists so
 * a *deliberate* one can be set, and this cluster sets none.
 */
@Composable
fun PhotoScanPanel(
    label: String,
    done: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = ScreenGutter, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        if (total > 0) {
            Text(
                text = stringResource(R.string.photo_scan_progress, done, total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { done.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * The `Completing` phase, drawn.
 *
 * **The composable owns the animation and reports back with an Intent.** The competitor reveals its
 * list from a Lottie `onAnimationEnd` *and then* from an interstitial's close callback, so the list
 * never appears when the ad SDK is absent (`docs/screens/13-photo-and-media.md` §0.2, §1.4). Here
 * the ViewModel never learns an animation exists; it only receives `CompletionAnimationFinished`.
 */
@Composable
fun PhotoCompletionPanel(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val finished by rememberUpdatedState(onFinished)
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        sweep.animateTo(1f, tween(durationMillis = CompletionSweepMillis))
        finished()
    }
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = ScreenGutter, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.photo_scan_finished),
            style = MaterialTheme.typography.bodyMedium,
        )
        LinearProgressIndicator(progress = { sweep.value }, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * How long the completion sweep runs. It is this composable's own animation length, measured
 * against nothing but itself — it is NOT a floor on the scan, and no data waits on it: the scan has
 * already finished when the phase becomes `Completing`.
 */
private const val CompletionSweepMillis: Int = 450
