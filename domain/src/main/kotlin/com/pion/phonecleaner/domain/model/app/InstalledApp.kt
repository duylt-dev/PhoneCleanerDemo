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
    /**
     * `PackageInfo.firstInstallTime`, `0` when it could not be read.
     *
     * It is a DATE and it is read from a date field. The competitor has no install-time field at all:
     * its row formats [apkBytes] through a `yyyy-MM-dd` formatter and labels the result *"installation
     * time"*, so a 25 MB APK renders as a 1970 date (`docs/screens/14` §5.5). `0` here means the
     * `PackageManager` lookup failed, never "installed at the epoch"; a caller must render it as
     * unknown rather than as a date.
     */
    val firstInstallAtMillis: Long = 0L,
    /**
     * True for a package the platform ships — `FLAG_SYSTEM`, or `FLAG_UPDATED_SYSTEM_APP` for one
     * that shipped and was later updated by the store.
     *
     * OWNER DECISION (2026-09-03): the App Manager **never lists a system app**, and there is no
     * switch that changes it — `LoadInstalledAppsUseCase` drops them. That closes the UNKNOWN the
     * port carried: the list used to include them because no appendix stated a rule, and every such
     * row offered an uninstall the platform then refused.
     *
     * The flag is on the model rather than applied inside the port because the other three callers
     * of `InstalledAppsRepository` — app-lock, notification and network — were not part of that
     * decision and still see the whole list. A caller that wants the App Manager's rule filters on
     * this flag; the port never filters for it.
     */
    val isSystem: Boolean = false,
)
