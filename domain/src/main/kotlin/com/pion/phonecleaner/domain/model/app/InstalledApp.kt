package com.pion.phonecleaner.domain.model.app

/**
 * One installed app, as the **shared** `InstalledAppsRepository` returns it.
 *
 * OWNERSHIP NOTE — this is the only file this agent wrote under `model/app/`. `RunningApp`,
 * `LockableApp` and the rest of `LLM.md` §3.3's `model/app/` row belong to the cluster that owns
 * them. `InstalledApp` is here because `InstalledAppsRepository` is declared once in `coreDataModule`
 * and read by **four** clusters — files, app-lock, notification and device — so its return type
 * cannot live in any one of them (`docs/system-architecture.md` §4.1, §5.4).
 *
 * The name is adjudicated: the rich type won, and the competing one-field `InstalledApp(packageName)`
 * was renamed `RunningApp(packageName)` — a different model for a different screen
 * (`docs/system-architecture.md` §4.1).
 *
 * **No icon field.** Five competitor models hold a `Drawable`; that single choice is the direct cause
 * of `Selerover.writeToParcel` dropping the icon and of two identical rows comparing unequal because
 * the image loader returned a different instance. [packageName] is the icon's identity and
 * `AppIconLoader` resolves it at render (`LLM.md` §8, `docs/screens/21-shared-models-and-ui.md:1.2`).
 */
data class InstalledApp(
    /** The identity, and the key of every list this appears in. */
    val packageName: String,
    val label: String,
    val uid: Int,
    /** `sourceDir` length — a SIZE, not a date. The competitor's row mislabels this field. */
    val apkBytes: Long,
    /**
     * App bytes measured on disk, or null when nothing has measured them.
     *
     * `docs/system-architecture.md` §4.1 splits storage statistics **off** this port into
     * `AppStorageStatsRepository` (`filesDataModule`), so `InstalledAppsRepository` leaves this null;
     * only the stats port fills it. It stays on the model because
     * `docs/screens/21-shared-models-and-ui.md:73` adjudicates it as one of the six fields.
     */
    val appBytesOnDisk: Long? = null,
    /**
     * Epoch millis of last use, `0` when unknown.
     *
     * PENDING OWNER DECISION (3): whether the app requires the user to grant `PACKAGE_USAGE_STATS`
     * by hand. Without that grant this is `0` for every app, and a caller must treat `0` as "not
     * known", never as "not used" — sorting by it would then silently order by nothing. No code here
     * assumes either outcome.
     */
    val lastUsedAtMillis: Long = 0L,
)
