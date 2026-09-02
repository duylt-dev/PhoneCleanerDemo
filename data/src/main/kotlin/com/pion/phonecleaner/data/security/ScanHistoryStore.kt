package com.pion.phonecleaner.data.security

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.security.ScanRecord
import com.pion.phonecleaner.domain.model.security.ThreatVerdict
import kotlinx.coroutines.flow.Flow

/**
 * Where the last scan lives between screens, and between process launches
 * (`docs/screens/15-antivirus.md` §0.4, §0.6).
 *
 * `internal`: only [DefaultSecurityScanRepository] talks to it, and the layer above sees
 * `SecurityScanRepository` and nothing else. It is bound in `securityDataModule` so a test can swap
 * the Room implementation for a fake without a database.
 */
internal interface ScanHistoryStore {

    /** `null` until a scan has completed. Never "scanned at 0". */
    fun observeRecord(): Flow<ScanRecord?>

    /** Replaces the stored scan. Carries every ignore flag forward — see the implementation. */
    suspend fun save(findings: List<ThreatVerdict>): AppResult<Unit>

    suspend fun forget(md5: String): AppResult<Unit>

    suspend fun ignore(md5: String): AppResult<Unit>
}
