package com.pion.phonecleaner.feature.applock.lockscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
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
import coil3.compose.AsyncImage
import com.pion.phonecleaner.core.ui.component.pin.PinDots
import com.pion.phonecleaner.core.ui.component.pin.PinKeypad
import com.pion.phonecleaner.core.ui.icon.AppIconLoader
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.domain.model.applock.PIN_LENGTH
import com.pion.phonecleaner.feature.applock.R
import com.pion.phonecleaner.feature.applock.component.LockoutNotice
import com.pion.phonecleaner.feature.applock.pin.PinError
import org.koin.compose.koinInject

/**
 * The lock surface's content (`docs/screens/16-app-lock.md` §3.3).
 *
 * **No `PageHeader` and no back arrow.** MVI §11 fixes a document screen to `PageHeader` and an
 * immersive one to `OverlayHeader`; this is neither. It is a modal gate over another app with no
 * "up" — the only exits are a correct PIN and the launcher — so a header offering a back affordance
 * would be offering an exit that does not exist.
 *
 * The app's own icon and label are both shown. The competitor renders an empty title, so the user is
 * asked for a PIN by a screen that names nothing (§3.5).
 */
@Composable
internal fun LockScreenScreen(
    state: LockScreenState,
    onIntent: (LockScreenIntent) -> Unit,
    modifier: Modifier = Modifier,
    shakeOffset: Dp = 0.dp,
) {
    val icons = koinInject<AppIconLoader>()
    // Held steady, so the twelve keys keep their skip across a dot change (`LLM.md` §8).
    val onDigit = remember(onIntent) {
        { digit: Int -> onIntent(LockScreenIntent.DigitPressed(digit)) }
    }
    val onBackspace = remember(onIntent) { { onIntent(LockScreenIntent.BackspacePressed) } }

    Surface(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .screenInsetsPadding()
                .padding(horizontal = ScreenGutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xl, Alignment.CenterVertically),
        ) {
            AsyncImage(
                model = icons.request(state.targetPackage),
                imageLoader = icons.imageLoader,
                contentDescription = null, // decorative: the prompt below names the app
                modifier = Modifier.size(TargetIconSize),
            )
            Text(
                text = stringResource(R.string.lock_screen_prompt, state.displayName()),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            PinDots(filledCount = state.digits, totalCount = PIN_LENGTH, shakeOffset = shakeOffset)
            Text(
                text = stringResource(
                    if (state.errorKind == PinError.WrongPin) R.string.pin_error_wrong
                    else R.string.pin_prompt_unlock,
                ),
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
                    onElapsed = { onIntent(LockScreenIntent.LockoutElapsed) },
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

/**
 * The label when the enumeration resolved one, the package name when it did not. Never blank: a
 * prompt that names nothing is the defect this screen exists to fix.
 */
private fun LockScreenState.displayName(): String =
    targetLabel.ifEmpty { targetPackage.ifEmpty { UnknownAppName } }

/** A **position**, not a gap (MVI §11): 56 dp is the size §3.3 measures the target icon at. */
private val TargetIconSize = 56.dp

/**
 * UNKNOWN — no copy exists for "the extra was missing". Looked in
 * `docs/screens/16-app-lock.md` §3.1/§3.3 and in `:core:ui`'s shared strings. An em dash is a
 * placeholder that reads the same in all 17 locales and claims nothing; a fabricated sentence would
 * not.
 */
private const val UnknownAppName = "—"
