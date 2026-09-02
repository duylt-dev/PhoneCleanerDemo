package com.pion.phonecleaner.feature.network.traffic

import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.network.TrafficFilter
import com.pion.phonecleaner.domain.model.network.TrafficPeriod
import com.pion.phonecleaner.domain.model.network.TrafficReport
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList

/**
 * `traffic` (`docs/screens/19-network-and-speed-test.md` §1.1).
 *
 * Folds **three** competitor Activities into one state: the permission wall
 * (`OutwerolleActivity`), the 3 s loading screen (`JerkplushActivity`) and the list
 * (`LawsustincActivity`). Their twelve mutable fields, two booleans and an `isNavigating` latch
 * collapse into [phase] plus five values.
 *
 * There is no `isNavigating`: navigation is an Effect delivered exactly once by a `Channel`, so the
 * four-callbacks-one-navigation race that latch exists for cannot arise.
 *
 * There is no progress figure either. The competitor renders a 3 s animation over a query it then
 * discards; a determinate bar driven by a timer is a number nobody measured, and this project does
 * not ship those. `Scanning` renders an indeterminate indicator.
 */
@Immutable
data class NetworkTrafficState(
    val phase: Phase = Phase.CheckingAccess,

    // ── filters. Both survive process death through SavedStateHandle (§1.5 D6). ───────────────
    val period: TrafficPeriod = TrafficPeriod.ThisMonth,
    val filter: TrafficFilter = TrafficFilter.All,

    /**
     * The raw report. Filtering, sorting and bar lengths are DERIVED from it — never stored a second
     * time. The competitor keeps the result, an `appList` and the adapter's copy of `appList`: three
     * records of one fact, and its filter change rebuilds all three.
     */
    val report: TrafficReport? = null,

    /** Package name → display label, resolved once per report through `InstalledAppsRepository`. */
    val labels: ImmutableMap<String, String> = persistentMapOf(),

    /**
     * Set from the tap on *App info* until `ON_START` brings the user back. An id, never the row
     * object. The competitor holds a `shouldShowForceStopHint` flag and a delayed `Runnable`.
     */
    val stopRequestedFor: String? = null,

    val error: AppError? = null,
) : UiState {

    /** Deliberately not `ScanPhase` — that short name is retired (`docs/system-architecture.md` §4.5). */
    enum class Phase { CheckingAccess, NeedsUsageAccess, Scanning, Ready }

    /**
     * One row per **UID**, largest first, with the bar length as a fraction of the largest row.
     *
     * A UID whose packages resolve to no label is dropped rather than rendered as a raw package id
     * (§1.5 D4). The competitor includes it and labels it with the id — the guard it wrote is
     * `if (ai == null || !isSystem(ai))`, which admits exactly the rows it meant to exclude.
     *
     * Computed, so it cannot drift from [report]. Callers hold it in a `remember` keyed on the three
     * inputs rather than reading it once per recomposition.
     */
    val rows: ImmutableList<TrafficRow>
        get() {
            val scored = report?.apps.orEmpty().mapNotNull { app ->
                val bytes = app.bytesFor(filter)
                if (bytes <= 0L) return@mapNotNull null
                val named = app.packageNames.firstOrNull(labels::containsKey)
                    ?: return@mapNotNull null
                Triple(named, labels.getValue(named), bytes)
            }.sortedByDescending { it.third }

            val largest = scored.firstOrNull()?.third ?: 0L
            return scored.map { (packageName, label, bytes) ->
                TrafficRow(
                    packageName = packageName,
                    label = label,
                    bytes = bytes,
                    fractionOfLargest = if (largest > 0L) {
                        (bytes.toDouble() / largest.toDouble()).toFloat().coerceIn(0f, 1f)
                    } else {
                        0f
                    },
                )
            }.toImmutableList()
        }

    /** Device totals for the period, over every UID the query returned. */
    val mobileTotalBytes: Long get() = report?.mobileBytes ?: 0L
    val wifiTotalBytes: Long get() = report?.wifiBytes ?: 0L

    val isReady: Boolean get() = phase == Phase.Ready
    val isBusy: Boolean get() = phase == Phase.CheckingAccess || phase == Phase.Scanning
}

/**
 * One rendered row. [fractionOfLargest] is a **bar length**, `0f..1f`, and is never shown as a
 * number: `LLM.md` §1 bans percentage figures, and a share-of-the-largest-app figure is not a fact
 * about the user's device that any copy could state honestly.
 */
@Immutable
data class TrafficRow(
    val packageName: String,
    val label: String,
    val bytes: Long,
    val fractionOfLargest: Float,
)

sealed interface NetworkTrafficIntent : UiIntent {
    /** Every `ON_START`, including the return from Settings — no app is handed a result for that trip. */
    data object ScreenStarted : NetworkTrafficIntent
    data object GrantAccessPressed : NetworkTrafficIntent
    data class SelectPeriod(val period: TrafficPeriod) : NetworkTrafficIntent
    data class SelectFilter(val filter: TrafficFilter) : NetworkTrafficIntent

    /** The row's *App info* action. See [NetworkTrafficEffect.OpenAppDetails] for what it does not do. */
    data class StopPressed(val packageName: String) : NetworkTrafficIntent
    data object DonePressed : NetworkTrafficIntent
    data object BackPressed : NetworkTrafficIntent
    data object RetryPressed : NetworkTrafficIntent
}

sealed interface NetworkTrafficEffect : UiEffect {
    /** The Route owns the launcher; the ViewModel only asks. */
    data object OpenUsageAccessSettings : NetworkTrafficEffect

    /**
     * Opens the system App info page for one package.
     *
     * The competitor pairs this with `ActivityManager.killBackgroundProcesses`, whose permission it
     * never declares, so **every** call throws a `SecurityException` it swallows (§1.5 D5). No such
     * call is made here: there is no public API that stops another app, and a button that pretends to
     * is worse than one that hands the user the page that can.
     */
    data class OpenAppDetails(val packageName: String) : NetworkTrafficEffect
    data object NavigateBack : NetworkTrafficEffect
}
