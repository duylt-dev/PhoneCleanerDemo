package com.pion.phonecleaner.feature.settings.permissioncentre

import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.repository.PermissionRepository
import kotlinx.collections.immutable.toImmutableList

/**
 * `permissioncentre` (`docs/screens/20-settings-language-and-push.md` §5.2).
 *
 * **Forbidden here:** building an `Intent`, touching an `Activity`, calling any permission library.
 * The ViewModel names a permission; the Route performs the launch. That is also why the four `nc.*`
 * `AlertDialog` subclasses become one nullable field on state: a dialog is state, what its confirm
 * button asks the **system** for is an Effect (`LLM.md` §7.4).
 *
 * ### No analytics call
 *
 * §5.2 asks for "the analytics id" to be logged on `RationaleConfirmed`, citing
 * `docs/reverse-engineering/02-cross-cutting-permissions-storage-analytics.md` §4. **UNKNOWN** — no
 * `AnalyticsEvent` arm covers a permission, and the six that exist (`FeatureOpened`,
 * `ExitOfferShown`, `CleanRequested`, `JunkScanFinished`, `JunkScanCancelled`, `JunkCleanFinished`)
 * carry no id this event could reuse. `AnalyticsRepository` is not this cluster's file, and a
 * fabricated numeric id is exactly the defect that produced the competitor's `100525`/`100526`
 * transposition. `AnalyticsRepository` is therefore not injected at all: an unused dependency reads
 * as a call that was forgotten.
 *
 * ### Why `init` refreshes rather than observes only
 *
 * `PermissionRepository.observe()` re-emits when the **process** returns to the foreground, which is
 * the funnel's outer loop. A grant made *inside* the app — a runtime dialog — produces no lifecycle
 * transition, so [PermissionCentreIntent.PermissionResultReceived] carries that answer directly. Two
 * paths, one reducer, and neither invents the other's result.
 */
class PermissionCentreViewModel(
    private val permissions: PermissionRepository,
    log: AppLogger = AppLogger.NoOp,
) : MviViewModel<PermissionCentreState, PermissionCentreIntent, PermissionCentreEffect>(
    PermissionCentreState(),
    log,
) {

    init {
        setState { copy(cards = snapshot(), isRefreshing = false) }

        // A structural child of viewModelScope; there is no Job field to cancel by hand.
        permissions.observe().collectSafely { setState { copy(cards = snapshot()) } }
    }

    override fun onIntent(intent: PermissionCentreIntent) {
        when (intent) {
            // Idempotent and short, so a second one need not cancel the first (§5.2).
            PermissionCentreIntent.ScreenResumed -> refresh()
            is PermissionCentreIntent.CardTapped -> onCardTapped(intent.permission)
            PermissionCentreIntent.RationaleConfirmed -> onRationaleConfirmed()
            PermissionCentreIntent.RationaleDismissed -> setState { copy(rationaleFor = null) }
            is PermissionCentreIntent.PermissionResultReceived ->
                onResult(intent.permission, intent.granted)

            PermissionCentreIntent.BackPressed -> sendEffect(PermissionCentreEffect.NavigateBack)
        }
    }

    /**
     * The reads (`Environment.isExternalStorageManager`, `AppOpsManager`, `NotificationManagerCompat`)
     * are cheap but none is documented main-safe; they are made inside the repository, which is where
     * the dispatcher choice belongs (`LLM.md` §6.5). `isGranted` is deliberately non-suspending
     * because it is read from a reducer, and a reducer neither suspends nor waits.
     */
    private fun refresh() {
        setState { copy(isRefreshing = true) }
        launchSafely(onError = { setState { copy(isRefreshing = false) } }) {
            setState { copy(cards = snapshot(), isRefreshing = false) }
        }
    }

    /**
     * Preserves [PermissionCard.wasDeclined] across a refresh: it is the screen's memory of a round
     * trip, not a platform reading, and a re-read must not erase it. A grant clears it, because a
     * granted permission was plainly not declined.
     */
    private fun PermissionCentreState.snapshot() = PermissionCentreCatalog.managed
        .map { permission ->
            val isGranted = permissions.isGranted(permission)
            PermissionCard(
                permission = permission,
                isGranted = isGranted,
                wasDeclined = !isGranted && cards.wasDeclined(permission),
            )
        }
        .toImmutableList()

    private fun onCardTapped(permission: AppPermission) {
        if (currentState.cards.firstOrNull { it.permission == permission }?.isGranted == true) return
        if (PermissionCentreCatalog.needsRationale(permission)) {
            setState { copy(rationaleFor = permission) }
        } else {
            sendEffect(PermissionCentreEffect.RequestRuntimePermission(permission))
        }
    }

    private fun onRationaleConfirmed() {
        val permission = currentState.rationaleFor ?: return
        setState { copy(rationaleFor = null) }
        sendEffect(
            when (PermissionCentreCatalog.kindOf(permission)) {
                PermissionCentreCatalog.Kind.Runtime ->
                    PermissionCentreEffect.RequestRuntimePermission(permission)

                PermissionCentreCatalog.Kind.SpecialAccess ->
                    PermissionCentreEffect.OpenSystemSettings(permission)
            },
        )
    }

    private fun onResult(permission: AppPermission, granted: Boolean) = setState {
        copy(
            cards = cards
                .map { card ->
                    if (card.permission != permission) {
                        card
                    } else {
                        card.copy(isGranted = granted, wasDeclined = !granted)
                    }
                }
                .toImmutableList(),
        )
    }
}

private fun List<PermissionCard>.wasDeclined(permission: AppPermission): Boolean =
    firstOrNull { it.permission == permission }?.wasDeclined == true
