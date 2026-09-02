package com.pion.phonecleaner.feature.settings.language

import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.settings.AppLanguage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * `language` (`docs/screens/20-settings-language-and-push.md` §2.1). Replaces `VentioActivity`.
 *
 * The competitor's four Activity fields — `languageAdapter`, `allLangList`, `chooseLang`,
 * `chooseCountry` — collapse to [languages] plus [selectedTag]. **`isChoosed` leaves the model**:
 * `ae.k1.isChoosed` is a `public var` mutated in place on the elements of a process-global `Lazy`
 * list, and `ae.t.J()` resets every element on each entry (`ae/t.java:236-243`) — a screen writing to
 * a process singleton in order to draw a radio button (§2.4 delta 8). One nullable `String` replaces
 * all of it, and the list is genuinely immutable.
 *
 * `isSystemLanguageUnsupported` from the research draft is dropped: it existed to surface the
 * competitor's silent "no radio filled" case, and with a real *System default* row that case cannot
 * arise, because `null` is a selectable value with a visible row (§2.1).
 */
@Immutable
data class LanguageState(
    val languages: ImmutableList<AppLanguage> = persistentListOf(),

    /**
     * The pending selection. `null` means "follow the system" — a row the competitor does not offer
     * at all, because nothing ever calls the library method that clears its override
     * (`n7/b.java:17-20`, §2.4 delta 3).
     */
    val selectedTag: String? = null,

    /** What was applied when the screen opened, so Save can be disabled when nothing changed. */
    val appliedTag: String? = null,

    /** True while the write is in flight. **Both** outcome arms lower it (§2.2). */
    val isApplying: Boolean = false,
    val error: AppError? = null,
) : UiState {

    val isDirty: Boolean get() = selectedTag != appliedTag

    /**
     * The competitor's Save is always enabled and silently no-ops when nothing was picked
     * (`if (chooseLang.isEmpty()) return`) — a dead-looking button. A disabled button cannot no-op
     * (§2.4 delta 6).
     */
    val canSave: Boolean get() = isDirty && !isApplying
}

sealed interface LanguageIntent : UiIntent {
    /** `null` selects *System default*. */
    data class LanguageSelected(val tag: String?) : LanguageIntent
    data object SaveTapped : LanguageIntent
    data object BackPressed : LanguageIntent
}

sealed interface LanguageEffect : UiEffect {
    /**
     * The only correct "apply a locale" signal. The Route calls `AppCompatDelegate` — a platform
     * call, so it cannot live in a ViewModel (§2.3, §2.4 delta 1).
     */
    data class ApplyLocale(val tag: String?) : LanguageEffect
    data object NavigateBack : LanguageEffect

    /** Carries the error; the collector never reads `state.error`, which is the pre-failure value. */
    data class ShowMessage(val error: AppError) : LanguageEffect
}
