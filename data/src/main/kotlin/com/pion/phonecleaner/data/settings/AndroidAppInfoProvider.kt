package com.pion.phonecleaner.data.settings

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.core.content.pm.PackageInfoCompat
import com.pion.phonecleaner.domain.repository.AppInfoProvider

/**
 * [AppInfoProvider] over `PackageManager` — the platform's own answer, read once.
 *
 * The four values it exposes replace the competitor's hand-typed `"v2.0.0.0"` literal at
 * `AuddulgActivity.java:153` (`docs/screens/20-settings-language-and-push.md` §3.4 delta 3).
 *
 * The reads happen in the constructor and are cached: none of the four can change while the process
 * lives, and `about` reads them from a reducer, which neither suspends nor waits.
 *
 * `isDebugBuild` is `FLAG_DEBUGGABLE` on the **installed** application, not a `BuildConfig.DEBUG`
 * constant. A `BuildConfig` reference answers for whichever module was imported; this answers for the
 * APK, which is what "does the developer row exist" has to mean (§3.2).
 */
internal class AndroidAppInfoProvider(context: Context) : AppInfoProvider {

    private val appContext = context.applicationContext

    override val appName: String = appContext.applicationInfo
        .loadLabel(appContext.packageManager)
        .toString()

    private val packageInfo = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0)
    }.getOrNull()

    /**
     * Empty rather than a placeholder when the platform gives us nothing: `versionName` is
     * `@Nullable` on every API level, and a fabricated "1.0" in a screen whose only job is to state
     * the version is worse than a blank (`LLM.md` §11's rule on invented values).
     */
    override val versionName: String = packageInfo?.versionName.orEmpty()

    override val versionCode: Long = packageInfo?.let(PackageInfoCompat::getLongVersionCode) ?: 0L

    override val isDebugBuild: Boolean =
        (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
