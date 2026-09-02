package com.pion.phonecleaner.feature.onboarding.splash

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import com.pion.phonecleaner.domain.model.onboarding.ConsentHost
import com.pion.phonecleaner.domain.model.settings.LegalDocument
import com.pion.phonecleaner.feature.onboarding.R
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Public and stateful; navigation callbacks in, nothing out (MVI §4). It never names another
 * feature's route — that is what makes `:feature:A -> :feature:B` unnecessary rather than merely
 * forbidden (`LLM.md` §2, §7.1).
 *
 * Everything platform-shaped lives here and reports upward: the permission launcher, the `Activity`
 * the consent form presents itself over, and the two lifecycle edges. `SplashViewModel` imports no
 * `android.*` type at all.
 *
 * `CollectEffects` has a branch for **every** declared effect and no `else`, uses `collect` (never
 * `collectLatest`) and is lifecycle-aware — two navigations raised in one frame must not collapse
 * into one.
 */
@Composable
fun SplashRoute(
    onNavigateToDeviceCheck: () -> Unit,
    onNavigateToHome: () -> Unit,
    onOpenPolicyPage: (LegalDocument) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SplashViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }

    val activity = LocalActivity.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val policiesRequired = stringResource(R.string.onboarding_splash_policies_required)

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onIntent(SplashIntent.NotificationPermissionResolved(granted))
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            SplashEffect.RequestNotificationPermission -> requestNotifications(
                launch = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                onNotApplicable = { onIntent(SplashIntent.NotificationPermissionResolved(true)) },
            )

            // The handle, not the round trip: the suspend call is the ViewModel's, over
            // `ConsentRepository`, so `android.app.Activity` never reaches it. A null Activity —
            // the composable is previewed, or the window is already gone — settles the gate with the
            // same "unavailable" answer the repository gives, rather than hanging the splash.
            SplashEffect.RequestConsent ->
                onIntent(SplashIntent.ConsentHostReady(ActivityConsentHost(activity)))

            is SplashEffect.OpenPolicyPage -> onOpenPolicyPage(effect.page)

            // `showSnackbar` suspends for the length of the notice, so it runs in the screen's own
            // scope. Holding the collector open for it would stall every effect behind it.
            SplashEffect.ShowPoliciesRequired -> scope.launch {
                snackbarHostState.showSnackbar(policiesRequired)
            }

            SplashEffect.NavigateToDeviceCheck -> onNavigateToDeviceCheck()
            SplashEffect.NavigateToHome -> onNavigateToHome()
        }
    }

    LifecycleStartEffect(viewModel) {
        onIntent(SplashIntent.ScreenStarted)
        onStopOrDispose { }
    }

    // The permission funnel's return leg (`LLM.md` §7.4): the user read a policy page, pressed back,
    // and the same reducer is re-entered. Idempotent — on the first resume no policy page was open.
    LifecycleResumeEffect(viewModel) {
        onIntent(SplashIntent.ReturnedFromPolicyPage)
        onPauseOrDispose { }
    }

    // Inert ON PURPOSE, and ported deliberately (delta 12). `CucurtagActivity:130-138` swallows back
    // with an empty `OnBackPressedCallback`; a splash a user can back out of mid-consent leaves the
    // app with no record of what they agreed to, which is worse than one they cannot leave.
    BackHandler(enabled = true) { }

    Box(modifier.fillMaxSize()) {
        SplashScreen(state = state, onIntent = onIntent)
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * `POST_NOTIFICATIONS` does not exist below API 33, and launching a request for a permission the
 * platform does not define returns a denial the user was never shown. Below 33 the ask is not
 * applicable, so the answer is reported directly and the latch is written once, from the same
 * intent — never before an ask (delta 2).
 *
 * **PENDING OWNER DECISION 4 — how assertive the app is outside itself.** `POST_NOTIFICATIONS` is
 * declared in **no** manifest in this project: `:data`'s says so in as many words, listing it beside
 * `PACKAGE_USAGE_STATS`, `SYSTEM_ALERT_WINDOW` and `RECEIVE_BOOT_COMPLETED` as permissions that "sit
 * behind a pending owner decision. Nothing here declares a permission on a decision's behalf."
 * Until that decision lands the launcher below resolves as an immediate denial, which is the
 * conservative outcome and blocks nothing: the flow settles and continues.
 *
 * The shape is deliberately left ready. When the decision is taken, ONE line —
 * `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` in
 * `feature/onboarding/src/main/AndroidManifest.xml` — turns the ask on, and nothing in this file,
 * the contract or the ViewModel changes.
 */
private inline fun requestNotifications(launch: () -> Unit, onNotApplicable: () -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launch() else onNotApplicable()
}

/**
 * The opaque handle of `docs/screens/10-splash-and-onboarding.md` §4, carrying the `Activity` the
 * Google UMP form would present itself over.
 *
 * `platformHost` is `Any` so `:domain` sees no Android type (`LLM.md` §2), and it is the same
 * deliberate shape as `PendingIntentToken` (`LLM.md` §12): nothing above `:data` calls a method on
 * it. §4 says "implemented in `:app`" — it is implemented here instead because the Route is the one
 * composable that has an `Activity` and raises the intent, and moving it to `:app` would put a type
 * in the assembly module that only this screen constructs.
 */
private class ActivityConsentHost(activity: Activity?) : ConsentHost {
    override val platformHost: Any = activity ?: Unit
}
