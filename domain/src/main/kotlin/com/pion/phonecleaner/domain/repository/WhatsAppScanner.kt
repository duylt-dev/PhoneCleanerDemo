package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.domain.model.file.WhatsAppScanProgress
import kotlinx.coroutines.flow.Flow

/**
 * The WhatsApp bucket engine. It composes [StorageScanner] with a bounded `WalkConfig` and reads its
 * paths from [WhatsAppRoots]; **the competitor's second hand-written recursive `listFiles()` walk —
 * no depth cap, no symlink guard, no exclusions — is deleted**
 * (`docs/screens/14-file-tools-and-app-manager.md` §0.1, §6.2).
 *
 * DECLARED IN `filesDataModule` as `DefaultWhatsAppScanner`.
 *
 * Reachability is the open item this cluster carries (§10 item 1): the default branch walks
 * `Android/media/com.whatsapp/…` through ordinary media access and treats the legacy `/WhatsApp`
 * root as an opt-in SAF tree. **The per-API-level matrix was not verified against the platform** —
 * confidence medium — which is exactly why `WhatsAppScanProgress.Finished` carries a `ScanCoverage`
 * rather than implying the six buckets are the whole story.
 */
interface WhatsAppScanner {

    /** One `BucketFinished` per group, in [WhatsAppRoots]' order, then exactly one `Finished`. */
    fun scan(): Flow<WhatsAppScanProgress>
}
