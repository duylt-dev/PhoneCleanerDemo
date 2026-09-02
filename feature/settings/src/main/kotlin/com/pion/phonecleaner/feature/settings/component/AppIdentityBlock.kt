package com.pion.phonecleaner.feature.settings.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.settings.R

/**
 * Icon, name and version — the block `settings` and `about` both draw
 * (`docs/screens/20-settings-language-and-push.md` §1.3, §3.3).
 *
 * **The icon is not clickable, and that is a deliberate deletion of two behaviours**, not an
 * omission:
 *
 *  1. The competitor's 120 dp logo carries an **empty** `onClick` (`DonactioActivity.java:59-60`).
 *     TalkBack announces a click target as actionable, so an empty one is a defect that reads as a
 *     feature (§1.4 delta 2). `contentDescription = null` here: the app name is written directly
 *     below, so the image is decorative and a second announcement is noise.
 *  2. On `about`, eight taps on the same logo permanently enable logging **in the shipped release
 *     build** and flip a bundled-SDK flag for the rest of the process (§3.4 delta 1). Production
 *     logging any user can switch on is a data-exposure path, and the gesture has no time window and
 *     no reset. Debug tooling lives in `src/debug` and nowhere else.
 *
 * @param version already formatted by the caller, because the two screens format it differently and
 *   a composable that takes a `versionName` plus a `versionCode` would have to choose for both.
 */
@Composable
internal fun AppIdentityBlock(
    appName: String,
    version: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        AppIcon()
        if (appName.isNotBlank()) {
            Text(text = appName, style = MaterialTheme.typography.titleMedium)
        }
        if (version.isNotBlank()) {
            Text(
                text = version,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The launcher icon, drawn through the `Drawable` the platform hands back.
 *
 * `painterResource(applicationInfo.icon)` is deliberately not used: a modern launcher icon is an
 * `AdaptiveIconDrawable` XML, which that loader does not decode, and the id is `0` on an app that
 * declares none — a crash in the one screen whose whole job is to say what this app is.
 */
@Composable
private fun AppIcon() {
    val context = LocalContext.current
    val drawable = remember(context) {
        runCatching { context.packageManager.getApplicationIcon(context.applicationInfo) }.getOrNull()
    } ?: return
    Canvas(Modifier.size(AppIconSize)) {
        drawIntoCanvas { canvas ->
            drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
            drawable.draw(canvas.nativeCanvas)
        }
    }
}

/** A size, not a gap: off the 4 dp spacing scale on purpose, and named rather than inlined. */
private val AppIconSize = 96.dp

/**
 * "Version 1.0 (1)" — formatted where the strings live, and shared by `settings` and `about` so the
 * two cannot drift apart.
 *
 * Blank when the platform gave us no `versionName`: [AppIdentityBlock] then draws nothing, which is
 * honest, rather than "Version  (0)". The competitor hand-types `"v2.0.0.0"` and reuses
 * `@string/app_name` as its debug row's label (§3.4 deltas 3 and 4).
 */
@Composable
internal fun appVersionLabel(versionName: String, versionCode: Long): String =
    if (versionName.isBlank()) {
        ""
    } else {
        stringResource(R.string.settings_about_version, versionName, versionCode)
    }
