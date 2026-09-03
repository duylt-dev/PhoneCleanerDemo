package com.pion.phonecleaner.feature.files.appmanager.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.format.rememberAbsoluteDate
import com.pion.phonecleaner.domain.model.app.ManagedApp
import com.pion.phonecleaner.domain.usecase.LoadInstalledAppsUseCase
import com.pion.phonecleaner.feature.files.R

/**
 * The two lines under the size: when the app was installed, and when it was last used.
 *
 * **Two lines, not one.** They were previously one line that showed the last-used date *or*, failing
 * that, the install date — so the two facts could never be read together, and a row that had both
 * silently hid one of them. They are different facts about different things and the row states both.
 *
 * Both are **dates rendered from date fields**. The competitor formats the APK's byte length through
 * a `yyyy-MM-dd` formatter and labels it *"installation time"*, so a 25 MB APK shows as a 1970 date
 * (§5.5).
 *
 * `0` is not a date and neither line renders one:
 *
 *  * `firstInstallEpochMillis == 0` means the `PackageManager` lookup failed — "install date not
 *    available", never a fabricated date.
 *  * `lastUsedEpochMillis == 0` has **two** meanings and they are not interchangeable, which is why
 *    [usageAccessGranted] is a parameter. Without the grant nothing was measured and the honest
 *    answer is "no usage data"; with it, `0` means the app was not opened inside the query window,
 *    and the copy names that window from the same constant the query uses
 *    ([LoadInstalledAppsUseCase.USAGE_WINDOW_DAYS]) rather than spelling a period out in prose. The
 *    competitor queries 720 days and writes "Not Used For One Year" — two numbers, maintained apart.
 */
@Composable
internal fun AppRowSubtitle(app: ManagedApp, usageAccessGranted: Boolean) {
    Text(
        text = installedLine(app),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = lastUsedLine(app, usageAccessGranted),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun installedLine(app: ManagedApp): String = if (app.firstInstallEpochMillis > 0L) {
    stringResource(R.string.app_manager_installed_on, rememberAbsoluteDate(app.firstInstallEpochMillis))
} else {
    stringResource(R.string.app_manager_install_date_unknown)
}

@Composable
private fun lastUsedLine(app: ManagedApp, usageAccessGranted: Boolean): String = when {
    app.lastUsedEpochMillis > 0L ->
        stringResource(R.string.app_manager_last_used_on, rememberAbsoluteDate(app.lastUsedEpochMillis))

    !usageAccessGranted -> stringResource(R.string.app_manager_last_used_unknown)

    else -> {
        val days = LoadInstalledAppsUseCase.USAGE_WINDOW_DAYS.toInt()
        pluralStringResource(R.plurals.app_manager_last_used_never, days, days)
    }
}
