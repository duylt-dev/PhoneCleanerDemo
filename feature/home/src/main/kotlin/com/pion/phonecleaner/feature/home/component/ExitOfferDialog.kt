package com.pion.phonecleaner.feature.home.component

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.catalog.FeatureDescriptor
import com.pion.phonecleaner.core.ui.component.dialog.AppDialog
import com.pion.phonecleaner.feature.home.R

/**
 * The BACK-press offer — the competitor's `nc.u0` over `intwor.xml`.
 *
 * **Dismissible, and that is the delta** (12). The original is `setCancelable(false)`, so BACK is
 * consumed and there is no outside-tap exit; its only "close" affordance is an X icon whose handler
 * loads another ad. Here `onDismissRequest` is a plain dismissal that leaves the app running, and
 * `AppDialog`'s default `dismissible = true` routes both BACK and the scrim tap to it.
 *
 * The call to action is [FeatureDescriptor.exitCtaRes] — the competitor's four-way `if` over the
 * module index, made into data, so adding a feature cannot leave it saying the wrong verb.
 */
@Composable
internal fun ExitOfferDialog(
    descriptor: FeatureDescriptor,
    onAccept: () -> Unit,
    onQuit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(
        onDismissRequest = onDismiss,
        confirmLabel = stringResource(descriptor.exitCtaRes),
        onConfirm = onAccept,
        title = stringResource(descriptor.titleRes),
        body = stringResource(descriptor.descriptionRes),
        icon = { Icon(descriptor.exitIcon, contentDescription = null) },
        dismissLabel = stringResource(R.string.home_exit_quit),
        onDismiss = onQuit,
    )
}
