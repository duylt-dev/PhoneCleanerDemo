package com.pion.phonecleaner.feature.notification.component

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pion.phonecleaner.core.ui.icon.AppIconLoader
import org.koin.compose.koinInject

/**
 * One app icon, resolved from a package name at draw time.
 *
 * `AppIconLoader` is `koinInject`ed into the composable and **never into a ViewModel**
 * (`docs/system-architecture.md` §4.7): a ViewModel holding an image loader is one a screenshot test
 * has to stub, and it puts an Android type behind the boundary MVI §1 keeps clear.
 *
 * No model in this cluster holds an icon. `vd.d.a()` calls `loadIcon` for every launchable app before
 * anything renders, so the first frame waits on N `PackageManager` loads
 * (`docs/screens/17-notification-and-permissions.md` §2.5); here the load is per **visible** row and
 * Coil cancels it when the row scrolls away.
 */
@Composable
internal fun AppIconImage(
    packageName: String,
    modifier: Modifier = Modifier,
    size: Dp = AppIconSize,
) {
    val icons = koinInject<AppIconLoader>()
    AsyncImage(
        model = icons.request(packageName),
        imageLoader = icons.imageLoader,
        // Decorative: the row's own label already names the app, and a second announcement of the
        // same name is noise to a screen reader.
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

/** A **position**, not a gap, so it is off the 4 dp scale and named (MVI §11): the list-row icon size. */
internal val AppIconSize: Dp = 40.dp
