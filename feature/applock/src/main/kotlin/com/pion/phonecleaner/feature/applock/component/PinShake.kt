package com.pion.phonecleaner.feature.applock.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The wrong-PIN nudge. **Purely visual local state**, which MVI §4 permits and MVI §3 requires: a
 * failure animation is a one-shot, so it arrives as `PinEffect.ShakeKeypad` and is played here.
 * Replaying it on rotation — which is what holding it on `State` would do — would tell the user they
 * had just got the PIN wrong when they had only turned the phone.
 *
 * `PinDots` in `:core:ui` takes the offset as a `Dp` and knows nothing about why it moved.
 */
@Stable
internal class PinShakeState {

    private val animatable = Animatable(0f)

    val offset: Dp get() = animatable.value.dp

    suspend fun play() {
        animatable.snapTo(0f)
        repeat(ShakeCycles) {
            animatable.animateTo(ShakeAmplitude, tween(ShakeStepMillis))
            animatable.animateTo(-ShakeAmplitude, tween(ShakeStepMillis))
        }
        animatable.animateTo(0f, tween(ShakeStepMillis))
    }
}

@Composable
internal fun rememberPinShake(): PinShakeState = remember { PinShakeState() }

/** A **position**, not a gap (MVI §11): 8 dp is visible without moving the dots off their centre. */
private const val ShakeAmplitude = 8f
private const val ShakeCycles = 2
private const val ShakeStepMillis = 45
