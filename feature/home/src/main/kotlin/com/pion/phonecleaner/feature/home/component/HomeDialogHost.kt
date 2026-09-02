package com.pion.phonecleaner.feature.home.component

import androidx.compose.runtime.Composable
import com.pion.phonecleaner.core.ui.catalog.FeatureDescriptors
import com.pion.phonecleaner.feature.home.HomeDialog
import com.pion.phonecleaner.feature.home.HomeIntent

/**
 * The three dialogs, as one state-driven host and **no second ViewModel**
 * (`docs/screens/11-home.md` §2).
 *
 * The competitor builds each one as an `oc.a` subclass with its own inflated view, its own listener
 * interface and its own analytics calls. None of the three is a screen: none has a route, none
 * survives a rotation, and all three mutate the host Activity. Here they are one `state.dialog`
 * field and one `when` — a screen with nothing to decide gets no ViewModel of its own (MVI §3).
 *
 * `ExitOffer` carries a `FeatureId` and the descriptor is resolved **here, at render time**. A
 * locale change or a catalogue refresh then re-renders the open dialog with fresh copy, instead of
 * the snapshot `ae.i2` bakes in by calling `getString` once per process.
 */
@Composable
internal fun HomeDialogHost(
    dialog: HomeDialog,
    onIntent: (HomeIntent) -> Unit,
) {
    when (dialog) {
        HomeDialog.NotificationPermission -> NotificationPermissionSheet(
            onTurnOn = { onIntent(HomeIntent.NotificationSheetAnswered(true)) },
            onDismiss = { onIntent(HomeIntent.NotificationSheetAnswered(false)) },
        )

        HomeDialog.AntivirusConsent -> SecurityConsentDialog(
            onAgree = { onIntent(HomeIntent.SecurityConsentAnswered(true)) },
            onReject = { onIntent(HomeIntent.SecurityConsentAnswered(false)) },
            onOpenDocument = { onIntent(HomeIntent.LinkTapped(it)) },
        )

        is HomeDialog.ExitOffer -> ExitOfferDialog(
            descriptor = FeatureDescriptors.of(dialog.feature),
            onAccept = { onIntent(HomeIntent.ExitOfferAnswered(true)) },
            onQuit = { onIntent(HomeIntent.ExitOfferAnswered(false)) },
            onDismiss = { onIntent(HomeIntent.ExitOfferDismissed) },
        )
    }
}
