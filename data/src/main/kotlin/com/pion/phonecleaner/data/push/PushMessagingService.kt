package com.pion.phonecleaner.data.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pion.phonecleaner.data.di.APP_SCOPE
import com.pion.phonecleaner.domain.model.push.PushMessage
import com.pion.phonecleaner.domain.repository.PushRepository
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

/**
 * The manifest component that receives a push message, and the **only** thing in this app that names
 * Firebase Messaging (`LLM.md` §3.6 — a `Service` lives in `:data`, beside its code, and its
 * `<service>` element in this module's manifest).
 *
 * ### `KoinComponent` + `by inject()` is correct here, and nowhere else
 *
 * The system instantiates this class through its no-arg constructor, so constructor injection is not
 * available. `LLM.md` §6.5 names exactly two classes for which the service-locator form is right —
 * `NotificationInterceptorService` and this one — and states the rule here so that nobody copies it
 * into a ViewModel.
 *
 * ### What it does, and where it deliberately stops
 *
 * It maps `RemoteMessage` to [PushMessage] and hands it to [PushRepository]. That is the whole of it.
 *
 *  * **There is no backend and none is in scope** (owner decision 1), so **nothing sends to this
 *    service**. Its production behaviour today is to never be called. It is written anyway because
 *    the boundary is the deliverable: the day a payload contract exists, `PushRepository`'s one
 *    implementation is what changes.
 *  * **PENDING OWNER DECISION 4** defers notification scenes, re-engagement notifications and the
 *    resident status-bar widget, so nothing here posts a notification, enqueues a worker or starts a
 *    service. `onMessageReceived` runs on a binder thread with roughly ten seconds of budget; the
 *    handoff goes to the app-scope `CoroutineScope` from `coreModule` rather than blocking it.
 *  * **`onNewToken` is deliberately not overridden.** A refreshed registration token has no reader:
 *    there is no backend to register it with, and a token persisted for nobody is the orphan key
 *    `docs/screens/20-settings-language-and-push.md` §6.4 delta 5 records. (The SDK's own
 *    `onNewToken(String)` is deprecated in this release besides, so overriding it would carry a
 *    deprecation into the module for no reader.)
 *  * `remoteMessage.notification` is deliberately **not** read. A `notification` payload is displayed
 *    by the SDK itself when the app is backgrounded, which would post a notification the owner
 *    decision defers; only the `data` map — which the platform hands to the app either way — is
 *    carried.
 *
 * ### The competitor's version of this path
 *
 * Its out-of-app entry `mf.EnsileActivity` is `exported="true"` with no `intent-filter`, no
 * `android:permission` and no caller check, and does `putExtras(getIntent())` verbatim into a launch
 * (`AndroidManifest.xml:501-503`, `mf/EnsileActivity.java:65-72`), so any installed app can drive it
 * to any module and set its ad scene (`LLM.md` §7.3). There is no exported trampoline here, and a
 * payload that is only logged cannot route anything.
 */
internal class PushMessagingService : FirebaseMessagingService(), KoinComponent {

    private val push: PushRepository by inject()
    private val appScope: CoroutineScope by inject(named(APP_SCOPE))

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val message = PushMessage(
            messageId = remoteMessage.messageId,
            sentAtMillis = remoteMessage.sentTime,
            data = remoteMessage.data.toImmutableMap(),
        )
        appScope.launch { push.handle(message) }
    }
}
