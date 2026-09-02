package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.domain.model.file.WhatsAppScanProgress
import com.pion.phonecleaner.domain.repository.WhatsAppScanner
import kotlinx.coroutines.flow.Flow

/**
 * The WhatsApp bucket scan (`docs/screens/14-file-tools-and-app-manager.md` §6.2).
 *
 * A pass-through, for the same reason as [FindDuplicatesUseCase]: the walk is I/O and lives in the
 * engine, and no screen in this cluster names an engine's port directly.
 *
 * What it does **not** do is as important: it does not pace, it does not floor, and it does not press
 * its own button. The competitor arms a 4 s countdown on an empty scan and then **self-clicks Clean**,
 * deleting nothing and walking the user into an interstitial (§6.5).
 */
class ScanWhatsAppUseCase(
    private val scanner: WhatsAppScanner,
) {
    operator fun invoke(): Flow<WhatsAppScanProgress> = scanner.scan()
}
