package com.pion.phonecleaner.feature.applock.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.pion.phonecleaner.feature.applock.R
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * "Too many attempts. Try again in N s." — the visible half of `PinLockout`.
 *
 * **The countdown lives here, not on either ViewModel.** `PinLockout.lockedUntil` is an absolute
 * `Instant` precisely so that nothing has to stay alive to count it down: a process kill cannot
 * shorten a deadline, and a ticker in a ViewModel would be a second clock disagreeing with the
 * repository's. When it reaches zero this raises [onElapsed], and the next attempt is still refused
 * by the repository if it is somehow early.
 *
 * The competitor has no lockout at all — `SacskipActivity.java:107-115` and
 * `GratinActivity.java:63-72` allow unlimited retries against a 10 000-entry space with no backoff.
 *
 * Shared by `pin` and `lockscreen` because they share one persisted allowance
 * (`docs/screens/16-app-lock.md` §2.5), which is the whole reason the record is in the repository.
 */
@Composable
internal fun LockoutNotice(
    until: Instant,
    onElapsed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val elapsed by rememberUpdatedState(onElapsed)
    var secondsLeft by remember(until) { mutableIntStateOf(until.secondsFromNow()) }

    LaunchedEffect(until) {
        while (true) {
            val left = until.secondsFromNow()
            secondsLeft = left
            if (left <= 0) {
                elapsed()
                break
            }
            delay(TickMillis)
        }
    }

    Text(
        text = stringResource(
            R.string.pin_lockout_notice,
            stringResource(R.string.pin_lockout_seconds, secondsLeft),
        ),
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
    )
}

/** Rounded **up**, so the notice never reads "0 s" while input is still refused. */
private fun Instant.secondsFromNow(): Int {
    val remaining = this - Clock.System.now()
    if (remaining <= Duration.ZERO) return 0
    return ((remaining.inWholeMilliseconds + 999L) / 1000L).toInt()
}

/** One tick per printed unit, so the number the user reads never skips (MVI §11). */
private const val TickMillis = 1_000L
