package com.pion.phonecleaner.feature.settings.permissioncentre

import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.permission.AppPermission
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * One row of the centre. `@Immutable`, and the enum makes the list item stable.
 *
 * **Selection-style state is not on the model:** whether the card is granted is a fact read from the
 * platform, and [wasDeclined] is the screen's own memory of a round trip. Neither is a mutable field
 * anyone writes in place, which is what `ae.k1` does across the language list (`LLM.md` §8).
 */
@Immutable
data class PermissionCard(
    val permission: AppPermission,
    val isGranted: Boolean,
    /**
     * True once the user was sent to a system screen or dialog and came back without granting. It
     * drives the rationale copy. The competitor has no such concept — it cannot, because a granted
     * card is simply `GONE` and a refused one is indistinguishable from an unasked one.
     */
    val wasDeclined: Boolean = false,
)

/**
 * `permissioncentre` (`docs/screens/20-settings-language-and-push.md` §5.1). Replaces
 * `GerraphiActivity` — "Authority Management".
 *
 * That Activity has **no fields at all**: its entire state is which of four `RelativeLayout`s is
 * `VISIBLE`, read from the OS in `z()` and written from four separate grant callbacks. Four callbacks
 * and four `setVisibility(GONE)` calls become one list and one intent.
 *
 * **Not `PermissionManager`.** That route is cluster 17's per-app permission browser and takes a
 * `tab` argument. Two different screens, adjacent names (§0).
 */
@Immutable
data class PermissionCentreState(
    val cards: ImmutableList<PermissionCard> = persistentListOf(),

    /**
     * Which permission's rationale sheet is open. **An id — never a `Dialog` handle** (`LLM.md`
     * §7.4). All eighteen of the competitor's dialogs vanish on rotation, structurally.
     */
    val rationaleFor: AppPermission? = null,
    val isRefreshing: Boolean = false,
) : UiState {

    val missing: ImmutableList<PermissionCard>
        get() = cards.filterNot { it.isGranted }.toImmutableList()

    /**
     * A granted permission stays **visible**. The competitor sets a granted card to `GONE`; grant all
     * four and the page holds nothing but an ad slot, so the user can neither verify what they
     * granted nor find their way back to revoke it (§5.4 delta 2).
     */
    val granted: ImmutableList<PermissionCard>
        get() = cards.filter { it.isGranted }.toImmutableList()

    val isAllGranted: Boolean get() = cards.isNotEmpty() && missing.isEmpty()

    /** The card the open sheet is about, or `null`. Resolved from the id, never stored twice. */
    val rationaleCard: PermissionCard?
        get() = rationaleFor?.let { wanted -> cards.firstOrNull { it.permission == wanted } }
}

sealed interface PermissionCentreIntent : UiIntent {
    /**
     * Sent from `ON_RESUME`, every time. **This is the whole fix for delta 1**: `c0()` runs only from
     * `z()` and the competitor overrides no `onResume`, so granting in system Settings and pressing
     * back leaves the stale card on screen — while its App Lock screen does re-check, so the app
     * contradicts itself.
     */
    data object ScreenResumed : PermissionCentreIntent
    data class CardTapped(val permission: AppPermission) : PermissionCentreIntent
    data object RationaleConfirmed : PermissionCentreIntent
    data object RationaleDismissed : PermissionCentreIntent
    data class PermissionResultReceived(
        val permission: AppPermission,
        val granted: Boolean,
    ) : PermissionCentreIntent
    data object BackPressed : PermissionCentreIntent
}

sealed interface PermissionCentreEffect : UiEffect {
    /** The ViewModel names a permission; the Route performs the launch (§5.2, forbidden list). */
    data class RequestRuntimePermission(val permission: AppPermission) : PermissionCentreEffect
    data class OpenSystemSettings(val permission: AppPermission) : PermissionCentreEffect
    data object NavigateBack : PermissionCentreEffect
}
