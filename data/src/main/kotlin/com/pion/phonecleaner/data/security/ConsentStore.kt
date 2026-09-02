package com.pion.phonecleaner.data.security

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.security.ScanConsentState

/**
 * Whether the user has agreed to what the cloud scan sends
 * (`docs/screens/15-antivirus.md` §0.5).
 *
 * `internal`: the layer above asks `SecurityScanRepository.consent()`. It is an interface, and bound
 * in `securityDataModule`, so the repository's consent branches can be tested without a DataStore.
 */
internal interface ConsentStore {

    suspend fun state(): ScanConsentState

    /** Records the answer, **including a rejection**, against the current disclosure version. */
    suspend fun record(granted: Boolean): AppResult<Unit>
}
