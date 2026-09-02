package com.pion.phonecleaner.feature.network.traffic

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.domain.model.network.TrafficReport
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf

/**
 * The pure transitions of [NetworkTrafficState], kept out of the ViewModel so the ViewModel file is
 * the *policy* and this one is the *arithmetic* (`LLM.md` §3.7, the shape the files cluster uses).
 *
 * **Every one of them lowers or sets [NetworkTrafficState.phase] explicitly.** The one failure mode
 * a phase enum has is stranding, and it strands when a path forgets to leave the busy state.
 */

internal fun NetworkTrafficState.withAccessMissing(): NetworkTrafficState = copy(
    phase = NetworkTrafficState.Phase.NeedsUsageAccess,
    // The wall is not a place to keep a half-finished round trip, or a report from a grant that
    // has since been revoked.
    report = null,
    labels = persistentMapOf(),
    stopRequestedFor = null,
    error = null,
)

internal fun NetworkTrafficState.withLoadStarted(): NetworkTrafficState = copy(
    phase = NetworkTrafficState.Phase.Scanning,
    error = null,
    stopRequestedFor = null,
)

internal fun NetworkTrafficState.withReport(
    report: TrafficReport,
    labels: ImmutableMap<String, String>,
    error: AppError?,
): NetworkTrafficState = copy(
    phase = NetworkTrafficState.Phase.Ready,
    report = report,
    labels = labels,
    error = error,
)

/**
 * A failure keeps whatever was already on screen: a refused refresh of a list the user is reading
 * should not blank it. The card and its *Try again* appear above the rows.
 */
internal fun NetworkTrafficState.withFailure(error: AppError): NetworkTrafficState = copy(
    phase = NetworkTrafficState.Phase.Ready,
    error = error,
)

internal fun NetworkTrafficState.withStopRequested(packageName: String): NetworkTrafficState =
    copy(stopRequestedFor = packageName)
