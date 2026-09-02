package com.pion.phonecleaner.feature.applock.applock.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pion.phonecleaner.core.ui.icon.AppIconLoader
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.applock.LockableApp
import com.pion.phonecleaner.feature.applock.R
import com.pion.phonecleaner.feature.applock.applock.AppLockIntent
import org.koin.compose.koinInject

/**
 * One app in the list.
 *
 * **The icon is resolved from [LockableApp.packageName] at draw time**, by `AppIconLoader` from
 * `coreUiModule` — `koinInject()`ed in the composable, never into a ViewModel. The competitor's
 * `be.g` carries a `Drawable` on the model, which is what makes two identical rows compare unequal
 * once the image loader hands back a different instance (`LLM.md` §8).
 *
 * [isBusy] is `packageName in state.togglingPackages`: a **per-row** guard, replacing a
 * process-global 500 ms click debounce under which a tap anywhere blocks a tap everywhere
 * (`java/md/g1.java:61, 426-432`).
 *
 * `onIntent` is taken as-is and the tap emits the **event**, not the mutation: the reducer decides
 * what a tap on this row means, because that decision differs by permission state and by whether the
 * row is already locked.
 */
@Composable
internal fun AppLockRow(
    app: LockableApp,
    isBusy: Boolean,
    onIntent: (AppLockIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val icons = koinInject<AppIconLoader>()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !isBusy) { onIntent(AppLockIntent.AppRowTapped(app.packageName)) }
            .padding(horizontal = ScreenGutter, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = icons.request(app.packageName),
            imageLoader = icons.imageLoader,
            contentDescription = null, // decorative: the label beside it names the app
            modifier = Modifier.size(RowIconSize),
        )
        Text(
            text = app.label,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.lg),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isBusy) {
            CircularProgressIndicator(Modifier.size(BusyIndicatorSize))
        } else {
            Icon(
                imageVector = if (app.isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = stringResource(
                    if (app.isLocked) R.string.app_lock_row_locked else R.string.app_lock_row_unlocked,
                ),
                tint = if (app.isLocked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** **Positions**, not gaps (MVI §11): 40 dp is a legible launcher icon in a list row. */
private val RowIconSize = 40.dp
private val BusyIndicatorSize = 24.dp
