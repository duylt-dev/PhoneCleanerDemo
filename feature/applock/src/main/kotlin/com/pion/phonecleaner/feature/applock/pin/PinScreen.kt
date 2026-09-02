package com.pion.phonecleaner.feature.applock.pin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.component.pin.PinDots
import com.pion.phonecleaner.core.ui.component.pin.PinKeypad
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.domain.model.applock.PIN_LENGTH
import com.pion.phonecleaner.domain.model.applock.PinMode
import com.pion.phonecleaner.feature.applock.R
import com.pion.phonecleaner.feature.applock.component.LockoutNotice

/**
 * The pad (`docs/screens/16-app-lock.md` §2.3). It renders `state.digits` — a **count** — and emits
 * key events; no composable below it ever holds a digit, and `PinDots`/`PinKeypad` in `:core:ui` are
 * stateless for the same reason.
 *
 * [shakeOffset] comes from the Route, because the animation is played by the `ShakeKeypad` Effect
 * collector and an `Animatable` is purely visual local state (MVI §4). It is a `Dp`, so this
 * composable can still skip.
 */
@Composable
internal fun PinScreen(
    state: PinState,
    onIntent: (PinIntent) -> Unit,
    modifier: Modifier = Modifier,
    shakeOffset: Dp = 0.dp,
) {
    // Held steady, so the twelve keys keep their skip across a dot change (`LLM.md` §8).
    val onDigit = remember(onIntent) { { digit: Int -> onIntent(PinIntent.DigitPressed(digit)) } }
    val onBackspace = remember(onIntent) { { onIntent(PinIntent.BackspacePressed) } }

    Surface(modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.screenInsetsPadding(),
            topBar = {
                PageHeader(
                    title = stringResource(state.titleRes()),
                    onBack = { onIntent(PinIntent.BackPressed) },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = ScreenGutter),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xl, Alignment.CenterVertically),
            ) {
                // Both Set and Change are two-step, so both show it. The competitor shows the
                // indicator in Change mode only, although Set is equally two-step.
                if (state.showsStepIndicator) {
                    Text(
                        text = stringResource(R.string.pin_step_indicator, state.stepNumber()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PinDots(
                    filledCount = state.digits,
                    totalCount = PIN_LENGTH,
                    shakeOffset = shakeOffset,
                )
                Text(
                    text = stringResource(state.promptRes()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (state.errorKind == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    textAlign = TextAlign.Center,
                )
                state.lockedOutUntil?.let { until ->
                    LockoutNotice(
                        until = until,
                        onElapsed = { onIntent(PinIntent.LockoutElapsed) },
                    )
                }
                PinKeypad(
                    onDigit = onDigit,
                    onBackspace = onBackspace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = PageSpacing.headerToContent),
                    enabled = state.acceptsInput,
                )
            }
        }
    }
}

/** Copy is chosen here, never built in the ViewModel (MVI §5). */
private fun PinState.titleRes(): Int = when (mode) {
    PinMode.Set -> R.string.pin_title_set
    PinMode.Change -> R.string.pin_title_change
    PinMode.Verify -> R.string.pin_title_verify
}

/** The prompt is the flow's sentence, so an error replaces it rather than sitting beside it. */
private fun PinState.promptRes(): Int = when {
    errorKind == PinError.WrongPin -> R.string.pin_error_wrong
    errorKind == PinError.Mismatch -> R.string.pin_error_mismatch
    step == PinStep.Verify -> R.string.pin_prompt_verify_old
    step == PinStep.Confirm -> R.string.pin_prompt_confirm
    mode == PinMode.Verify -> R.string.pin_prompt_unlock
    else -> R.string.pin_prompt_enter
}

private fun PinState.stepNumber(): Int = if (step == PinStep.Confirm) 2 else 1
