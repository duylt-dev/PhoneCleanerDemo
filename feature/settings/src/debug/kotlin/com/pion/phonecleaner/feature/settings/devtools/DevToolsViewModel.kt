package com.pion.phonecleaner.feature.settings.devtools

import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.push.PushMessage
import com.pion.phonecleaner.domain.repository.PushRepository

/**
 * `devtools` (`docs/screens/20-settings-language-and-push.md` §6.2). **`src/debug` only.**
 *
 * `DeliverTapped` drives `PushRepository.handle` — **the same method `PushMessagingService` calls**,
 * never a parallel one. That is the only property that makes a bench worth having.
 *
 * There is **no process restart** anywhere in this file. The competitor's device-id save calls
 * `AppUtils.relaunchApp(killProcess = true)`, the same process kill as its language save
 * (§6.4 delta 2).
 */
class DevToolsViewModel(
    private val push: PushRepository,
    log: AppLogger = AppLogger.NoOp,
) : MviViewModel<DevToolsState, DevToolsIntent, DevToolsEffect>(DevToolsState(), log) {

    override fun onIntent(intent: DevToolsIntent) {
        when (intent) {
            is DevToolsIntent.PayloadChanged -> setState { copy(payloadInput = intent.value) }
            DevToolsIntent.DeliverTapped -> deliver()
            DevToolsIntent.BackPressed -> sendEffect(DevToolsEffect.NavigateBack)
        }
    }

    private fun deliver() {
        val data = parsePayload(currentState.payloadInput)
        if (data.isEmpty()) {
            sendEffect(DevToolsEffect.NothingToDeliver)
            return
        }
        // Both scalar fields are the sender's to set, and there is no sender: `null` and `0` are
        // exactly what `RemoteMessage` reports when a real message carries neither. Filling them
        // with plausible values here would let a future handler key on something no wire format
        // guarantees.
        val message = PushMessage(messageId = null, sentAtMillis = 0L, data = data)
        launchSafely(onError = { sendEffect(DevToolsEffect.DeliveryFailed) }) {
            when (push.handle(message)) {
                is AppResult.Success -> sendEffect(DevToolsEffect.Delivered(data.size))
                is AppResult.Failure -> sendEffect(DevToolsEffect.DeliveryFailed)
            }
        }
    }
}
