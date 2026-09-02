package com.pion.phonecleaner.core.ui.component.pin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.R
import com.pion.phonecleaner.core.ui.token.Spacing

/**
 * The 4 × 3 keypad. **It holds nothing** — see `PinDots`' KDoc for what that buys.
 *
 * Nested `Row`s inside a `Column`, not a lazy grid: twelve always-visible fixed cells, so a lazy grid
 * would pay for recycling that can never happen and would need a fixed height inside a scrolling
 * parent (`docs/screens/16-app-lock.md:378`). This is also where `Exoduhis` goes — a self-measuring
 * grid whose `onMeasure` passes the **loop index** as the measure-spec size (`docs/screens/21` §4.4).
 *
 * [onDigit] emits one digit, once. It does not know how many have been entered, whether the PIN is
 * complete, or what it is being compared against.
 *
 * @param enabled `false` while a comparison is in flight, so a fifth tap cannot race the fourth.
 */
@Composable
fun PinKeypad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        KeyRow(1..3, enabled, onDigit)
        KeyRow(4..6, enabled, onDigit)
        KeyRow(7..9, enabled, onDigit)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(Modifier.weight(1f).height(KeyHeight)) // the empty tenth cell
            DigitKey(0, enabled, onDigit, Modifier.weight(1f))
            TextButton(
                onClick = onBackspace,
                enabled = enabled,
                modifier = Modifier.weight(1f).height(KeyHeight),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = stringResource(R.string.pin_backspace),
                )
            }
        }
    }
}

@Composable
private fun KeyRow(digits: IntRange, enabled: Boolean, onDigit: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        digits.forEach { DigitKey(it, enabled, onDigit, Modifier.weight(1f)) }
    }
}

@Composable
private fun DigitKey(
    digit: Int,
    enabled: Boolean,
    onDigit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = { onDigit(digit) },
        enabled = enabled,
        modifier = modifier.height(KeyHeight),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(digit.toString(), style = MaterialTheme.typography.headlineSmall)
        }
    }
}

/** A **position**, not a gap (MVI §11): 64 dp gives a comfortable touch target on a 12-cell pad. */
private val KeyHeight = 64.dp
