package com.pion.phonecleaner.data.files

import com.pion.phonecleaner.domain.model.file.WhatsAppBucketId
import com.pion.phonecleaner.domain.repository.WhatsAppRoots
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The WhatsApp path catalogue, **carrying no paths**.
 *
 * PENDING OWNER DECISION (1) — **still open for THIS cluster.** The junk half of that fork was
 * settled on 2026-09-06 (branch 2: our own rules, `KotlinJunkRuleCatalog`), and this is now the last
 * reader of the decision. The precedent it sets is the one to follow when this half is settled: write
 * our own table in code, never paste the competitor's. The six-bucket path table is the **competitor's**
 * (`docs/reverse-engineering/14-file-tools-and-app-manager.md`), and the task that commissioned this
 * cluster is explicit: read the paths through an interface, do not paste a copied table into this
 * repository. Whether a competitor-derived catalogue is reused at all is the owner's call.
 *
 * Replacing it is one line in `filesDataModule` and nothing else — an asset-backed
 * `AssetWhatsAppRoots` or a hand-written `KotlinWhatsAppRoots` binds in place of this class. Until
 * then the scanner reports six empty buckets and the screen renders its empty state, which is what
 * "we found nothing" honestly looks like when there is nothing to look for.
 *
 * **What must not be copied even when the decision lands**: the absolute prefix. The competitor
 * hard-codes `/storage/emulated/0`, so a secondary volume or a non-zero user profile is a different
 * path and its cleaner silently reads nothing. This port hands out **relative suffixes**, which
 * `DefaultWhatsAppScanner` joins to a root `StorageRootProvider` resolved (§6.2).
 */
internal class EmptyWhatsAppRoots : WhatsAppRoots {

    override suspend fun suffixesFor(bucket: WhatsAppBucketId): ImmutableList<String> =
        persistentListOf()
}
