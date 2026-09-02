package com.pion.phonecleaner.feature.onboarding.appresume

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import org.koin.androidx.compose.koinViewModel

/**
 * Public and stateful; one navigation callback in, nothing out (MVI §4).
 *
 * `:app` hosts this as a **`dialog { }` destination on the existing back stack** (`LLM.md` §7.5),
 * and [onDismiss] is a `popBackStack()`. The competitor starts a whole Activity from `Application`
 * scope with `FLAG_ACTIVITY_NEW_TASK` (`Condfil.java:858-868`).
 *
 * ### What is NOT here, and why
 *
 * `AppResumeGate` — the thing that decides *whether* this window opens — is not implemented by this
 * change. It is a process-level policy that needs two things this cluster cannot own: the current
 * nav back-stack entry (`currentRoute !in RESUME_EXEMPT_ROUTES`, a `:app` fact) and the
 * `ProcessLifecycleOwner` observer plus its 517 ms debounce, whose home is a `:data` package outside
 * this change's ownership. It also sits directly on **pending owner decision 4** — how assertive the
 * app is outside itself. The route and its window ship; the trigger is reported, not guessed.
 */
@Composable
fun AppResumeRoute(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppResumeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            AppResumeEffect.Dismiss -> onDismiss()
        }
    }

    LifecycleStartEffect(viewModel) {
        onIntent(AppResumeIntent.Shown)
        onStopOrDispose { }
    }

    // Unlike the splash, back is NOT swallowed here: this is a window the user did not ask for, and
    // the ramp behind it is not collecting a decision that would be lost.
    BackHandler(enabled = !state.isBusy) { onIntent(AppResumeIntent.DismissRequested) }

    AppResumeScreen(state = state, onIntent = onIntent, modifier = modifier)
}
