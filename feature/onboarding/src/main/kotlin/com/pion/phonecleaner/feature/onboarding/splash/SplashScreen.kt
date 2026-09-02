package com.pion.phonecleaner.feature.onboarding.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.pion.phonecleaner.core.ui.error.userMessage
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.feature.onboarding.R
import com.pion.phonecleaner.feature.onboarding.component.BrandBlock
import com.pion.phonecleaner.feature.onboarding.component.ConsentRow

/**
 * Stateless: `(state, onIntent) -> Unit`, never the ViewModel (MVI §4).
 *
 * `posparce.xml` is a `FrameLayout` over a vertical `LinearLayout` of six views
 * (`docs/screens/10-splash-and-onboarding.md` §1.3). Five survive. The sixth, `adaptinsis`, is the
 * full-screen ad container: it is **not** a composable here — the ad boundary owns an Activity-level
 * container, and the Route hands it one only if the chosen network needs it.
 *
 * **No header, deliberately.** MVI §11 says a screen renders `PageHeader` or `OverlayHeader` and
 * never writes its own; a splash renders neither, the way `LensScreen` renders neither, because there
 * is nothing to go back to and nothing to title. It writes no header of its own either.
 *
 * The outermost container takes the inset (MVI §11): in landscape the cutout moves to one side and
 * the whole page moves with it. Nothing here is a `LazyColumn` — the tree is fixed at six elements.
 */
@Composable
internal fun SplashScreen(
    state: SplashState,
    onIntent: (SplashIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .screenInsetsPadding()
                .padding(horizontal = ScreenGutter)
                .padding(top = PageSpacing.sectionGap, bottom = PageSpacing.headerToContent),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            BrandBlock()
            Spacer(Modifier.weight(1f))
            SplashProgress(state)
            if (state.isConsentPanelVisible) ConsentPanel(state, onIntent)
        }
    }
}

/**
 * `withdeabl`. `state.isProgressComplete` rather than `state.progress >= 100` — the screen never
 * derives business truth (MVI §4); that comparison is a `val` on the state class.
 */
@Composable
private fun SplashProgress(state: SplashState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        LinearProgressIndicator(
            progress = { state.progress.toFloat() / SplashState.PROGRESS_MAX },
            modifier = Modifier.fillMaxWidth(),
        )
        if (!state.isProgressComplete) {
            Text(
                text = stringResource(R.string.onboarding_splash_progress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // A failed latch write or preference read is reported and NOT acted on: an error must never
        // hold a user on a splash, so the flow continues underneath this line
        // (`docs/screens/10-splash-and-onboarding.md` §1.2). There is no retry to offer, which is
        // why this is a caption and not an `ErrorCard`.
        state.error?.let { error ->
            Text(
                text = error.userMessage(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * `covular` and its five children.
 *
 * The button is enabled whatever the box says: tapping it unticked is what raises
 * [SplashEffect.ShowPoliciesRequired], which is the one place in this screen allowed to complain out
 * loud (delta 7). Disabling it would leave a user with no way to find out why nothing happens.
 */
@Composable
private fun ConsentPanel(
    state: SplashState,
    onIntent: (SplashIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ConsentRow(
            accepted = state.hasAcceptedPolicies,
            onAcceptedChange = { onIntent(SplashIntent.PoliciesAcceptanceChanged(it)) },
            onTermsClick = { onIntent(SplashIntent.TermsClicked) },
            onPrivacyClick = { onIntent(SplashIntent.PrivacyPolicyClicked) },
        )
        Button(
            onClick = { onIntent(SplashIntent.ContinueRequested) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isBusy,
        ) {
            Text(stringResource(R.string.onboarding_splash_continue))
        }
    }
}
