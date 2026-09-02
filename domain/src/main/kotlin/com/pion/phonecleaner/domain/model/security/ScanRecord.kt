package com.pion.phonecleaner.domain.model.security

import kotlinx.collections.immutable.ImmutableList

/**
 * The last completed scan, as persisted (`docs/screens/15-antivirus.md` §0.4).
 *
 * This type is the fix for the central defect of the competitor's result screen: **its finding list
 * exists in exactly one place, a Gson blob in the `Intent` extra of a *finished* Activity**. The scan
 * screen finishes 300 ms after handing it over, so closing the result screen destroys the list —
 * no re-open, no history, and re-checking costs another full cloud round trip
 * (`docs/reverse-engineering/15-antivirus.md` §3.4).
 *
 * Written **before** the navigation Effect fires, and `observeLastResult()` is the only read path.
 *
 * DELIBERATE OMISSION — no `ScanCoverage` field. `docs/screens/15-antivirus.md` §1.5 carries coverage
 * "into the result", but §0.4 states this type as two fields and `threat_cache` (owned by
 * `coreDataModule`, not by this cluster) has no column for it. Persisting it would mean editing a
 * table this cluster does not own; fabricating it on read would be worse.
 */
data class ScanRecord(
    /** Epoch milliseconds. `MAX(found_at)` over the cached rows. */
    val finishedAtEpochMs: Long,
    /** Already filtered by the repository, and already minus the rows the user chose to ignore. */
    val findings: ImmutableList<ThreatVerdict>,
)
