package com.pion.phonecleaner.feature.onboarding.appresume

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.feature.onboarding.component.BrandBlock

/**
 * Stateless: `(state, onIntent) -> Unit`, never the ViewModel (MVI §4). It takes `onIntent` even
 * though nothing below it raises one today, because that is the contract every `XScreen` in this
 * codebase has and a screen that drops it is the one a later change has to re-plumb.
 *
 * The tree is the splash's, **with `ConsentRow` simply not called** (delta 6). `ScoutioneActivity`
 * reuses `posparce.xml` and sets that row `INVISIBLE`, so 48 dp of the window is reserved for a
 * control that can never appear there.
 *
 * No header: the same reason as the splash — nothing to go back to and nothing to title (MVI §11).
 */
@Composable
internal fun AppResumeScreen(
    state: AppResumeState,
    @Suppress("UNUSED_PARAMETER") onIntent: (AppResumeIntent) -> Unit,
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
            LinearProgressIndicator(
                progress = { state.progress.toFloat() / AppResumeState.PROGRESS_MAX },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
