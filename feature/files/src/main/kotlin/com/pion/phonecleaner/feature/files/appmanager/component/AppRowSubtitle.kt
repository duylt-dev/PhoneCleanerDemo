package com.pion.phonecleaner.feature.files.appmanager.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.format.rememberRelativeTimestamp
import com.pion.phonecleaner.domain.model.app.ManagedApp
import com.pion.phonecleaner.feature.files.R

/**
 * The second line: when the app was last used, or when it was installed.
 *
 * Both are **dates rendered from date fields**. The competitor formats the APK's byte length through
 * a `yyyy-MM-dd` formatter and labels it *"installation time"*, so a 25 MB APK shows as a 1970 date
 * (§5.5).
 *
 * `0` is not a date. `lastUsedEpochMillis == 0` means "not seen inside the usage window" and renders
 * as "no usage data"; `firstInstallEpochMillis == 0` means the value was not read at all.
 *
 * UNKNOWN — `LoadInstalledAppsUseCase` currently passes `firstInstallEpochMillis = 0L` for every
 * row, because `InstalledApp` (domain/model/app/InstalledApp.kt) carries no install timestamp.
 * Looked for: `InstalledApp`, `InstalledAppsRepository`, and §5.1/§5.5 of
 * `docs/screens/14-file-tools-and-app-manager.md`, which specify `PackageInfo.firstInstallTime` but
 * do not say what the row shows when it is absent. Saying so is the conservative branch; inventing
 * a date is not.
 */
@Composable
internal fun AppRowSubtitle(app: ManagedApp) {
    val text = when {
        app.lastUsedEpochMillis > 0L -> rememberRelativeTimestamp(app.lastUsedEpochMillis)
        app.firstInstallEpochMillis > 0L -> rememberRelativeTimestamp(app.firstInstallEpochMillis)
        else -> stringResource(R.string.app_manager_last_used_unknown)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
