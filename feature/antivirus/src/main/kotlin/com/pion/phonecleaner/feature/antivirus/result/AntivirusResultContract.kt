package com.pion.phonecleaner.feature.antivirus.result

import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.security.RiskLevel
import com.pion.phonecleaner.domain.model.security.ThreatVerdict
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * The result screen's state (`docs/screens/15-antivirus.md` §2.1).
 *
 * It replaces an Activity with **no ViewModel at all**, whose four pieces of state are fields and
 * whose finding list exists only as a Gson blob in the `Intent` extra of a *finished* Activity
 * (`docs/reverse-engineering/15-antivirus.md` §3.4). Nothing here carries the list across a
 * boundary: `SecurityScanRepository.observeLastResult()` is the only read path, so process death, a
 * rotation and a cold deep link all render the same thing.
 *
 * DELIBERATE OMISSION — no `coverage` field, although §2.1 lists one. `ScanRecord` carries no
 * coverage and says why in its own KDoc (`threat_cache` belongs to `coreDataModule` and has no
 * column for it), and there is no second read path. A field that could only ever hold
 * `ScanCoverage.Unknown` would render as a claim about coverage that nothing supports; the scan
 * screen, which *does* know, is where the note is shown.
 */
@Immutable
data class AntivirusResultState(
    val isLoading: Boolean = true,
    val findings: ImmutableList<ThreatVerdict> = persistentListOf(),
    val scannedAtEpochMs: Long? = null,
    /**
     * The route argument, used for the headline of the enter transition **only** and overwritten by
     * the first emission from the repository (§2.2). It is not a second source of truth: the moment
     * [isLoading] clears, [findingCount] reads the list.
     */
    val expectedCount: Int = 0,
    /** Which row the confirm dialog is asking about. The MD5, never the object (§2.1). */
    val pendingRemovalMd5: String? = null,
    /** Rows whose removal is in flight — a second tap is ignored without freezing the list. */
    val removingMd5s: ImmutableSet<String> = persistentSetOf(),
    /** A failure of the *read*. An action's failure is a `ShowMessage`, which carries its own. */
    val error: AppError? = null,
) : UiState {

    val findingCount: Int get() = if (isLoading) expectedCount else findings.size

    val isEmpty: Boolean get() = !isLoading && findings.isEmpty()

    /**
     * No scan has ever completed on this device — which is **not** the same statement as "nothing
     * was found", and in this build it is the one the user will actually meet: the vendor SDK has
     * no Maven coordinate, so `TrustLookClient` raises `ScanFailure.SdkUnavailable` and no record is
     * ever written (see `data/security/TrustLookClient.kt`).
     */
    val hasNeverScanned: Boolean get() = !isLoading && scannedAtEpochMs == null

    val highCount: Int get() = findings.count { it.risk == RiskLevel.High }

    val elevatedCount: Int get() = findings.count { it.risk == RiskLevel.Elevated }

    /** The headline tint. The competitor hard-codes its red even for a list of low-score rows. */
    val headlineLevel: RiskLevel
        get() = when {
            highCount > 0 -> RiskLevel.High
            elevatedCount > 0 -> RiskLevel.Elevated
            else -> RiskLevel.Clean
        }

    val pendingRemoval: ThreatVerdict?
        get() = pendingRemovalMd5?.let { md5 -> findings.firstOrNull { it.md5 == md5 } }
}

sealed interface AntivirusResultIntent : UiIntent {
    data object ScreenStarted : AntivirusResultIntent
    data class FindingTapped(val md5: String) : AntivirusResultIntent
    data object RemovalConfirmed : AntivirusResultIntent
    data object RemovalDismissed : AntivirusResultIntent
    data class IgnorePressed(val md5: String) : AntivirusResultIntent

    /**
     * Reported by the composable when a `PACKAGE_REMOVED` broadcast lands. **Matched against the
     * list**, and an unmatched broadcast is ignored — the competitor compares nothing and removes
     * whatever its last-tapped field holds, so uninstalling any app anywhere deletes the wrong row.
     */
    data class PackageRemoved(val packageName: String) : AntivirusResultIntent

    /** The user came back from the system uninstall dialog. Lowers that row's in-flight flag. */
    data class UninstallReturned(val md5: String) : AntivirusResultIntent

    data object RescanPressed : AntivirusResultIntent
    data object BackPressed : AntivirusResultIntent
}

sealed interface AntivirusResultEffect : UiEffect {

    /**
     * DELIBERATE DEVIATION from §2.3, which types this `LaunchUninstall(packageName)`. The md5
     * travels too because the launcher's result has to name the row it belongs to, and **a package
     * name is not unique across this list** — §2.3's own reason for keying on the digest: a loose
     * `.apk` and a test-file hit both carry an empty package name.
     */
    data class LaunchUninstall(val md5: String, val packageName: String) : AntivirusResultEffect

    data object NavigateToScan : AntivirusResultEffect
    data object NavigateBack : AntivirusResultEffect

    /**
     * Carries the error. Reading `state` in the collector reads the pre-failure value: the collector
     * runs one main-queue turn after `sendEffect` and a frame before the matching `setState`
     * renders (MVI §4).
     *
     * UNKNOWN — `docs/screens/15-antivirus.md` §2.1 types this `UiText`. **No `UiText` exists** in
     * `:core:mvi`, `:core:ui` or `:core:common` (grepped), and none of those modules is this
     * cluster's to add one to. `AppError` is what the scan screen's twin effect already carries, and
     * `:core:ui`'s `ErrorMessages` maps it to copy at render time.
     */
    data class ShowMessage(val error: AppError) : AntivirusResultEffect
}
