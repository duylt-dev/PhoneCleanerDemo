package com.pion.phonecleaner.data.notification

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.notification.NotificationHidingSettings
import com.pion.phonecleaner.domain.policy.NotificationInterceptionPolicy
import com.pion.phonecleaner.domain.repository.HiddenNotificationNotifier
import com.pion.phonecleaner.domain.repository.NotificationCleanerRepository
import com.pion.phonecleaner.domain.repository.NotificationHidingSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The notification listener (`docs/screens/17-notification-and-permissions.md` §5), replacing
 * `DevitioService` + `od.i` + the `flux_refresh_noti_list` channel.
 *
 * A `NotificationListenerService` is a platform component: the system instantiates it, binds it and
 * owns its lifetime. It cannot extend `MviViewModel`, it has no `State` and it cannot take constructor
 * dependencies — so this and `PushMessagingService` are the **only** two places in the app where Koin's
 * service-locator form (`KoinComponent` + `by inject()`) is correct (§4.4,
 * `docs/system-architecture.md` §5.9). It must not be copied into a ViewModel.
 *
 * ### The hot path holds no disk read
 *
 * [settings] is a `MutableStateFlow` warmed in [onListenerConnected], so `shouldHide` reads memory. The
 * competitor performs **two `SharedPreferences` reads on the binder thread per posted notification**,
 * and then a full Gson decode / encode / `commit()` to append (§5.2) — synchronous disk I/O on a thread
 * the system is waiting on.
 *
 * ### `onListenerDisconnected` calls `requestRebind`
 *
 * The competitor only logs. Being unbound after an app update or a low-memory kill and never returning
 * until the user re-toggles the switch is the documented Android failure mode for this component (§5.2).
 */
class NotificationInterceptorService : NotificationListenerService(), KoinComponent {

    private val repository: NotificationCleanerRepository by inject()
    private val settingsStore: NotificationHidingSettingsStore by inject()
    private val notifier: HiddenNotificationNotifier by inject()
    private val policy: NotificationInterceptionPolicy by inject()
    private val dispatchers: DispatcherProvider by inject()
    private val log: AppLogger by inject()

    private val serviceScope by lazy { CoroutineScope(SupervisorJob() + dispatchers.io) }

    /** Hiding nothing is the only safe answer while the settings are still unknown. */
    private val settings = MutableStateFlow(NotificationHidingSettings.Disabled)

    private val mapper by lazy { StatusBarNotificationMapper(applicationContext) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // An explicit `collect` inside `launch`, not `.launchIn(...)`: the two are the same coroutine,
        // and the explicit form is the one a grep for a stray collector finds (MVI §1).
        serviceScope.launch {
            settingsStore.observe().collect { settings.value = it }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        requestRebind(ComponentName(this, javaClass))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val posted = sbn ?: return
        val current = settings.value
        if (!policy.shouldHide(mapper.toFacts(posted), current)) return

        val hidden = mapper.toHiddenNotification(posted)
        serviceScope.launch {
            if (repository.insert(hidden) !is AppResult.Success) return@launch
            // The count comes from the store, never recomputed by hand: `od.i` keeps its own running
            // total beside the list and the two drift (§5.2).
            notifier.advertise(
                count = repository.observeHiddenCount().first(),
                packageNames = listOf(hidden.packageName),
            )
        }
        // Only the service may call this, and only after the policy said yes. A blank notification is
        // neither stored nor cancelled, so it stays in the shade where it is at least readable (§5.2).
        cancelNotification(posted.key)
    }

    override fun onDestroy() {
        log.d { "NotificationInterceptorService destroyed" }
        serviceScope.cancel()
        super.onDestroy()
    }
}
