package com.pion.phonecleaner.domain.model.security

/**
 * One finding, as the app understands it — **a verdict, not the SDK's row type**
 * (`docs/screens/15-antivirus.md` §0.1).
 *
 * The vendor's own row carries twenty fields, is mutable, is `Serializable`, and is handed between
 * the competitor's two screens as a Gson blob in an `Intent` extra, read back with `checkNotNull` —
 * an NPE on the main thread at `onCreate` for any malformed extra
 * (`docs/reverse-engineering/15-antivirus.md` §3.4). **It must not leak past `:data`**: the mapping
 * lives in `data/security/`, and this is what every layer above sees.
 *
 * No `@Immutable`: `:domain` is a `kotlin("jvm")` module compiled without Compose, so the stability
 * declaration is the `com.pion.phonecleaner.domain.model.*` entry in `compose-stability.conf`
 * (`LLM.md` §8). Every field is a `val` of a stable type, which is what that entry promises.
 *
 * [familyName], [summary], [category] and [vid] are carried although the competitor renders none of
 * them: they are already parsed and already in memory, and they are what lets a row say *why* it is
 * listed instead of showing a level with no reason (§0.1).
 */
data class ThreatVerdict(
    /**
     * The identity, and the key of every list and every selection this appears in.
     *
     * **Not the package name**: a loose `.apk` on storage and a test-file hit both have an empty
     * [packageName], so a package name is not unique across the list (§2.3).
     */
    val md5: String,
    /** `""` for a loose `.apk` or a test-file hit. See [isInstalledApp]. */
    val packageName: String,
    val apkPath: String,
    /** `appName ?: packageName ?: file name`, resolved ONCE in the mapper — never at render. */
    val label: String,
    /** 0, 1, 6 or 8 in practice. [RiskLevel.of] is the whole risk model. */
    val score: Int,
    /** The server's verdict id. Kept, unlike the competitor, so a report can be traced back. */
    val vid: String,
    val familyName: String,
    /** The English description from the vendor's own table. The confirm dialog's body. */
    val summary: String,
    val category: String,
    val apkSizeBytes: Long,
) {
    /** An installed app is removed by the system uninstall dialog; a loose file is deleted by us. */
    val isInstalledApp: Boolean get() = packageName.isNotBlank()

    val risk: RiskLevel get() = RiskLevel.of(score)
}
