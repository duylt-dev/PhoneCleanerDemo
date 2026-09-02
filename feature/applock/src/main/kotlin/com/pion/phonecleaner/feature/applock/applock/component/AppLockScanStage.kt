package com.pion.phonecleaner.feature.applock.applock.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.applock.R
import kotlinx.coroutines.delay

/**
 * The opening stage. **It is decoration with a timeout, not a gate.**
 *
 * The competitor shows its list only when a Lottie `onAnimationEnd` fires: clear the animation,
 * detach the view or disable animations system-wide and the tabs never appear
 * (`docs/screens/16-app-lock.md` §1.5). Here the list already exists in state; what this stage does
 * is delay showing it, and the `LaunchedEffect` below raises [onFinished] **regardless** of whether
 * anything animated.
 *
 * > UNKNOWN — the animation asset. `lottie-compose` is in `gradle/libs.versions.toml` (line 105) and
 * > `LLM.md` §12 records it as "decoration only; never gates data", but no asset name for this
 * > screen is recovered anywhere: looked in `docs/screens/16-app-lock.md` §1.3 (which writes
 * > `LottieAnimation(scan)` / `LottieAnimation(complete)` with no file), in
 * > `docs/reverse-engineering/16-app-lock.md`, and in this repository's `src/main/assets` and
 * > `src/main/res/raw`, both of which are absent. A determinate indicator is drawn instead of naming
 * > a file that does not exist; swapping it for the asset changes this file and nothing else.
 */
@Composable
internal fun AppLockScanStage(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val finished by rememberUpdatedState(onFinished)
    LaunchedEffect(Unit) {
        delay(ScanStageMillis)
        finished()
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = ScreenGutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator(Modifier.size(IndicatorSize))
        Text(
            text = stringResource(R.string.app_lock_scanning),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A **position**, not a gap (MVI §11): 64 dp reads as an illustration rather than as a control.
 */
private val IndicatorSize = 64.dp

/**
 * How long the stage holds the list back.
 *
 * `docs/screens/16-app-lock.md` §1.2 says the floor is not a `delay` in the ViewModel and that
 * `MinimumDuration` (`coreModule`) is available **if the product wants one** — it is not wired here,
 * because a floor the user cannot skip belongs in a use case rather than in a composable. This value
 * is the animation's own length, and the competitor's is four seconds; that is long enough to feel
 * like a stall over a list that is already in memory, so it is shorter.
 */
private const val ScanStageMillis = 1_200L
