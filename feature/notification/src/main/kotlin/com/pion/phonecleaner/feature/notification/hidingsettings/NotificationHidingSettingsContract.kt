package com.pion.phonecleaner.feature.notification.hidingsettings

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.notification.NotificationHidingApp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * `hidingsettings` — master and per-app switches
 * (`docs/screens/17-notification-and-permissions.md` §2.1).
 *
 * It replaces `InteencActivity` + `Extambe` + `ud.b`/`ud.c` + `vd.d.a()`. The competitor has **no
 * Activity fields at all** on this screen — its adapter's backing list is the model — so the only thing
 * folded is `appsNotiHideEnableInfoObser`, into [NotificationHidingSettingsState.apps]. `isLoading` and
 * `isEmpty` are new: the competitor has no loading, empty or error state, and a `PackageManager` throw
 * escapes into its coroutine, leaving the screen blank forever (§2.5).
 *
 * [NotificationHidingSettingsState.togglingPackages] is a `Set` of package names on `State`, not an
 * `isBusy` field on the row: a mutated item is `equals` its predecessor inside the old list, so no diff
 * can see it (`LLM.md` §8).
 */
data class NotificationHidingSettingsState(
    val isLoading: Boolean = true,
    /** Parity: the competitor's default is on. The store answers the same way when the key is absent. */
    val isMasterEnabled: Boolean = true,
    val apps: ImmutableList<NotificationHidingApp> = persistentListOf(),
    val togglingPackages: ImmutableSet<String> = persistentSetOf(),
    val error: AppError? = null,
) : UiState {
    val isEmpty: Boolean get() = !isLoading && apps.isEmpty()
    val enabledCount: Int get() = apps.count { it.isHidingEnabled }
}

sealed interface NotificationHidingSettingsIntent : UiIntent {
    data class MasterToggled(val enabled: Boolean) : NotificationHidingSettingsIntent

    data class AppToggled(
        val packageName: String,
        val enabled: Boolean,
    ) : NotificationHidingSettingsIntent

    data object RetryTapped : NotificationHidingSettingsIntent

    data object BackPressed : NotificationHidingSettingsIntent
}

/**
 * `NavigateToHiddenList` and `NavigateToDisabledWall` are deliberately **absent** (§2.1).
 *
 * The competitor's Back forward-navigates, and calls the router *before* `super`, so both transitions
 * play — Back does not go back. Here Back pops, and the hidden-list route observes the master switch
 * itself and renders its own paused banner, so nothing needs re-routing (§2.5).
 */
sealed interface NotificationHidingSettingsEffect : UiEffect {
    data object NavigateBack : NotificationHidingSettingsEffect

    data class ShowMessage(val error: AppError) : NotificationHidingSettingsEffect
}
