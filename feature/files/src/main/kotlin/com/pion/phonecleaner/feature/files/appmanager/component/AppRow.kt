package com.pion.phonecleaner.feature.files.appmanager.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.icon.AppIconLoader
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.app.ManagedApp
import com.pion.phonecleaner.feature.files.R
import com.pion.phonecleaner.feature.files.appmanager.AppManagerIntent
import org.koin.compose.koinInject

/**
 * One app (`docs/screens/14-file-tools-and-app-manager.md` §5.3).
 *
 * The icon is Coil through `AppIconLoader`, **injected into the composable with `koinInject()` and
 * never into a ViewModel**. It replaces a hand-rolled 2-thread `ExecutorService`, a 50-entry
 * `LruCache<Drawable>` and a 500-entry label cache.
 *
 * `onIntent` arrives as-is and both callbacks are `remember`ed on the package name, so a tap
 * recomposes one row instead of re-summing every row and rebinding the footer (`LLM.md` §8).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppRow(
    app: ManagedApp,
    selected: Boolean,
    onIntent: (AppManagerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val icons = koinInject<AppIconLoader>()
    val bytes = rememberByteFormat()
    val packageName = app.packageName
    val onToggle = remember(packageName, onIntent) {
        { onIntent(AppManagerIntent.RowToggled(packageName)) }
    }
    val onLongPress = remember(packageName, onIntent) {
        { onIntent(AppManagerIntent.AppInfoRequested(packageName)) }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onLongClick = onLongPress, onClick = onToggle)
            .padding(horizontal = ScreenGutter, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = icons.request(packageName),
            imageLoader = icons.imageLoader,
            contentDescription = null, // decorative: the label beside it names the app
            modifier = Modifier.size(RowIconSize),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = Spacing.md),
        ) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // A refused StorageStatsManager query renders as "—". The competitor defaults the
                // field to 1000L and renders a fabricated "1000 B" that also sorts wrong (§5.5).
                text = if (app.sizeKnown) {
                    bytes.size(app.totalBytes).toString()
                } else {
                    stringResource(R.string.app_manager_size_unknown)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppRowSubtitle(app)
        }
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
    }
}

private val RowIconSize = 40.dp
