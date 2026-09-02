package com.pion.phonecleaner.data.security

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.security.ScanConsentState
import com.pion.phonecleaner.domain.model.security.ScanRecord
import com.pion.phonecleaner.domain.model.security.SecurityScanPhase
import com.pion.phonecleaner.domain.model.security.ThreatVerdict
import com.pion.phonecleaner.domain.repository.SecurityScanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The one implementation of the seam both antivirus screens see
 * (`docs/screens/15-antivirus.md` §0.4, §0.6).
 *
 * It composes three collaborators and owns one rule:
 *
 * | Collaborator | What it is |
 * |---|---|
 * | [TrustLookClient] | the vendor SDK behind one cold flow — the only thing that talks to it |
 * | [ConsentStore] | the two DataStore keys of §0.5 |
 * | [ScanHistoryStore] | `threat_cache`, through the DAO already bound in `coreDataModule` |
 *
 * The rule is [asFindings]: **what counts as a finding**. It is applied here, once, so the scan
 * screen's count and the result screen's list can never disagree — which they do in the competitor,
 * whose list filter and whose badge read the same score through two different cut-points.
 */
internal class DefaultSecurityScanRepository(
    private val client: TrustLookClient,
    private val consentStore: ConsentStore,
    private val history: ScanHistoryStore,
) : SecurityScanRepository {

    /**
     * The client's flow with the filter applied to the terminal emission. Cancelling the collector
     * cancels the client — nothing here adds a scope, a job or a dispatcher: `TrustLookClientImpl`
     * already applies `flowOn(dispatchers.io)`, and a second `flowOn` here would hide it.
     */
    override fun scan(): Flow<SecurityScanPhase> = client.scan().map { phase ->
        when (phase) {
            is SecurityScanPhase.Finished -> SecurityScanPhase.Finished(phase.findings.asFindings())
            else -> phase
        }
    }

    override suspend fun consent(): ScanConsentState = consentStore.state()

    override suspend fun recordConsent(granted: Boolean): AppResult<Unit> =
        consentStore.record(granted)

    override fun observeLastResult(): Flow<ScanRecord?> = history.observeRecord()

    /**
     * Filtered again on the way in, so a caller that hands over a raw list cannot poison the cache.
     * The scan path has already filtered; this is idempotent.
     */
    override suspend fun recordScanFinished(findings: List<ThreatVerdict>): AppResult<Unit> =
        history.save(findings.asFindings())

    override suspend fun forget(md5: String): AppResult<Unit> = history.forget(md5)

    override suspend fun ignore(md5: String): AppResult<Unit> = history.ignore(md5)
}
