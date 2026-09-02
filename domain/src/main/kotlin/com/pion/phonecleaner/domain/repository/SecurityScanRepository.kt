package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.security.ScanConsentState
import com.pion.phonecleaner.domain.model.security.ScanRecord
import com.pion.phonecleaner.domain.model.security.SecurityScanPhase
import com.pion.phonecleaner.domain.model.security.ThreatVerdict
import kotlinx.coroutines.flow.Flow

/**
 * The seam both antivirus screens see (`docs/screens/15-antivirus.md` §0.4). Declared once, in
 * `securityDataModule`; no other cluster names this type (`LLM.md` §6.4).
 *
 * **The app-side filter lives here**, not in either ViewModel: de-duplicate rows that carry a package
 * name by package name, keep rows that do not, and keep `score >= RiskLevel.ELEVATED_SCORE`. It is a
 * domain rule about what counts as a finding, and both screens need the same answer.
 *
 * Two signature deviations from §0.4, both deliberate:
 *  - `hasConsent(): Boolean` becomes [consent] returning [ScanConsentState] — see that enum's KDoc,
 *    which quotes the two §0.5 requirements a Boolean cannot express.
 *  - the write methods return `AppResult<Unit>` rather than `Unit`: `LLM.md` §4 requires a repository
 *    to return `AppResult` and never to throw across the boundary, and a silently swallowed write is
 *    how the competitor's ignore list would fail invisibly.
 */
interface SecurityScanRepository {

    /**
     * Cold. Collection starts the scan; cancelling the collector cancels the client, which is what
     * makes `withTimeoutOrNull` around the collection a complete cancellation story (§1.2).
     */
    fun scan(): Flow<SecurityScanPhase>

    suspend fun consent(): ScanConsentState

    /** Records the answer — **including a rejection**, which the competitor never writes. */
    suspend fun recordConsent(granted: Boolean): AppResult<Unit>

    /** The only read path for the result screen. `null` means no scan has ever completed. */
    fun observeLastResult(): Flow<ScanRecord?>

    /**
     * Persists a completed scan. Called **before** the navigation Effect fires, so a screen that
     * starts on the next frame can never find nothing there (§0.4).
     *
     * A plain `List` because it is an input the caller already owns; what this port *hands out* is
     * immutable (`LLM.md` §8).
     */
    suspend fun recordScanFinished(findings: List<ThreatVerdict>): AppResult<Unit>

    /** After a confirmed removal. */
    suspend fun forget(md5: String): AppResult<Unit>

    /**
     * The action the competitor has no version of: one action per row, delete or uninstall, and no
     * way to keep a false positive on a trusted app off the list (§2.5). Filtered out of
     * [observeLastResult], and carried across a rescan.
     */
    suspend fun ignore(md5: String): AppResult<Unit>
}
