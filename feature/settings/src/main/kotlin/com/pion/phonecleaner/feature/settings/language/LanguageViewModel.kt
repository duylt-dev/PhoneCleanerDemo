package com.pion.phonecleaner.feature.settings.language

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.repository.LanguageRepository

/**
 * `language` (`docs/screens/20-settings-language-and-push.md` §2.2).
 *
 * **Forbidden here:** `Locale`, `Configuration`, `Resources`, `AppCompatDelegate` — every one of them
 * a platform type (MVI §8). Applying the locale is `LanguageEffect.ApplyLocale`, performed by the
 * Route. Nothing in this file restarts the process; the competitor calls
 * `AppUtils.relaunchApp(killProcess = true)` twice per save (§2.4 delta 1).
 *
 * `init` observes and does not act: one `setState` from the constant roster, then one collector on
 * the applied language. Both are structural children of `viewModelScope`, so there is **no `Job`
 * field** to cancel by hand.
 */
class LanguageViewModel(
    private val languageRepository: LanguageRepository,
    log: AppLogger = AppLogger.NoOp,
) : MviViewModel<LanguageState, LanguageIntent, LanguageEffect>(LanguageState(), log) {

    init {
        // A pure constant list — no IO, so no launch and no loading state.
        setState { copy(languages = languageRepository.supportedLanguages()) }

        languageRepository.currentLanguage().collectSafely(onError = ::onFailure) { applied ->
            setState {
                copy(
                    appliedTag = applied?.tag,
                    // The user's pending choice wins over a re-emission: a write to any other key in
                    // the shared store must not discard a selection they have not saved yet.
                    selectedTag = if (isDirty) selectedTag else applied?.tag,
                )
            }
        }
    }

    override fun onIntent(intent: LanguageIntent) {
        when (intent) {
            // Nothing is persisted; Back discards — the same semantics as the competitor's N(...).
            is LanguageIntent.LanguageSelected -> setState { copy(selectedTag = intent.tag) }
            LanguageIntent.SaveTapped -> save()
            LanguageIntent.BackPressed -> sendEffect(LanguageEffect.NavigateBack)
        }
    }

    /**
     * The write is **awaited** before the Effect is raised. The competitor's `od.d0.l` uses
     * `commit()` — a synchronous fsync on the main thread, twice — immediately before killing the
     * process (§2.4 delta 7); a suspending `DataStore` write that has landed is what makes the locale
     * survive the recreation `setApplicationLocales` triggers.
     */
    private fun save() {
        if (!currentState.canSave) return
        val tag = currentState.selectedTag
        setState { copy(isApplying = true, error = null) }
        launchSafely(onError = ::onFailure) {
            when (val result = languageRepository.setLanguage(tag)) {
                is AppResult.Failure -> onFailure(result.error)
                is AppResult.Success -> {
                    setState { copy(isApplying = false, appliedTag = tag) }
                    sendEffect(LanguageEffect.ApplyLocale(tag))
                }
            }
        }
    }

    /** Lowers the in-flight flag on the third path too — the one no `AppResult` arm reaches. */
    private fun onFailure(error: AppError) {
        setState { copy(isApplying = false, error = error) }
        sendEffect(LanguageEffect.ShowMessage(error))
    }
}
