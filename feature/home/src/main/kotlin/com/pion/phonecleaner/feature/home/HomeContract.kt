package com.pion.phonecleaner.feature.home

import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.model.settings.LegalDocument
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * One immutable object folds the competitor's nine mutable Activity fields, its two statics and the
 * four values it re-derives on every `onResume` (`docs/screens/11-home.md` §1.1).
 */
@Immutable
data class StorageUsage(val totalBytes: Long = 0L, val freeBytes: Long = 0L) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)

    /**
     * Storage **occupancy**, which is a measurement, not a performance claim (`docs/screens/11-home.md`
     * §4.2). 0 when the total is unknown.
     */
    val usedPercent: Int get() = if (totalBytes > 0) ((usedBytes * 100f) / totalBytes).toInt() else 0
}

/**
 * Modelled, not inferred at render time: "we have not looked" and "there is nothing" must not render
 * identically, which is what the competitor's two booleans plus a `-1` sentinel produce (delta 13).
 */
@Immutable
sealed interface JunkPill {
    data object NotMeasured : JunkPill
    data object Clean : JunkPill
    data class Measured(val bytes: Long) : JunkPill
}

/** A dialog is a visible condition, so it is State and survives rotation — never an Effect. */
@Immutable
sealed interface HomeDialog {
    data object NotificationPermission : HomeDialog
    data object AntivirusConsent : HomeDialog

    /** Holds an id, never a `FeatureDescriptor`: a locale change must re-render the open dialog. */
    data class ExitOffer(val feature: FeatureId) : HomeDialog
}

data class HomeState(
    // header
    val showPermissionWarning: Boolean = false,
    // hero card
    val storage: StorageUsage = StorageUsage(),
    /** From `JunkRepository.cachedJunkBytes(): Flow<Long?>`, mapped by `Long?.toJunkPill()`. */
    val junkPill: JunkPill = JunkPill.NotMeasured,
    val isJunkEstimating: Boolean = false,
    /** UNKNOWN — `CleanStatsRepository` (`backgroundModule`) is not in `:domain/repository/` yet. */
    val needsSecurityScanToday: Boolean = false,
    // banner
    val showNotificationBanner: Boolean = false,
    // grid
    val sections: ImmutableList<HomeSection> = persistentListOf(),
    /** `null` renders `"--"`. The honest rendering of "not sampled"; never a zero (delta 4). */
    val downloadBytesPerSecond: Long? = null,
    // footer
    /** UNKNOWN — `AppInfoProvider` (`settingsDataModule`) is not in `:domain/repository/` yet. */
    val daysInstalled: Int = 1,
    val lifetimeSavedBytes: Long = 0L,
    // dialogs
    val dialog: HomeDialog? = null,
    // one-shot gates the competitor keeps as Activity booleans
    val hasOfferedNotificationSheet: Boolean = false,
    val arrivedFromNotification: Boolean = false,
    val pendingDeepLink: FeatureId? = null,
    /**
     * NOT IN THE APPENDIX, and required by it: §2 says the consent must be persisted **before**
     * `NavigateToFeature`, and the appendix's own §4.3 item 1 records that the repository which would
     * persist it (`AppSettingsRepository`) has no declared owner. Session-scoped here so the gate's
     * shape ships without closing that decision; nothing writes it to storage.
     */
    val hasAcceptedSecurityConsent: Boolean = false,
    /**
     * NOT IN THE APPENDIX, and required by it: `PermissionResolved(permission, granted)` carries no
     * feature, so the reducer cannot re-enter the gate it raised without remembering which tile asked.
     * On State rather than a private field so it survives process death like every other gate.
     */
    val permissionGate: FeatureId? = null,
    val error: AppError? = null,
) : UiState {
    /** Derived, never stored: two fields that can disagree eventually will. */
    val isExitDialogShowing: Boolean get() = dialog is HomeDialog.ExitOffer

    val isBusy: Boolean get() = isJunkEstimating
}

sealed interface HomeIntent : UiIntent {
    // lifecycle — they change behaviour, so they are intents
    data object ScreenStarted : HomeIntent
    data object ScreenResumed : HomeIntent
    data object ScreenStopped : HomeIntent

    // user actions
    data class FeatureTapped(val feature: FeatureId) : HomeIntent
    data object CleanTapped : HomeIntent
    data object SettingsTapped : HomeIntent
    data object PermissionWarningTapped : HomeIntent
    data object NotificationBannerTapped : HomeIntent
    data object BackPressed : HomeIntent

    // dialog answers
    data class NotificationSheetAnswered(val accepted: Boolean) : HomeIntent
    data class SecurityConsentAnswered(val accepted: Boolean) : HomeIntent
    data class ExitOfferAnswered(val accepted: Boolean) : HomeIntent
    data object ExitOfferDismissed : HomeIntent
    data class LinkTapped(val document: LegalDocument) : HomeIntent

    // platform results, reported upward by the composable
    data class NotificationPermissionResolved(val granted: Boolean) : HomeIntent
    data class PermissionResolved(val permission: AppPermission, val granted: Boolean) : HomeIntent
}

sealed interface HomeEffect : UiEffect {
    // navigation is always an Effect, never a flag in state
    data class NavigateToFeature(val feature: FeatureId) : HomeEffect
    data object NavigateToSettings : HomeEffect
    data object NavigateToPermissionCentre : HomeEffect
    data object ExitApp : HomeEffect

    // system
    data object RequestNotificationPermission : HomeEffect
    data class RequestPermission(val permission: AppPermission) : HomeEffect
    data class OpenLegalDocument(val document: LegalDocument) : HomeEffect

    /** Carries the payload. Never read back off state — the collector runs a frame early. */
    data class ShowMessage(val error: AppError) : HomeEffect
}
