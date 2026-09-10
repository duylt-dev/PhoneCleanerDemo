package com.pion.phonecleaner.feature.settings.settings

import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.repository.AppInfoProvider
import com.pion.phonecleaner.domain.repository.LanguageRepository
import com.pion.phonecleaner.domain.repository.PermissionRepository
import com.pion.phonecleaner.domain.repository.ResidentWidgetSettingsRepository
import com.pion.phonecleaner.domain.usecase.ObserveTrashSummaryUseCase
import com.pion.phonecleaner.feature.settings.permissioncentre.PermissionCentreCatalog

/**
 * `settings` (`docs/screens/20-settings-language-and-push.md` §1.2).
 *
 * `init` observes; it does not act. Four `collectSafely` collectors, each folding into one
 * `setState`, all structural children of `viewModelScope` — **no `Job` fields**. `ScreenResumed`
 * therefore does nothing: the flows already re-emit, and an explicit refresh would be a second source
 * of truth.
 *
 * No dispatcher is chosen here. The one preference write goes to `dispatchers.io` **inside** the
 * repository (`LLM.md` §6.5).
 */
class SettingsViewModel(
    private val languageRepository: LanguageRepository,
    appInfo: AppInfoProvider,
    private val widgetSettings: ResidentWidgetSettingsRepository,
    private val permissions: PermissionRepository,
    observeTrashSummary: ObserveTrashSummaryUseCase,
    log: AppLogger = AppLogger.NoOp,
) : MviViewModel<SettingsState, SettingsIntent, SettingsEffect>(
    SettingsState(
        appName = appInfo.appName,
        versionName = appInfo.versionName,
        versionCode = appInfo.versionCode,
    ),
    log,
) {

    init {
        languageRepository.currentLanguage().collectSafely { language ->
            setState { copy(currentLanguage = language) }
        }
        widgetSettings.isEnabled().collectSafely { enabled ->
            setState { copy(isResidentWidgetEnabled = enabled) }
        }
        // The badge counts exactly the rows the centre draws, read off the same roster the centre
        // uses — two lists would disagree the first time either changed.
        permissions.observe().collectSafely { granted ->
            val missing = PermissionCentreCatalog.managed.count { it !in granted }
            setState { copy(missingPermissionCount = missing) }
        }
        observeTrashSummary().collectSafely { summary ->
            setState { copy(trashSummary = summary) }
        }
    }

    override fun onIntent(intent: SettingsIntent) {
        when (intent) {
            SettingsIntent.ScreenResumed -> Unit
            SettingsIntent.LanguageRowTapped -> sendEffect(SettingsEffect.NavigateToLanguage)
            SettingsIntent.AboutRowTapped -> sendEffect(SettingsEffect.NavigateToAbout)
            SettingsIntent.PermissionCentreRowTapped ->
                sendEffect(SettingsEffect.NavigateToPermissionCentre)

            SettingsIntent.TrashRowTapped -> sendEffect(SettingsEffect.NavigateToTrash)
            is SettingsIntent.ResidentWidgetToggled -> setResidentWidget(intent.enabled)
            SettingsIntent.BackPressed -> sendEffect(SettingsEffect.NavigateBack)
        }
    }

    /**
     * The state change arrives back through the collector, so the screen never holds an optimistic
     * copy a failed write could contradict (§1.2).
     */
    private fun setResidentWidget(enabled: Boolean) {
        launchSafely { widgetSettings.setEnabled(enabled) }
    }
}
