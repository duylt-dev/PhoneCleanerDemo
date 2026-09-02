package com.pion.phonecleaner.domain.model.file

/**
 * One emission per finished bucket, then exactly one [Finished]
 * (`docs/screens/14-file-tools-and-app-manager.md` §6.2).
 *
 * Per-bucket emission is the point: tiles fill in one at a time instead of all six appearing at once
 * after the competitor's 4 000 ms floor.
 */
sealed interface WhatsAppScanProgress {

    data class BucketFinished(val bucket: WhatsAppBucket) : WhatsAppScanProgress

    /** [coverage] says which surfaces the current grant reached — the legacy root may not be one. */
    data class Finished(val coverage: ScanCoverage = ScanCoverage.Unknown) : WhatsAppScanProgress
}
