package com.pion.phonecleaner.domain.model.app

/**
 * One row of the App Manager (`docs/screens/14-file-tools-and-app-manager.md` §5.1).
 *
 * **Named apart from [InstalledApp] on purpose** (§10 item 4): the shared port hands out
 * `InstalledApp`, four clusters read it, and a row carrying three measured sizes and two timestamps
 * is not that type. `RunningApp(packageName)` — the device cluster's one-field model — is a third
 * thing again. The competitor has three separate entry points to one list and one bean serving all
 * of them, which is how its row came to render a byte count through a `yyyy-MM-dd` formatter.
 *
 * **No icon field.** [packageName] is the icon's identity and `AppIconLoader` resolves it at draw
 * time. Five competitor models hold a `Drawable`; that one choice is the direct cause of an `equals`
 * anomaly where two identical rows compare unequal because the loader returned a different instance
 * (`LLM.md` §8).
 *
 * **No `isSelected`.** Selection is a `Set<String>` of [packageName] on `State`, beside the list.
 */
data class ManagedApp(
    /** The identity, and the `LazyColumn` key. */
    val packageName: String,
    val label: String,
    val uid: Int,
    /** `sourceDir` length — a SIZE. The competitor's row labels this field "installation time". */
    val apkBytes: Long,
    /** `PackageInfo.firstInstallTime` — what the row always claimed to show. */
    val firstInstallEpochMillis: Long,
    /**
     * Epoch millis of last use, `0` when unknown.
     *
     * PENDING OWNER DECISION (3) — whether the user must grant `PACKAGE_USAGE_STATS` by hand.
     * Without that grant this is `0` for every app, and `0` must be read as "not known", never as
     * "not used": sorting by it would silently order by nothing. The screen disables the *Last used*
     * sort instead (§5.2), and nothing here assumes either outcome.
     */
    val lastUsedEpochMillis: Long = 0L,
    /** Filled by `AppStorageStatsRepository`, one emission per app. Null until measured. */
    val stats: AppStorageStats? = null,
) {
    /** What removing the app would recover, as far as anything has measured. */
    val totalBytes: Long get() = stats?.totalBytes ?: 0L
    val sizeKnown: Boolean get() = stats?.isMeasured == true
}
