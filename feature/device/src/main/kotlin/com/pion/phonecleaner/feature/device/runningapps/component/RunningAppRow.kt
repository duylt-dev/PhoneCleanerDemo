package com.pion.phonecleaner.feature.device.runningapps.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pion.phonecleaner.core.ui.icon.AppIconLoader
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.device.RunningApp
import com.pion.phonecleaner.feature.device.R
import com.pion.phonecleaner.feature.device.runningapps.RunningAppsIntent
import org.koin.compose.koinInject

/**
 * One row (`docs/screens/18-device-battery-and-apps.md` §6.2).
 *
 * The icon is Coil through `AppIconLoader`, **injected into the composable with `koinInject()` and
 * never into a ViewModel** — it replaces the competitor's hand-rolled 2-thread pool, its two
 * `LruCache`s and its view-tag recycling guard wholesale.
 *
 * ## UNKNOWN — the app label
 *
 * §6.2 writes the row as *"`AsyncImage(AppIcon(pkg))`, a label from `AppIconLoader`, `TextButton`"*,
 * and §6.5 rejects the package name as a **placeholder**. There is no label source in this codebase
 * for this row: `RunningApp` carries `packageName` and nothing else — deliberately, §1 — and
 * `AppIconLoader` (`:core:ui/icon/AppIconLoader.kt`) exposes an `ImageLoader` and a
 * `Request(packageName)`, with no label API; `InstalledAppsRepository`'s rich model is the App
 * Manager's, and §8 keeps the two lists apart on purpose. Looked in both files, in
 * `:core:ui/icon/PackageIconFetcher.kt`, and in the digest's `:core:ui` signature list.
 *
 * So the package name is rendered as the row's **value**, not as a placeholder standing in for a
 * label that is loading — there is no second state it resolves into, and a shimmer that never
 * resolves would be worse than the identifier. Adding a label means a label field on `RunningApp`
 * and a `PackageManager` read in `PackageManagerRunningAppsRepository`; that is a design change,
 * not a rendering one.
 *
 * `onIntent` arrives as-is and the callback is `remember`ed on the package name, so a tap recomposes
 * one row rather than every row (`LLM.md` §8).
 *
 * A verified stop replaces the button with a plain word. It is not a claim about memory, speed or
 * battery — it reports the one bit Android actually set (§6.5).
 */
@Composable
internal fun RunningAppRow(
    app: RunningApp,
    onIntent: (RunningAppsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val icons = koinInject<AppIconLoader>()
    val packageName = app.packageName
    val onStop = remember(packageName, onIntent) {
        { onIntent(RunningAppsIntent.StopTapped(packageName)) }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenGutter, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = icons.request(packageName),
            imageLoader = icons.imageLoader,
            contentDescription = null, // decorative: the text beside it names the app
            modifier = Modifier.size(RowIconSize),
        )
        Text(
            text = packageName,
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.md),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (app.isStopped) {
            Text(
                text = stringResource(R.string.running_apps_stopped),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            TextButton(onClick = onStop) {
                Text(stringResource(R.string.running_apps_stop))
            }
        }
    }
}

private val RowIconSize = 40.dp
