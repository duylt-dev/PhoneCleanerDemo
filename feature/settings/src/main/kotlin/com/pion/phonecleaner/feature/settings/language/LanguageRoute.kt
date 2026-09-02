package com.pion.phonecleaner.feature.settings.language

import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import com.pion.phonecleaner.core.ui.error.messageRes
import com.pion.phonecleaner.core.ui.token.PageSpacing
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Callbacks in, nothing out (`LLM.md` §7.1).
 *
 * ### The one platform call, and why it lives here
 *
 * `AppCompatDelegate.setApplicationLocales` is **the** reason `androidx.appcompat` is in the version
 * catalogue, and the catalogue entry says so. It replaces the competitor's save path, which is
 * `AppUtils.relaunchApp(killProcess = true)` → `Process.killProcess` + `System.exit(0)` **after** a
 * second library has already fired the launcher intent
 * (`docs/screens/20-settings-language-and-push.md` §2.4 delta 1). That destroys the back stack, drops
 * every in-flight coroutine, re-runs `Application.onCreate`, and on Android 12+ a self-restart from a
 * background-ish state is not guaranteed to land. The framework instead recreates the Activity in
 * place and persists the choice itself.
 *
 * An empty `LocaleListCompat` is the *System default* row: it clears the override, which is the call
 * the competitor ships (`n7.b.a(ctx)`) and never makes (§2.4 delta 3).
 *
 * UNKNOWN — **whether the change is applied without a process restart below API 33.** From API 33
 * the framework owns the locale and recreates the Activity itself. Below 33 the appcompat backport
 * persists the value and then recreates **AppCompat** activities; `MainActivity` is a plain
 * `ComponentActivity` (`app/src/main/kotlin/com/pion/phonecleaner/MainActivity.kt:21`), so on API
 * 28-32 the choice is stored and may not repaint until the next launch. The call is still the right
 * one — it is the only API that persists the choice for the framework — and the two candidate fixes
 * (an `AppCompatActivity` host, or an explicit `recreate()` here) both change files this cluster does
 * not own. Looked for a stated position in `docs/screens/20-settings-language-and-push.md` §2.3 and
 * §8 open item 3, which fix the call and the `minSdk` and say nothing about the host type.
 *
 * UNKNOWN — the per-app entry in the **system** Settings app needs API 33 plus
 * `res/xml/locales_config.xml` and `android:localeConfig` on `<application>`, and `:app` is not this
 * cluster's module (§8 open item 3). `minSdk` is 28, so `setApplicationLocales` is called for
 * everyone and the appcompat backport applies it below 33; the system-Settings entry appears only
 * once `:app` ships that file. Looked for it in `app/src/main/AndroidManifest.xml` and
 * `app/src/main/res`, where neither exists today.
 */
@Composable
fun LanguageRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LanguageViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is LanguageEffect.ApplyLocale -> AppCompatDelegate.setApplicationLocales(
                effect.tag?.let(LocaleListCompat::forLanguageTags)
                    ?: LocaleListCompat.getEmptyLocaleList(),
            )

            LanguageEffect.NavigateBack -> onNavigateBack()

            // Resolved from the effect's own payload, never from state: the collector runs one
            // main-queue turn after sendEffect and a frame before the matching setState renders.
            // Not awaited — showSnackbar suspends until dismissal, which would queue the next effect.
            is LanguageEffect.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(context.getString(effect.error.messageRes()))
            }
        }
    }

    BackHandler { onIntent(LanguageIntent.BackPressed) }

    Box(modifier.fillMaxSize()) {
        LanguageScreen(state = state, onIntent = onIntent)
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = PageSpacing.snackbarLift),
        )
    }
}
